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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.ftpserver.ftplet.FtpFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File releated tools
 * 
 * @author francisdb
 *
 */
public class FileTools {
	
	private static final Logger logger = LoggerFactory.getLogger(FileTools.class);
	
	private static final String SFV_EXT = ".sfv";

	private FileTools() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static File findSfv(File folder) {
		File sfv = null;
		File[] files = folder.listFiles(new SfvFileFilter());
		if(files != null){
			for (File curFile : files) {
				// TODO handle more than one sfv?
				sfv = curFile;
			}
		}
		return sfv;
	}
	
	public static boolean isSfv(File file){
		return file.getName().toLowerCase().endsWith(SFV_EXT);
	}
	
	public static boolean isSfv(FtpFile file){
		return file.getName().toLowerCase().endsWith(SFV_EXT);
	}
	
	public static boolean createSymbolicLink(File source, File destination) throws IOException{
		boolean succes = true;
		String srcabsolutePathfix = source.getAbsolutePath();
		String desabsolutePathfix = destination.getAbsolutePath();

		Process process = Runtime.getRuntime().exec( new String[] { "ln", "-s", srcabsolutePathfix, desabsolutePathfix } );
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			succes = false;
			logger.error(e.getMessage(), e);
		}
		process.destroy();
		return succes;
	}
	
	private static final class SfvFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && FileTools.isSfv(pathname);
		}
	}

}
