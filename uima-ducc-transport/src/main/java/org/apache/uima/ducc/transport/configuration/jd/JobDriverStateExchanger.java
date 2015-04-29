/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.transport.configuration.jd;

import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.uima.ducc.common.NodeIdentity;
import org.apache.uima.ducc.common.jd.files.workitem.IWorkItemStateKeeper;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccLoggerComponents;
import org.apache.uima.ducc.common.utils.DuccPropertiesResolver;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.container.jd.JobDriver;
import org.apache.uima.ducc.container.jd.mh.IMessageHandler;
import org.apache.uima.ducc.container.jd.mh.iface.IOperatingInfo;
import org.apache.uima.ducc.container.jd.mh.iface.IProcessInfo;
import org.apache.uima.ducc.container.jd.mh.impl.ProcessInfo;
import org.apache.uima.ducc.transport.dispatcher.DuccEventHttpDispatcher;
import org.apache.uima.ducc.transport.dispatcher.IDuccEventDispatcher;
import org.apache.uima.ducc.transport.event.JdReplyEvent;
import org.apache.uima.ducc.transport.event.JdRequestEvent;
import org.apache.uima.ducc.transport.event.common.DuccProcessMap;
import org.apache.uima.ducc.transport.event.common.IDuccProcess;
import org.apache.uima.ducc.transport.event.common.IDuccProcessMap;
import org.apache.uima.ducc.transport.event.common.IDuccWorkJob;
import org.apache.uima.ducc.transport.event.common.IProcessState.ProcessState;
import org.apache.uima.ducc.transport.event.common.IResourceState.ProcessDeallocationType;
import org.apache.uima.ducc.transport.event.jd.IDriverStatusReport;
import org.apache.uima.ducc.transport.event.jd.JobDriverReport;

public class JobDriverStateExchanger extends Thread {
	
	private static final DuccLogger logger = DuccLoggerComponents.getOrLogger(JobDriverStateExchanger.class.getName());
	private static final DuccId jobid = null;
	
	private JobDriverComponent jdc = null;
	
	private IDuccEventDispatcher dispatcher;
	private String orchestrator = "orchestrator";
	
	private long minMillis = 1000;
	private long wakeUpMillis = 15*1000;
	
	private long sleepTime = wakeUpMillis;
	private long lastTime = System.currentTimeMillis();
	
	private boolean die = false;
	
	private AtomicInteger getStateReqNo = new AtomicInteger(0);
	
	private IDuccProcessMap dpMap = new DuccProcessMap();
	
	public static IDuccEventDispatcher create(Object specs) throws Exception {
		IDuccEventDispatcher retVal = null;
		String targetUrl = (String) specs;
		retVal = new DuccEventHttpDispatcher(targetUrl);
		return retVal;
	}

	public JobDriverStateExchanger() {
		initialize();
	}
	
	private void initialize() {
		initializeTarget();
		initializeThread();
	}
	
	private void initializeThread() {
		String location = "initializeThread";
		String key = DuccPropertiesResolver.ducc_jd_state_publish_rate;
		String exchange_rate = DuccPropertiesResolver.getInstance().getProperty(key);
		if(exchange_rate != null) {
			try {
				long rate = Long.parseLong(exchange_rate);
				if(rate < minMillis) {
					logger.error(location, jobid, key+" < minimum of "+minMillis);
				}
				else {
					wakeUpMillis = rate;
					sleepTime = wakeUpMillis;
				}
			}
			catch(Throwable t) {
				logger.error(location, jobid, t);
			}
		}
		logger.debug(location, jobid, "rate:"+wakeUpMillis);
	}
	
	private void initializeTarget() {
		String location = "initializeTarget";
		try {
			String targetUrl = getTargetUrl();
			logger.info(location, jobid, targetUrl);
			dispatcher = create(targetUrl);
		} 
		catch (Exception e) {
			logger.error(location, jobid, e);
		}
	}
	
	private String getServer() {
		return orchestrator;
	}
	
	private String getTargetUrl() {
		String targetUrl = null;
		String server = getServer();
		String host = DuccPropertiesResolver.get("ducc." + server + ".http.node");
	    String port = DuccPropertiesResolver.get("ducc." + server + ".http.port");
        if ( host == null || port == null ) {
        	String message = "ducc." + server + ".http.node and/or .port not set in ducc.properties";
            throw new IllegalStateException(message);
        }
        targetUrl = "http://" + host + ":" + port + "/" + server.substring(0, 2);
		return targetUrl;
	}
	
	public void setJobDriverComponent(JobDriverComponent value) {
		jdc = value;
	}
	
	public void setProcessMap(IDuccProcessMap value) {
		dpMap = value;
	}
	
	public JdReplyEvent request(JdRequestEvent jdRequestEvent) {
		String location = "request";
		JdReplyEvent jdReplyEvent = null;
		try {
			jdReplyEvent = (JdReplyEvent) dispatcher.dispatchAndWaitForDuccReply(jdRequestEvent);
		} 
		catch (Exception e) {
			logger.error(location, jobid, e);
		}
		return jdReplyEvent;
	}
	
	private JdRequestEvent getJdRequestEvent() {
		String location = "getJdRequestEvent";
		JdRequestEvent jdRequestEvent = new JdRequestEvent();
		try {
			IMessageHandler mh = JobDriver.getInstance().getMessageHandler();
			IOperatingInfo oi = mh.handleGetOperatingInfo();
			IDriverStatusReport driverStatusReport = new JobDriverReport(oi, dpMap);
			driverStatusReport.setNode(jdc.getNode());
			driverStatusReport.setPort(jdc.getPort());
			driverStatusReport.setJmxUrl(jdc.getJmxUrl());
			jdRequestEvent.setDriverStatusReport(driverStatusReport);
			logger.debug(location, jobid, "reqNo: "+getStateReqNo.incrementAndGet());
		}
		catch(Exception e) {
			logger.error(location, jobid, e);
		}
		return jdRequestEvent;
	}
	
	private void handle(JdReplyEvent jdReplyEvent) {
		String location = "handle";
		IDuccWorkJob dwj = jdReplyEvent.getJob();
		try {
			JobDriver jd = JobDriver.getInstance();
			IMessageHandler mh = jd.getMessageHandler();
			if(dwj != null) {
				setProcessMap(dwj.getProcessMap());
				IWorkItemStateKeeper wisk = JobDriver.getInstance().getWorkItemStateKeeper();
				wisk.persist();
				IDuccProcessMap pMap = dwj.getProcessMap();
				for(Entry<DuccId, IDuccProcess> entry : pMap.entrySet()) {
					IDuccProcess p = entry.getValue();
					ProcessState state = p.getProcessState();
					NodeIdentity ni = p.getNodeIdentity();
					String node = ni.getName();
					String ip = ni.getIp();
					String pidName = p.getDuccId().getFriendly()+"";
					String pid = p.getPID();
					StringBuffer sb = new StringBuffer();
					sb.append("node: "+node);
					sb.append(" ");
					sb.append("ip: "+ip);
					sb.append(" ");
					sb.append("pid: "+pid);
					sb.append(" ");
					sb.append("state:"+state.name());
					sb.append(" ");
					String reasonStopped = p.getReasonForStoppingProcess();
					if(reasonStopped != null) {
						sb.append("reason[stopped]:"+reasonStopped);
						sb.append(" ");
					}
					String reasonDeallocated = null;
					ProcessDeallocationType processDeallocationType = p.getProcessDeallocationType();
					if(processDeallocationType != null) {
						reasonDeallocated = processDeallocationType.name();
						sb.append("reason[deallocated]:"+reasonDeallocated);
						sb.append(" ");
					}
					logger.debug(location, jobid, sb.toString());
					switch(state) {
					case Starting:    
					case Initializing:
					case Running:
						break;
					default:
						try {
							if(pid != null) {
								int iPid = Integer.parseInt(pid.trim());
								IProcessInfo processInfo = new ProcessInfo(node, ip, pidName, iPid, reasonStopped, reasonDeallocated);
								if(p.isFailedInitialization()) {
									mh.handleProcessFailedInitialization(processInfo);
								}
								else if(p.isPreempted()) {
									mh.handleProcessPreempt(processInfo);
								}
								else if(p.isVolunteered()) {
									mh.handleProcessVolunteered(processInfo);
								}
								else {
									mh.handleProcessDown(processInfo);
								}
							}
						}
						catch(Exception e) {
							logger.error(location, jobid, e);
						}
						break;
					}
				}
			}
		}
		catch(Exception e) {
			logger.error(location, jobid, e);
		}
	}
	
	private boolean isTime() {
		String location = "isTime";
		boolean retVal = true;
		long currTime = System.currentTimeMillis();
		long elapsedTime = currTime - lastTime;
		logger.debug(location, jobid, "elapsedTime: "+elapsedTime);
		if(elapsedTime < wakeUpMillis) {
			retVal = false;
			sleepTime = wakeUpMillis - elapsedTime;
		}
		else {
			lastTime = currTime;
			sleepTime = wakeUpMillis;
		}
		return retVal;
	}
	
	public void run() {
		String location = "run";
		logger.debug(location, jobid, "begin");
		while(!die) {
			try {
				if(isTime()) {
					JdRequestEvent jdRequestEvent = getJdRequestEvent();
					JdReplyEvent jdReplyEvent = request(jdRequestEvent);
					handle(jdReplyEvent);
				}
			}
			catch(Exception e) {
				logger.error(location, jobid, e);
			}
			try {
				logger.debug(location, jobid, "sleep "+sleepTime/1000);
				Thread.sleep(sleepTime);
			}
			catch(Exception e) {
				logger.error(location, jobid, e);
			}
		}
		logger.debug(location, jobid, "end");
	}
}