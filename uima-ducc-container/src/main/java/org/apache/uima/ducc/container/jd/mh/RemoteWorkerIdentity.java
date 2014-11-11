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
package org.apache.uima.ducc.container.jd.mh;

import org.apache.uima.ducc.container.common.ContainerLogger;
import org.apache.uima.ducc.container.common.IEntityId;
import org.apache.uima.ducc.container.common.IContainerLogger;
import org.apache.uima.ducc.container.jd.mh.iface.remote.IRemoteNode;
import org.apache.uima.ducc.container.jd.mh.iface.remote.IRemotePid;
import org.apache.uima.ducc.container.jd.mh.iface.remote.IRemoteWorkerIdentity;
import org.apache.uima.ducc.container.net.iface.IMetaCasRequester;

public class RemoteWorkerIdentity implements IRemoteWorkerIdentity, Comparable<Object> {
	
	private IContainerLogger logger = ContainerLogger.getLogger(RemoteWorkerIdentity.class, IContainerLogger.Component.JD.name());
	
	private String nodeName = null;
	private String nodeAddress = null;
	private int pid = 0;
	private int tid = 0;
	
	public RemoteWorkerIdentity(IMetaCasRequester metaCasRequester) {
		setNodeName(metaCasRequester.getRequesterName());
		setNodeAddress(metaCasRequester.getRequesterAddress());
		setPid(metaCasRequester.getRequesterProcessId());
		setTid(metaCasRequester.getRequesterThreadId());
	}
	
	public RemoteWorkerIdentity(String nodeName, String nodeAddress, int pid, int tid) {
		setNodeName(nodeName);
		setNodeAddress(nodeName);
		setPid(pid);
		setTid(tid);
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		if(nodeName != null) {
			sb.append(nodeName);
			sb.append(".");
		}
		sb.append(pid);
		sb.append(".");
		sb.append(tid);
		return sb.toString();
	}
	
	@Override
	public String getNodeName() {
		return nodeName;
	}
	
	@Override
	public void setNodeName(String value) {
		nodeName = value;
	}
	
	@Override
	public String getNodeAddress() {
		return nodeAddress;
	}
	
	@Override
	public void setNodeAddress(String value) {
		nodeAddress = value;
	}
	
	@Override
	public int getPid() {
		return pid;
	}
	
	@Override
	public void setPid(int value) {
		pid = value;
	}
	
	@Override
	public int getTid() {
		return tid;
	}
	
	@Override
	public void setTid(int value) {
		tid = value;
	}
	
	private int compareNodeName(RemoteWorkerIdentity that) {
		int retVal = 0;
		String thisNodeName = this.getNodeName();
		String thatNodeName = that.getNodeName();
		if(thisNodeName != null) {
			if(thatNodeName != null) {
				retVal = thisNodeName.compareTo(thatNodeName);
			}
		}
		return retVal;
	}
	
	private int comparePid(RemoteWorkerIdentity that) {
		int retVal = 0;
		Integer thisPid = new Integer(this.pid);
		Integer thatPid = new Integer(that.pid);
		retVal = thisPid.compareTo(thatPid);
		return retVal;
	}
	
	private int compareTid(RemoteWorkerIdentity that) {
		int retVal = 0;
		Integer thisTid = new Integer(this.tid);
		Integer thatTid = new Integer(that.tid);
		retVal = thisTid.compareTo(thatTid);
		return retVal;
	}
	
	@Override
	public int compareTo(Object o) {
		String location = "compareTo";
		int retVal = 0;
		try {
			if(o != null) {
				RemoteWorkerIdentity that = (RemoteWorkerIdentity) o;
				if(retVal == 0) {
					retVal = compareNodeName(that);
				}
				if(retVal == 0) {
					retVal = comparePid(that);
				}
				if(retVal == 0) {
					retVal = compareTid(that);
				}
			}
		}
		catch(Exception e) {
			logger.error(location, IEntityId.null_id, e);
		}
		return retVal;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		String thisNodeName = this.getNodeName();
		Integer thisPid = new Integer(this.pid);
		Integer thisTid = new Integer(this.tid);
		result = prime * result + ((thisNodeName == null) ? 0 : thisNodeName.hashCode());
		result = prime * result + ((thisPid == null) ? 0 : thisPid.hashCode());
		result = prime * result + ((thisTid == null) ? 0 : thisTid.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		String location = "equals";
		boolean retVal = false;
		try {
			if(obj != null) {
				if(this == obj) {
					retVal = true;
				}
				else {
					RemoteWorkerIdentity that = (RemoteWorkerIdentity) obj;
					if(this.compareTo(that) == 0) {
						retVal = true;
					}
				}
			}
		}
		catch(Exception e) {
			logger.error(location, IEntityId.null_id, e);
		}
		return retVal;
	}

	/////
	
	private int compareToNodeName(IRemoteNode that) {
		int retVal = 0;
		String thisNodeName = this.getNodeName();
		String thatNodeName = that.getNodeName();
		if(thisNodeName != null) {
			if(thatNodeName != null) {
				retVal = thisNodeName.compareTo(thatNodeName);
			}
		}
		return retVal;
	}
	
	private int compareToPid(IRemotePid that) {
		int retVal = 0;
		Integer thisPid = new Integer(this.getPid());
		Integer thatPid = new Integer(that.getPid());
		retVal = thisPid.compareTo(thatPid);
		return retVal;
	}
	
	@Override
	public boolean comprises(IRemoteNode that) {
		boolean retVal = false;
		if(that != null) {
			if(this.compareToNodeName(that) == 0) {
				retVal = true;
			}
		}
		return retVal;
	}

	@Override
	public boolean comprises(IRemotePid that) {
		boolean retVal = false;
		if(that != null) {
			if(this.compareToNodeName(that) == 0) {
				if(this.compareToPid(that) == 0) {
					retVal = true;
				}
			}
		}
		return retVal;
	}
}
