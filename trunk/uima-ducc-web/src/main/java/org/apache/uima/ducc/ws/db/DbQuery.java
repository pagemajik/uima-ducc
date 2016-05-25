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
package org.apache.uima.ducc.ws.db;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.uima.ducc.common.persistence.rm.IRmPersistence;
import org.apache.uima.ducc.common.persistence.rm.RmPersistenceFactory;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccLoggerComponents;
import org.apache.uima.ducc.common.utils.DuccPropertiesResolver;
import org.apache.uima.ducc.common.utils.IDuccLoggerComponents;
import org.apache.uima.ducc.common.utils.id.DuccId;

public class DbQuery {

	private static DuccLogger logger = DuccLoggerComponents.getWsLogger(DbQuery.class.getName());
	private static DuccId jobid = null;
	
	private static DbQuery instance = null;
	
	private static boolean enabled = true;
	
	private IRmPersistence persistence = null;
	
	private static String component = IDuccLoggerComponents.abbrv_webServer;
	
	static {
		synchronized(DbQuery.class) {
			if(instance == null) {
				instance = new DbQuery();
			}
		}
	}
	
	private DbQuery() {
		DuccPropertiesResolver dpr = DuccPropertiesResolver.getInstance();
		String value;
		value = dpr.getProperty(DuccPropertiesResolver.ducc_database_host);
		if(value != null) {
			if(value.equalsIgnoreCase(DuccPropertiesResolver.ducc_database_disabled)) {
				enabled = false;
			}
		}
		persistence = RmPersistenceFactory.getInstance(this.getClass().getName(),component);
	}
	
	public static DbQuery getInstance() {
		return instance;
	}

	public boolean isEnabled() {
		return enabled;
	}
	
	public static void dumpMap(Map<String, IDbMachine> dbMachineMap) {
		String location = "dumpMap";
		if(dbMachineMap != null) {
			if(!dbMachineMap.isEmpty()) {
				for(Entry<String, IDbMachine> entry : dbMachineMap.entrySet()) {
					String key = entry.getKey();
					String value = entry.getValue().getIp();
					logger.debug(location, jobid, "host="+key+" "+"ip="+value);
				}
			}
			else {
				logger.debug(location, jobid, "map is empty");
			}
		}
		else {
			logger.debug(location, jobid, "map is null");
		}
	}
	
	public Map<String, IDbMachine> getMapMachines() { 
		String location = "getMapMachines";
		Map<String, IDbMachine> retVal = new HashMap<String, IDbMachine>();
		if(isEnabled()) {
			try {
				Map<String, Map<String, Object>> state = persistence.getAllMachines();
				if(!state.isEmpty()) {
					for ( String key : state.keySet() ) {
						Map<String, Object> entry = state.get(key);
						DbMachine value = new DbMachine(entry);
						retVal.put(key, value);
					}
				}
				else {
					logger.info(location, jobid, "map is empty");
				}
			}
			catch(Exception e) {
				logger.error(location, jobid, e);
			}
		}
		else {
			logger.info(location, jobid, "enabled="+enabled);
		}
		dumpMap(retVal);
		return retVal;
	}
	
	public void close() {
		if(persistence != null) {
			persistence.close();
		}
	}
}
