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
package com.google.code.sfvcheckftplet;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;

import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.sfvcheckftplet.service.CrcService;
import com.google.code.sfvcheckftplet.service.FileTools;
import com.google.code.sfvcheckftplet.service.SystemTools;
import com.google.code.sfvcheckftplet.SfvCheckFtpServer;

/**
 * Ftplet that crc checks incoming files
 *  
 * TODO use http://java.sun.com/j2se/1.5.0/docs/api/java/util/Formatter.html
 * 
 * @author francisdb
 *
 */
public class SfvCheckFtpLet extends DefaultFtplet {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpLet.class);
	
	private static final int SITE_RESPONSE = 200;
	private static final int TRANSFER_COMPLETE_RESPONSE = 226;
	private static final int REQUESTED_FILE_ACTION_OK = 250;
        private FtpletContext ftpletcontext;
	private final CrcService crcService;
	private final Formatter formatter;
	public static ArrayList<String> quota_path = new ArrayList<String>();
	public static ArrayList<Long> quota_limit = new ArrayList<Long>();
	public static ArrayList<Long> quota_size = new ArrayList<Long>();
	public static String homeDir = System.getProperty("user.dir");
        public static boolean sfvcheckenabled;
        public static String shellpath = "/bin/sh";
	public SfvCheckFtpLet() {
		this.crcService = new CrcService();
		this.formatter = new Formatter();
	}
	
	@Override
	public void init(FtpletContext paramftpletContext) throws FtpException {
                this.ftpletcontext = paramftpletContext;
                SfvCheckReadConfig();
                logger.info("SFV Check: " + sfvcheckenabled);
		crcService.init();
                shellpath = ReadShellExecutePathConfig();
                QuotaInit();

	}
	
	@Override
	public void destroy() {
		crcService.shutdown();
	}
	
	@Override
	public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String fileName = request.getArgument();
                // Disk Quota Start
                String FilePath = session.getFileSystemView().getFile(request.getArgument()).getAbsolutePath();
                int matchingQuotaPath = QuotaIndexOfPathforAbsolutePath(FilePath);
                
                if(matchingQuotaPath != -1){
                    long freeSpace = quota_limit.get(matchingQuotaPath) - quota_size.get(matchingQuotaPath);
                    logger.info("Free Quota in QuotaPath " + matchingQuotaPath + " " + freeSpace + " bytes");
                    if(freeSpace <= 0){
                        session.write(new DefaultFtpReply(553, fileName+": Disk Full"));
			return FtpletResult.SKIP;
                    }
                }                
                // Disk Quota End
		if(denied(fileName)){
			session.write(new DefaultFtpReply(553, fileName+": path-filter denied permission. (Filename accept)"));
			return FtpletResult.SKIP;
		}else{
			return super.onUploadStart(session, request);
		}
	}
	
	@Override
	public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		// TODO find a better place to handle this where we can generate output (neg value = no output)
		SessionWriter writer = new DefaultSessionWriter(session, -TRANSFER_COMPLETE_RESPONSE);
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);
                // Disk Quota Start
                long FileSize = session.getFileSystemView().getFile(request.getArgument()).getSize();
                String FilePath = file.getPath();
                if(FilePath.startsWith(".")){
                    FilePath = FilePath.replaceFirst(".", "");
                }
                int matchingQuotaPath = QuotaIndexOfPathforAbsolutePath(FilePath);
                if(matchingQuotaPath != -1){
                    logger.info("Adding to QuotaPath " + matchingQuotaPath + " " + FileSize + " bytes");
                    QuotaAddToSize(matchingQuotaPath, FileSize);
                }else{
                    logger.info("Test " + FilePath);
                }
                logger.info("File Path: " + FilePath);
                logger.info("File Size: " + FileSize);
                
                // Disk Quota End
                if(sfvcheckenabled == true){
		    crcService.checksum(file, true);
		    if(!FileTools.isSfv(file)){
			    handleFile(writer, file);
		    }
		    rescan(writer, file.getParentFile(), false);
                }
		return super.onUploadEnd(session, request);
	}
        @Override
        public FtpletResult onDeleteStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);
                // Disk Quota Start
                long FileSize = session.getFileSystemView().getFile(request.getArgument()).getSize();
                String FilePath = file.getPath();
                if(FilePath.startsWith(".")){
                    FilePath = FilePath.replaceFirst(".", "");
                }
                int matchingQuotaPath = QuotaIndexOfPathforAbsolutePath(FilePath);
                if(matchingQuotaPath != -1){
                    logger.info("Removing from QuotaPath " + matchingQuotaPath + " " + FileSize + " bytes");
                    QuotaAddToSize(matchingQuotaPath, -FileSize);
                }
                logger.info("File Path: " + FilePath);
                logger.info("File Size: " + FileSize);
                // Disk Quota End

                return super.onDeleteStart(session, request);
        }

	@Override
	public FtpletResult onDeleteEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		SessionWriter writer = new DefaultSessionWriter(session, -REQUESTED_FILE_ACTION_OK);
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);
                if(sfvcheckenabled == true){
		    if(FileTools.isSfv(file)){
			    cleanUp(file.getParentFile());
			    removeParentIncompleteFile(file.getParentFile());
		    }
		    crcService.clearData(file);
		    rescan(writer, file.getParentFile(), false);
                }
		return super.onDeleteEnd(session, request);
	}

	
	@Override
	public FtpletResult onSite(FtpSession session, FtpRequest request) throws FtpException, IOException {
                // Edited Method for new functions
                String raw_getArgument = request.getArgument();
                String argument = "null";
                boolean admin = ftpletcontext.getUserManager().isAdmin(session.getUser().getName());
                User curr_user = session.getUser();
                if(raw_getArgument != null){
  		        argument = request.getArgument().toUpperCase();
                }

                logger.info("DEBUG Raw MSG: " + argument);
		if("RESCAN".equals(argument) && admin == true){
			return onSiteRescan(session, request);
		}else if("CACHE".equals(argument)){
			return onSiteCache(session, request);
		}else if("USERS".equals(argument) && admin == true){
                        String user_response = "USERS:\nUsername | isEnabled\n";
                        for(String user : ftpletcontext.getUserManager().getAllUserNames()){
                            ArrayList<Object> user_settings = BaseUser(ftpletcontext.getUserManager().getUserByName(user));
                            String user_is_enabled = user_settings.get(5).toString();
                            user_response = user_response + user + " | " + user_is_enabled + "\n";
                        }
                        user_response = user_response + "End.\n";
                        session.write(new DefaultFtpReply(200, user_response));
                        return FtpletResult.SKIP;
                }else if(argument.contains("DISABLEUSER ") && admin == true){
                        String user = raw_getArgument.replace("DISABLEUSER ", "");
                        user = user.replace("disableuser ", "");
                        User user_object = ftpletcontext.getUserManager().getUserByName(user);
                        if(user_object == null){
                            session.write(new DefaultFtpReply(200, user + " could not disabled"));
                        }else{
                            ArrayList<Object> user_array = BaseUser(user_object);
                            user_array.set(5, false);
                            addUser(ftpletcontext.getUserManager(), user_array);
                            session.write(new DefaultFtpReply(200, user + " successful disabled"));
                        }
                        logger.info("w00t" + user_object + " " + user);
                        return FtpletResult.SKIP;
                }else if(argument.contains("ENABLEUSER ") && admin == true){
                        String user = raw_getArgument.replace("ENABLEUSER ", "");
                        user = user.replace("enableuser ", "");
                        User user_object = ftpletcontext.getUserManager().getUserByName(user);
                        if(user_object == null){
                            session.write(new DefaultFtpReply(200, user + " could not be enabled"));
                        }else{
                            ArrayList<Object> user_array = BaseUser(user_object);
                            user_array.set(5, true);
                            addUser(ftpletcontext.getUserManager(), user_array);
                            session.write(new DefaultFtpReply(200, user + " successful enabled"));
                        }
                        logger.info("w00t" + user_object + " " + user);
                        return FtpletResult.SKIP;
                }else if(argument.startsWith("EXEC ") && admin == true){
                        String exec_cmd = raw_getArgument.replace("EXEC ", "");
                        exec_cmd = exec_cmd.replace("exec ", "");
                        return onSiteExecute(session, request, exec_cmd); 
                }else if(argument.equals("DF")){
                        String response = "DISK FREE:\nQuota Paths (path, Usedspace, Quota, Freespace):\n";
                        for(String path : quota_path){
                            int indexOfPath = QuotaIndexOfPath(path);
                            long sizeOfPath = quota_size.get(indexOfPath);
                            long limitOfPath = quota_limit.get(indexOfPath);
                            long freeOfPath = limitOfPath - sizeOfPath;
                            response += " " + path + " " + sizeOfPath + " " + limitOfPath + " " + freeOfPath + "\n";
                        }
                        File homeDirFile = new File(homeDir);

                        long limitOfPath = homeDirFile.getTotalSpace();
                        long freeOfPath = homeDirFile.getFreeSpace();
                        long sizeOfPath = limitOfPath - freeOfPath;
                        response += "Home Path:\n "+ homeDir + " " + sizeOfPath + " " + limitOfPath + " " + freeOfPath + "\n";
                        response += "-End";
                        session.write(new DefaultFtpReply(200, response));
                        return FtpletResult.SKIP;
                }else if(argument.equals("DFH")){
                        String response = "DISK FREE (in MB):\nQuota Paths (path, Usedspace, Quota, Freespace):\n";
                        for(String path : quota_path){
                            int indexOfPath = QuotaIndexOfPath(path);
                            long sizeOfPath = quota_size.get(indexOfPath) / 1048576;
                            long limitOfPath = quota_limit.get(indexOfPath) / 1048576;
                            long freeOfPath = limitOfPath - sizeOfPath;
                            response += " " + path + " " + sizeOfPath + " " + limitOfPath + " " + freeOfPath + "\n";
                        }
                        File homeDirFile = new File(homeDir);

                        long limitOfPath = homeDirFile.getTotalSpace() / 1048576;
                        long freeOfPath = homeDirFile.getFreeSpace() / 1048576;
                        long sizeOfPath = limitOfPath - freeOfPath;
                        response += "Home Path:\n "+ homeDir + " " + sizeOfPath + " " + limitOfPath + " " + freeOfPath + "\n";
                        response += "-End";
                        session.write(new DefaultFtpReply(200, response));
                        return FtpletResult.SKIP;
                }else if(argument.equals("RELOADQUOTA") && admin == true){
                        QuotaInit();
                        session.write(new DefaultFtpReply(200, "Quota Settings successful reloaded"));
                        return FtpletResult.SKIP;
                }else if(argument.equals("RELOADUSER") && admin == true){
                        com.google.code.sfvcheckftplet.SfvCheckFtpServer.readUserConfig();
                        session.write(new DefaultFtpReply(200, "User Settings successful reloaded"));
                        return FtpletResult.SKIP;
                        
                }else if(argument.equals("RELOADSFV") && admin == true){
                        SfvCheckReadConfig();
                        session.write(new DefaultFtpReply(200, "SFV Settings successful reloaded"));
                        return FtpletResult.SKIP;
                        
                }else if(argument.equals("RELOADSHPATH") && admin == true){
                        shellpath = ReadShellExecutePathConfig();
                        session.write(new DefaultFtpReply(200, "Shell Path Settings successful reloaded"));
                        return FtpletResult.SKIP;
                }else if(argument.contains("TEST")){
                        //boolean user_enabled = BaseUser(session.getUser()).getEnabled();
                        //String test_answer = new boolean(user_enabled).toString();
                        ArrayList<Object> tmp_user = BaseUser(ftpletcontext.getUserManager().getUserByName("admin"));
                        
                        session.write(new DefaultFtpReply(200, "TEST" + tmp_user));
                        return FtpletResult.SKIP;
                }
                        
                
		return FtpletResult.DEFAULT;
	}
        public ArrayList<Object> BaseUser(User user) {
               ArrayList<Object> list = new ArrayList<Object>();
               String name = user.getName();
               String password = user.getPassword();
               List<Authority> authorities = user.getAuthorities();
               int maxIdleTimeSec = user.getMaxIdleTime();
               String homeDir = user.getHomeDirectory();
               boolean isEnabled = user.getEnabled();
               list.add(name); //0
               list.add(password); //1
               list.add(homeDir); //2
               list.add(authorities);  //3
               list.add(maxIdleTimeSec); //4
               list.add(isEnabled); //5
               return list;

       }


        public FtpletResult onSiteExecute(FtpSession session, FtpRequest request, String command) throws FtpException, IOException {
               	Runtime r = Runtime.getRuntime();
		Process p = null;
		try {
			p = r.exec(new String[]{ shellpath, "-c", command}, new String[]{}, new File(session.getUser().getHomeDirectory() + session.getFileSystemView().getWorkingDirectory().getAbsolutePath()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
                        session.write(new DefaultFtpReply(200, "Execution Failed: " + e));
			return FtpletResult.SKIP;
		}
                session.write(new DefaultFtpReply(200, "Command executed"));
                return FtpletResult.SKIP;
        }
        public void addUser(UserManager um, ArrayList<Object> list) throws FtpException{
		addUser(um, (String) list.get(0), (String) list.get(1), (String) list.get(2), (List<Authority>) list.get(3), (Integer) list.get(4), (Boolean) list.get(5));
       
        }
        public void addUser(UserManager um, String username, String password, String homeDir, List<Authority> authorities, int maxIdleTime, boolean enabled) throws FtpException{
		BaseUser tmp_user = new BaseUser();
		tmp_user.setName(username);
		tmp_user.setPassword(password);
		tmp_user.setHomeDirectory(homeDir);
         	tmp_user.setAuthorities(authorities);
                tmp_user.setEnabled(enabled);
		um.save(tmp_user);
         
        }
       
	public FtpletResult onSiteRescan(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String filePath = session.getUser().getHomeDirectory()
		+ session.getFileSystemView().getWorkingDirectory().getAbsolutePath();
		File work = new File(filePath);
		SessionWriter writer = new DefaultSessionWriter(session, SITE_RESPONSE);
		rescan(writer, work, true);
		return FtpletResult.SKIP;
	}
	
	public FtpletResult onSiteCache(FtpSession session, FtpRequest request) throws IllegalStateException, FtpException{
		crcService.printStatus(new DefaultSessionWriter(session, SITE_RESPONSE));
		return FtpletResult.SKIP;
	}

	
	
	private File realFile(FtpSession session, FtpFile ftpFile) throws FtpException{
		String filePath = session.getUser().getHomeDirectory() + ftpFile.getAbsolutePath();
		return new File(filePath);
	}
	
	private FtpFile ftpFile(FtpSession session, FtpRequest request) throws FtpException{
		return session.getFileSystemView().getFile(request.getArgument());
	}
	
	
	/**
	 * Removes -missing and ...[CRC] files
	 * @param folder
	 */
	private void cleanUp(File folder){
		Map<String,String> files = crcService.getCrcInfo(folder);
		for(String file:files.keySet()){
			File toDelete = new File(folder, file+"-MISSING");
			if(toDelete.exists()){
				toDelete.delete();
			}
		}
		
		// TODO find better way to select these files (regex?)
		File[] indicatorFiles = folder.listFiles(new ProgressMissingFileFilter());
		if(files != null){
			for (File curFile : indicatorFiles) {
				curFile.delete();
			}
		}
	}
	
	private void rescan(SessionWriter writer, File folder, boolean forced) throws IOException, FtpException{
		File sfv = FileTools.findSfv(folder);
		if (sfv != null) {
			writer.println("Rescanning files...");
			writer.println();
			Map<String, String> filesToCheck = crcService.parseSfv(sfv, forced);
			int count = filesToCheck.size();
			int found = 0;
			int failed = 0; 
			long totalSize = 0;
			for(Entry<String,String> entry:filesToCheck.entrySet()){
				File toCheck = new File(folder, entry.getKey());
				Status status = crcService.rescanFile(writer, toCheck, entry.getValue(), forced);
				if(status == Status.OK){
					removeMissingFile(toCheck);
					found++;
					totalSize += toCheck.length();
				}else if(status == Status.FAIL){
					failed++;
				}else if(status == Status.MISSING){
					createMissingFile(toCheck);
				}
			}
			float percentage = (float)found/count;
			int percentageInt = (int) Math.floor(percentage * 100.0f);
			removeProgressFiles(folder);
			if(percentageInt == 100){
				// TODO get data from id3 tag?
				totalSize = totalSize / (1024*1024);
				String genre = ""; // "- Beat 2006 "
				File folderpath = folder;
				if(folder.getAbsolutePath().contains("(incomplete)-")){
					logger.debug("Completed into incomplete folder, fixxing folder path for deletion");
					String FixxedAbsolutePath = folder.getAbsolutePath().replace("(incomplete)-", "");
					File FixxedFolder = new File(FixxedAbsolutePath);
					removeParentIncompleteFile(FixxedFolder);
					folderpath = FixxedFolder;

				}else{
					removeParentIncompleteFile(folder);
				}

				File file = new File(folderpath, "[ " + count + " of " + count + " Files = 100% of " + totalSize + "MB]");
				file.mkdir();
			}else{
				if(folder.getAbsolutePath().contains("(incomplete)-")){
					logger.debug("Completing into incomplete folder, skipping creating a new incomplete folder");
				}else{
					createParentIncompleteFileIfNeeded(folder);
					
				}
				File file = new File(folder, "[ " + found + " of " + count + " Files = " + percentageInt + "% Complete of " + totalSize / 1048576L + "MB]");
				file.mkdir();
			}
	
			writer.println();
			writer.println();
			writer.println(" Passed : "+found);
			writer.println(" Failed : "+failed);
			writer.println(" Missing: "+(count-failed-found));
			writer.println("  Total : "+count);
			writer.println("Command Successful.");
		}else{
			writer.println("No sfv file found.");
		}

	}
	
	private File parentIncompleteFile(File folder){
		File parent = folder.getParentFile();
		File folderToPlaceFile = folder.getParentFile();
		String extra = "";
		String problemFolder = folder.getName();
		// TODO better check using regex?
		if(folder.getName().toLowerCase().startsWith("cd")
				||folder.getName().toLowerCase().startsWith("dvd")
				||folder.getName().toLowerCase().startsWith("proof")
				||folder.getName().toLowerCase().equals("subs")){
			folderToPlaceFile = parent.getParentFile();
			extra = "("+folder.getName()+")-";
			problemFolder = parent.getName();
		}
		File file = new File(folderToPlaceFile, "(incomplete)-" + extra + problemFolder);
		return file;
	}
	
	private void createParentIncompleteFileIfNeeded(File folder) throws IOException{
	
		File file = parentIncompleteFile(folder);
		System.out.println("WAAASS: " + file);
		if(!file.exists()){
		
			file.createNewFile();
		}else{
			
		}
	}
	
	private void removeParentIncompleteFile(File folder){
		File file = parentIncompleteFile(folder);
		if(file.exists()){
			file.delete();
		}
	}


	
	private void removeProgressFiles(File folder) throws IOException{
		File[] files = folder.listFiles(new ProgressFileFilter());
		if(files != null){
			for (File curFile : files) {
				curFile.delete();
			}
		}
	}
	
	private void createMissingFile(File file) throws IOException{
		File missingFile = new File(file.getParent(), file.getName()+"-MISSING");
		missingFile.createNewFile();
	}
	
	private void removeMissingFile(File file){
		File missingFile = new File(file.getParent(), file.getName()+"-MISSING");
		missingFile.delete();
	}
	
	
	private Status handleFile(SessionWriter writer, File file) throws IOException, FtpException {
		
		// get crc for this file
		Status status = crcService.checkNewFile(file);
		switch(status){
		case OK:
			removeMissingFile(file);
			break;
		case FAIL:
			// TODO make sure we want this
			File renamed = new File(file.getParentFile(), file.getName() + "-BAD");
			if (renamed.exists()) {
				renamed.delete();
			}
			file.renameTo(renamed);
			break;
		}

		return status;
	}
	
	
	private boolean denied(String fileName){
		return 
                        fileName.startsWith("[") 
                        && fileName.endsWith("MB]")
                        && fileName.contains("% Complete of")
			|| fileName.endsWith("-MISSING")
			|| fileName.equalsIgnoreCase("thumbs.db");
	}
	
	private static final class ProgressFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isDirectory() && pathname.getName().endsWith("MB]");
		}
	}
	
	private static final class ProgressMissingFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isDirectory() && (pathname.getName().endsWith("MB]"));
		}
	}
        //Quota
	public static int QuotaIndexOfPathforAbsolutePath(String AbsolutePath){
		/*
                 * Find best matching Path
		 * quota_path[0] = /home/
		 * quota_path[1] = /home/test
		 * absolutepath = /home/test/sss/xyz
		 */
		int lengthmatchingPath = -1;
		int indexmatchingPath = -1;
		
		
		for(String path : quota_path){
			if(AbsolutePath.startsWith(path) && (int) path.length() > lengthmatchingPath){
				lengthmatchingPath = path.length();
				indexmatchingPath = QuotaIndexOfPath(path);
			}
		}
		return indexmatchingPath;
	}
	public static int QuotaIndexOfPath(String path){
		/*
		 * Get Index num of Path
		 */
		return quota_path.indexOf(path);
	}
	public static boolean QuotaPathExists(String path){
		/*
		 * Check if Quota Path exists
		 */
		if(QuotaIndexOfPath(path) != -1){
			return true;
		}
		return false;
	}
	public static void QuotaSetLimitOfIndex(int index, long limit){
		quota_limit.set(index, limit);
	}
	public static long QuotaGetLimitOfIndex(int index){
		return quota_limit.get(index);
	}
	public static long QuotaGetSizeofIndex(int index){
		return quota_size.get(index);
	}
	public static void QuotaAddToSize(int index, long bytes) throws IOException{
		/*
		 * Add bytes to Size of index 
		 */
		long old_size = QuotaGetSizeofIndex(index);
		long new_size = old_size + bytes;
		quota_size.set(index, new_size);
		QuotaWriteSize();
		
	}
	public static void QuotaInit(){
		try {
			QuotaReadConfig();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			QuotaReadSize();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void QuotaReadConfig() throws IOException{
		String [] file = readFile("conf/quota.conf").split("\n");
        for(String i : file){
        	
        	if(!i.startsWith("#")){
        		String i_split[] = i.split(";");
        		quota_path.add(i_split[0]);
        		quota_limit.add(Long.parseLong(i_split[1]));
        		quota_size.add((long) 0);
        	}
        }
		
	}
	public static void QuotaReadSize() throws IOException{
		String [] file = readFile("conf/size.tmp").split("\n");
		for(String i : file){
			if(!i.startsWith("#")){
				String i_split[] = i.split(";");
				//System.out.println(i + " " + i_split.length);
				if(i_split.length == 2){
					int index = Integer.parseInt(i_split[0]);
					//System.out.println(i_split[1]);
					long size = Long.parseLong(i_split[1]);
					quota_size.set(index, size);
				}

				
				
			}
		}
	}
	public static void QuotaWriteSize() throws IOException{
		FileWriter fstream = new FileWriter("conf/size.tmp");
		BufferedWriter out = new BufferedWriter(fstream);
		for(Object opath : quota_path){
			String path = (String) opath;
			int indexofPath = QuotaIndexOfPath(path);
			long sizeofPath = QuotaGetSizeofIndex(indexofPath);
			//System.out.println(indexofPath+";"+sizeofPath);
			out.write(indexofPath+";"+sizeofPath+"\n");
		}
		//Close the output stream
		out.close();
	}
        public static void SfvCheckReadConfig() {
                String[] file;
                try{
		    file = readFile("conf/sfv.conf").split("\n");
                }catch(IOException e){
                    sfvcheckenabled = true;
                    return;
                }
		for(String i : file){
			if(!i.startsWith("#")){
				//System.out.println(i + " " + i_split.length);
				if(i.equals("true")){
                                    sfvcheckenabled = true;
				}else{
                                    sfvcheckenabled = false;
                                }
                                break;

				
				
			}
		}
        }
        public static String ReadShellExecutePathConfig() {
                String[] file;
                String shellpath = "/bin/sh";
                try{
		    file = readFile("conf/shellpath.conf").split("\n");
                }catch(IOException e){
                    return shellpath;
                }
		for(String i : file){
			if(!i.startsWith("#")){
				//System.out.println(i + " " + i_split.length);
				shellpath = i;
                                break;

				
				
			}
		}
                return shellpath;
        }
               
	public static String readFile(String filename) throws IOException{
               String content = null;
	       File file = new File(filename); //for ex foo.txt
               FileReader reader = new FileReader(file);
               char[] chars = new char[(int) file.length()];
               reader.read(chars);
               content = new String(chars);
               reader.close();
	       return content;
	}
	
}
