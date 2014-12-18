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
package org.apache.uima.ducc.container.dgen.classload;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.container.common.FlagsExtendedHelper;
import org.apache.uima.ducc.container.common.MessageBuffer;
import org.apache.uima.ducc.container.common.Standardize;
import org.apache.uima.ducc.container.common.classloader.PrivateClassLoader;
import org.apache.uima.ducc.container.common.logger.IComponent;
import org.apache.uima.ducc.container.common.logger.ILogger;
import org.apache.uima.ducc.container.common.logger.Logger;

public class ProxyAeGenerate {

	private static Logger logger = Logger.getLogger(ProxyAeGenerate.class, IComponent.Id.JD.name());
	
	private URLClassLoader urlClassLoader = null;

	private String[] requiredClasses = { 
			"org.apache.uima.ducc.user.dgen.iface.AeException", 
			"org.apache.uima.ducc.user.dgen.iface.AeGenerate",
			"org.apache.uima.ducc.user.dgen.iface.IAeGenerate",
			// implied:
			//"org.springframework.util.Assert",
			//"org.apache.xmlbeans.XmlBeans",
			};
	
	public ProxyAeGenerate() throws ProxyAeException {
		initialize();
	}
	
	private void show(String text) {
		String location = "show";
		logger.info(location, ILogger.null_id, text);
		System.out.println(text);
	}
	
	private void show(String name, String value) {
		show(name+"="+value);
	}
	
	private void show(String name, Integer value) {
		show(name+"="+value.toString());
	}
	
	private void show(String name, List<String> value) {
		if(value == null) {
			show(name+"="+value);
		}
		else {
			show(name+"="+value.toString());
		}
	}
	
	public String generate(
			String directory, 
			String id,
			String dgenName,
			String dgenDescription,
			Integer dgenThreadCount,
			String dgenBrokerURL,
			String dgenEndpoint,
			String cmDescriptor,
			List<String> cmOverrides, 
			String aeDescriptor, 
			List<String> aeOverrides, 
			String ccDescriptor,
			List<String> ccOverrides
			) throws ProxyAeException
	{
		String retVal = null;
		try {
			show("directory", directory);
			show("id", id);
			show("dgenName", dgenName);
			show("dgenDescription", dgenDescription);
			show("dgenThreadCount", dgenThreadCount);
			show("dgenBrokerURL", dgenBrokerURL);
			show("dgenEndpoint", dgenEndpoint);
			show("cmDescriptor", cmDescriptor);
			show("cmOverrides", cmOverrides);
			show("aeDescriptor", aeDescriptor);
			show("aeOverrides", aeOverrides);
			show("ccDescriptor", ccDescriptor);
			show("ccOverrides", ccOverrides);
			Class<?> clazz = urlClassLoader.loadClass("org.apache.uima.ducc.user.dgen.iface.AeGenerate");
			Constructor<?> constructor = clazz.getConstructor();
			Object instance = constructor.newInstance();
			Class<?>[] parameterTypes = { 
					String.class,	// directory
					String.class,	// id
					String.class,	// dgenName
					String.class,	// dgenDescription
					Integer.class,	// dgenThreadCount
					String.class,	// dgenBrokerURL
					String.class,	// dgenEndpoint
					String.class,	// cmDescriptor
					List.class,		// cmOverrides
					String.class,	// aeDescriptor
					List.class,		// aeOverrides
					String.class,	// ccDescriptor
					List.class,		// ccOverrides
			};
			Method method = clazz.getMethod("generate", parameterTypes);
			Object[] args = { 
					directory, 
					id, 
					dgenName, 
					dgenDescription, 
					dgenThreadCount, 
					dgenBrokerURL, 
					dgenEndpoint,
					cmDescriptor,
					cmOverrides,
					aeDescriptor,
					aeOverrides,
					ccDescriptor,
					ccOverrides
					};
			String dgen = (String) method.invoke(instance, args);
			show("generated deployment descriptor", dgen);
			retVal = dgen;
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new ProxyAeException(e.toString());
		}
		return retVal;
	}
	
	private void initialize() throws ProxyAeException {
		FlagsExtendedHelper feh = FlagsExtendedHelper.getInstance();
		String userClasspath = feh.getUserClasspath();
		urlClassLoader = createClassLoader(userClasspath);
		validate();
	}
	
	private URLClassLoader createClassLoader(String userClasspath) {
		String location = "createClassLoader";
		URLClassLoader retVal = null;
		try {
			retVal = PrivateClassLoader.create(userClasspath);
		}
		catch(Exception e) {
			logger.error(location, ILogger.null_id, e);
		}
		return retVal;
	}
	
	private void validate() throws ProxyAeException {
		for(String className : requiredClasses) {
			loadClass(className);
		}
	}
	
	private void loadClass(String className) throws ProxyAeException {
		String location = "loadClass";
		try {
			MessageBuffer mb1 = new MessageBuffer();
			mb1.append(Standardize.Label.loading.get()+className);
			logger.debug(location, ILogger.null_id, mb1.toString());
			URL[] urls = urlClassLoader.getURLs();
			for(URL url : urls) {
				logger.trace(location, ILogger.null_id, url);
			}
			Class<?> loadedClass = urlClassLoader.loadClass(className);
			MessageBuffer mb2 = new MessageBuffer();
			mb2.append(Standardize.Label.loaded.get()+loadedClass.getName());
			logger.trace(location, ILogger.null_id, mb2.toString());
		} 
		catch (Exception e) {
			DuccLogger duccLogger = DuccLogger.getLogger(ProxyAeGenerate.class, "JD");
			duccLogger.error(location, null, e);
			logger.error(location, ILogger.null_id, e);
			throw new ProxyAeException(e);
		}
	}
}