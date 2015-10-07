/*
 * Copyright 2009 Francis De Brabandere
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.code.sfvcheckftplet.service;

/**
 * System related tools
 * 
 * @author francisdb
 *
 */
public class SystemTools {
	
//	AIX
//	Digital Unix
//	FreeBSD
//	HP UX
//	Irix
//	Linux
//	Mac OS
//	Mac OS X
//	MPE/iX
//	Netware 4.11
//	OS/2
//	Solaris
//	Windows 2000
//	Windows 95
//	Windows 98
//	Windows NT
//	Windows Vista
//	Windows XP
	
	
	
	
	/**
	 * Operating System
	 */
	enum Os {
		LINUX("linux"),
		SOLARIS("solaris"),
		BSD("bsd"),
		MAC("mac"),
		WINDOWS("windows");
		
		private final String osNameContains;
		
		private Os(final String osNameContains) {
			this.osNameContains = osNameContains;
		}
		
		public final String getOsNameContains() {
			return osNameContains;
		}
	}
	
	private SystemTools() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Finds out if the curent os supports symbolically linking files
	 * @return true if the os supports symlinks
	 */
	public static boolean osSupportsLinking(){
		Os os = getOs();
		return os == Os.LINUX || os == Os.MAC || os == Os.SOLARIS || os == Os.BSD;
	}
	
	/**
	 * Detects the operating system we are running on
	 * @return the os
	 */
	public static Os getOs(){
		Os os = null;
		String osNameLower = System.getProperty("os.name").toLowerCase();
		for(Os curOs:Os.values()){
			if(osNameLower.contains(curOs.getOsNameContains())){
				os = curOs;
			}
		}
		return os;
	}
}
