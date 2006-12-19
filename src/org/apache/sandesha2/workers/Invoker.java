/*
 * Copyright 1999-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *  
 */

package org.apache.sandesha2.workers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.Transaction;
import org.apache.sandesha2.storage.beanmanagers.InvokerBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.RMDBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SequencePropertyBeanMgr;
import org.apache.sandesha2.storage.beans.InvokerBean;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.SequencePropertyBean;
import org.apache.sandesha2.util.Range;
import org.apache.sandesha2.util.RangeString;
import org.apache.sandesha2.util.SandeshaUtil;

/**
 * This is used when InOrder invocation is required. This is a seperated Thread
 * that keep running all the time. At each iteration it checks the InvokerTable
 * to find weather there are any messages to me invoked.
 */

public class Invoker extends SandeshaThread {

	private static final Log log = LogFactory.getLog(Invoker.class);
	
	public Invoker() {
		super(Sandesha2Constants.INVOKER_SLEEP_TIME);
	}
	
	/**
	 * Forces dispatch of queued messages to the application.
	 * NOTE: may break ordering
	 * @param ctx
	 * @param sequenceID
	 * @param allowLaterDeliveryOfMissingMessages if true, messages skipped over during this
	 * action will be invoked if they arrive on the system at a later time. 
	 * Otherwise messages skipped over will be ignored
	 * @throws SandeshaException
	 */
	public synchronized void forceInvokeOfAllMessagesCurrentlyOnSequence(ConfigurationContext ctx, 
			String sequenceID,
			boolean allowLaterDeliveryOfMissingMessages)throws SandeshaException{
		//first we block while we wait for the invoking thread to pause
		blockForPause();
		try{
			//get all invoker beans for the sequence
			StorageManager storageManager = 
				SandeshaUtil.getSandeshaStorageManager(context, context.getAxisConfiguration());
	
			InvokerBeanMgr storageMapMgr = storageManager
					.getInvokerBeanMgr();
			RMDBeanMgr nextMsgMgr = storageManager.getRMDBeanMgr();
			RMDBean rMDBean = nextMsgMgr.retrieve(sequenceID);
			
			if (rMDBean != null) {
				
				//The outOfOrder window is the set of known sequence messages (including those
				//that are missing) at the time the button is pressed.
				long firstMessageInOutOfOrderWindow = rMDBean.getNextMsgNoToProcess();
			
				Iterator stMapIt = 
					storageMapMgr.find(new InvokerBean(null, 0, sequenceID)).iterator();
				
				long highestMsgNumberInvoked = 0;
				Transaction transaction = null;
				
				//invoke each bean in turn. 
				//NOTE: here we are breaking ordering
				while(stMapIt.hasNext()){
					transaction = storageManager.getTransaction();
					InvokerBean invoker = (InvokerBean)stMapIt.next();
					
					//invoke the app
					try{
						// start a new worker thread and let it do the invocation.
						String workId = sequenceID + "::" + invoker.getMsgNo(); //creating a workId to uniquely identify the
					   //piece of work that will be assigned to the Worker.
						
						String messageContextKey = invoker.getMessageContextRefKey();
						InvokerWorker worker = new InvokerWorker(context,
								messageContextKey, 
								true); //want to ignore the enxt msg number
						
						worker.setLock(getWorkerLock());
						worker.setWorkId(workId);
						
						//before we execute we need to set the 
						
						threadPool.execute(worker);
					
						//adding the workId to the lock after assigning it to a thread makes sure 
						//that all the workIds in the Lock are handled by threads.
						getWorkerLock().addWork(workId);

						long msgNumber = invoker.getMsgNo();
						//if necessary, update the "next message number" bean under this transaction
						if(msgNumber>highestMsgNumberInvoked){
							highestMsgNumberInvoked = invoker.getMsgNo();
							rMDBean.setNextMsgNoToProcess(highestMsgNumberInvoked+1);
							nextMsgMgr.update(rMDBean);
							
							if(allowLaterDeliveryOfMissingMessages){
								//we also need to update the sequence OUT_OF_ORDER_RANGES property
								//so as to include our latest view of this outOfOrder range.
								//We do that here (rather than once at the end) so that we reamin
								//transactionally consistent
								Range r = new Range(firstMessageInOutOfOrderWindow,highestMsgNumberInvoked);
										
								RangeString rangeString = null;
								SequencePropertyBeanMgr seqPropertyManager = storageManager.getSequencePropertyBeanMgr();
								SequencePropertyBean outOfOrderRanges = 
									seqPropertyManager.retrieve(sequenceID, Sandesha2Constants.SequenceProperties.OUT_OF_ORDER_RANGES);
								if(outOfOrderRanges==null){
									//insert a new blank one one
									outOfOrderRanges = new SequencePropertyBean(sequenceID,
											Sandesha2Constants.SequenceProperties.OUT_OF_ORDER_RANGES,
											"");

									seqPropertyManager.insert(outOfOrderRanges);
									rangeString = new RangeString("");
								}
								else{
									rangeString = new RangeString(outOfOrderRanges.getValue());
								}
								//update the range String with the new value
								rangeString.addRange(r);
								outOfOrderRanges.setValue(rangeString.toString());
								seqPropertyManager.update(outOfOrderRanges);
							}
						}
						
					}
					catch(Exception e){
						if(transaction != null) {
							transaction.rollback();
							transaction = null;
						}
					} finally {
						if(transaction != null) {
							transaction.commit();
							transaction = null;
						}
					}
		
				}//end while
			}
		}
		finally{
			//restart the invoker
			finishPause();
		}
	}

	private void addOutOfOrderInvokerBeansToList(String sequenceID, 
			StorageManager strMgr, List list)throws SandeshaException{
		if (log.isDebugEnabled())
			log.debug("Enter: InOrderInvoker::addOutOfOrderInvokerBeansToList");
		
		SequencePropertyBeanMgr seqPropertyManager = strMgr.getSequencePropertyBeanMgr();
		
		SequencePropertyBean outOfOrderRanges = 
			seqPropertyManager.retrieve(sequenceID, Sandesha2Constants.SequenceProperties.OUT_OF_ORDER_RANGES);		
		if(outOfOrderRanges!=null){
			String sequenceRanges = outOfOrderRanges.getValue();
			RangeString rangeString = new RangeString(sequenceRanges);
			//we now have the set of ranges that can be delivered out of order.
			//Look for any invokable message that lies in one of those ranges
			Iterator invokerBeansIterator = 
				strMgr.getInvokerBeanMgr().find(
						new InvokerBean(null, 
														0,  //finds all invoker beans
														sequenceID)).iterator();
			
			while(invokerBeansIterator.hasNext()){
				InvokerBean invokerBean = (InvokerBean)invokerBeansIterator.next();
				
				if(rangeString.isMessageNumberInRanges(invokerBean.getMsgNo())){
					//an invoker bean that has not been deleted and lies in an out
					//or order range - we can add this to the list
					list.add(invokerBean);
				}
			}
			
		}
			
		if (log.isDebugEnabled())
			log.debug("Exit: InOrderInvoker::addOutOfOrderInvokerBeansToList");
	}
	
	protected void internalRun() {
		if (log.isDebugEnabled())
			log.debug("Enter: InOrderInvoker::internalRun");
		
		// If this invoker is working for several sequences, we use round-robin to
		// try and give them all a chance to invoke messages.
		int nextIndex = 0;

		while (isThreadStarted()) {

			try {
				Thread.sleep(Sandesha2Constants.INVOKER_SLEEP_TIME);
			} catch (InterruptedException ex) {
				log.debug("Invoker was Inturrepted....");
				log.debug(ex.getMessage());
			}
					
			//pause if we have to
			doPauseIfNeeded();

			Transaction transaction = null;

			try {
				StorageManager storageManager = SandeshaUtil
						.getSandeshaStorageManager(context, context
								.getAxisConfiguration());
				RMDBeanMgr nextMsgMgr = storageManager.getRMDBeanMgr();

				InvokerBeanMgr storageMapMgr = storageManager
						.getInvokerBeanMgr();

				SequencePropertyBeanMgr sequencePropMgr = storageManager
						.getSequencePropertyBeanMgr();

				transaction = storageManager.getTransaction();

				// Getting the incomingSequenceIdList
				SequencePropertyBean allSequencesBean = sequencePropMgr
						.retrieve(
								Sandesha2Constants.SequenceProperties.ALL_SEQUENCES,
								Sandesha2Constants.SequenceProperties.INCOMING_SEQUENCE_LIST);

				if (allSequencesBean == null) {
					if (log.isDebugEnabled())
						log.debug("AllSequencesBean not found");
					continue;
				}
				
				// Pick a sequence using a round-robin approach
				ArrayList allSequencesList = SandeshaUtil
						.getArrayListFromString(allSequencesBean.getValue());
				int size = allSequencesList.size();
				log.debug("Choosing one from " + size + " sequences");
				if(nextIndex >= size) {
					nextIndex = 0;
					if (size == 0) continue;
				}
				String sequenceId = (String) allSequencesList.get(nextIndex++);
				log.debug("Chose sequence " + sequenceId);

				RMDBean nextMsgBean = nextMsgMgr.retrieve(sequenceId);
				if (nextMsgBean == null) {
					String message = "Next message not set correctly. Removing invalid entry.";
					log.debug(message);
	
					allSequencesList.remove(nextIndex - 1);
					
					// cleaning the invalid data of the all sequences.
					allSequencesBean.setValue(allSequencesList.toString());
					sequencePropMgr.update(allSequencesBean);
					continue;
				}

				long nextMsgno = nextMsgBean.getNextMsgNoToProcess();
				if (nextMsgno <= 0) {
					if (log.isDebugEnabled())
						log.debug("Invalid Next Message Number " + nextMsgno);
					String message = SandeshaMessageHelper.getMessage(
							SandeshaMessageKeys.invalidMsgNumber, Long
									.toString(nextMsgno));
					throw new SandeshaException(message);
				}

				List invokerBeans = storageMapMgr.find(
						new InvokerBean(null, nextMsgno, sequenceId));
				
				//add any msgs that belong to out of order windows
				addOutOfOrderInvokerBeansToList(sequenceId, 
						storageManager, invokerBeans);
				
				Iterator stMapIt = invokerBeans.iterator();
				
				//TODO correct the locking mechanism to have one lock per sequence.
				
				if (stMapIt.hasNext()) { //some invokation work is present

					InvokerBean bean = (InvokerBean) stMapIt.next();
					//see if this is an out of order msg
					boolean beanIsOutOfOrderMsg = bean.getMsgNo()!=nextMsgno;
					
					String workId = sequenceId + "::" + bean.getMsgNo(); 
																		//creating a workId to uniquely identify the
																   //piece of work that will be assigned to the Worker.
										
					//check whether the bean is already assigned to a worker.
					if (getWorkerLock().isWorkPresent(workId)) {
						String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.workAlreadyAssigned, workId);
						log.debug(message);
						continue;
					}

					String messageContextKey = bean.getMessageContextRefKey();
					
					if(transaction != null) {
						transaction.commit();
						transaction = null;
					}

					// start a new worker thread and let it do the invocation.
					InvokerWorker worker = new InvokerWorker(context,
							messageContextKey, 
							beanIsOutOfOrderMsg); //only ignore nextMsgNumber if the bean is an
																		//out of order message
					
					worker.setLock(getWorkerLock());
					worker.setWorkId(workId);
					
					threadPool.execute(worker);
					
					//adding the workId to the lock after assigning it to a thread makes sure 
					//that all the workIds in the Lock are handled by threads.
					getWorkerLock().addWork(workId);
				}

			} catch (Exception e) {
				if (transaction != null) {
					try {
						transaction.rollback();
						transaction = null;
					} catch (Exception e1) {
						String message = SandeshaMessageHelper.getMessage(
								SandeshaMessageKeys.rollbackError, e1
										.toString());
						log.debug(message, e1);
					}
				}
				String message = SandeshaMessageHelper
						.getMessage(SandeshaMessageKeys.invokeMsgError);
				log.debug(message, e);
			} finally {
				if (transaction != null) {
					try {
						transaction.commit();
						transaction = null;
					} catch (Exception e) {
						String message = SandeshaMessageHelper.getMessage(
								SandeshaMessageKeys.commitError, e.toString());
						log.debug(message, e);
					}
				}
			}
		}
		if (log.isDebugEnabled())
			log.debug("Exit: InOrderInvoker::internalRun");
	}

}
