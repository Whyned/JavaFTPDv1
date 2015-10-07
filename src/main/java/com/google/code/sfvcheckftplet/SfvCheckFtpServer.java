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
import java.util.ArrayList;
import java.util.List;
import java.io.FileReader;
import java.io.IOException;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;

import org.apache.ftpserver.ssl.SslConfigurationFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SfvCheckFtpServer {

	private static final Logger logger = LoggerFactory.getLogger(FtpServer.class);
 

	private static final String DEFAULT_HOME_DIR = "./";
        private static UserManager um;
        private static String homeDir = DEFAULT_HOME_DIR;
	public static void main(String[] args) {
		
		if(args.length == 1){
			homeDir = args[0];
		}
		try {
			new SfvCheckFtpServer().start(homeDir);
		} catch (FtpException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add shutdown hook.
	 */

	void start(final String homeDir) throws FtpException {

	        int DEFAULT_PORT = readPortConfig();
		ListenerFactory factory = new ListenerFactory();
		// set the port of the listener
		factory.setPort(DEFAULT_PORT);

		PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
		// userManagerFactory.setFile(new File("myusers.properties"));
		userManagerFactory.setPasswordEncryptor(new SaltedPasswordEncryptor());
		userManagerFactory.setAdminName("admin");
                
		um = userManagerFactory.createUserManager();
		
                addUser("admin", "adminadmin", homeDir, true, 0, 0, 0, 0, true);
                System.out.println(homeDir);
                readUserConfig(new File(homeDir).getAbsolutePath() + "/conf/users.conf");
		FtpServerFactory serverFactory = new FtpServerFactory();
		// replace the default listener

		// replace the default listener
		serverFactory.addListener("default", factory.createListener());
		serverFactory.getFtplets().put("SfvCheckFtpLet", new SfvCheckFtpLet());
		
		serverFactory.setUserManager(userManagerFactory.createUserManager());
		serverFactory.setUserManager(um);
                ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
                connectionConfigFactory.setMaxLogins(20);
                serverFactory.setConnectionConfig(connectionConfigFactory.createConnectionConfig());

		FtpServer server = serverFactory.createServer();

		// add shutdown hook if possible
		addShutdownHook(server);

               

		// start the server
		server.start();
		logger.info("Serving folder: " + new File(homeDir).getAbsolutePath());
		logger.info("Try connecting to localhost on port " + DEFAULT_PORT);
                logger.info("Maxlogins: " + connectionConfigFactory.getMaxLogins()); 
                logger.info("Admin: " + um.getAdminName());
	}

        public static void addUser(String username, String password, String homeDir, boolean perm_write, int maxLogins, int maxLoginsPerIP, int downloadRate, int uploadRate, boolean enabled) throws FtpException{
		BaseUser tmp_user = new BaseUser();
		tmp_user.setName(username);
		tmp_user.setPassword(password);
		tmp_user.setHomeDirectory(homeDir);
                tmp_user.setEnabled(enabled);
		List<Authority> tmp_auths = new ArrayList<Authority>();
                if(perm_write == true){
		    tmp_auths.add(new WritePermission());                         
                }
                tmp_auths.add(new ConcurrentLoginPermission(maxLogins, maxLoginsPerIP));
                tmp_auths.add(new TransferRatePermission(downloadRate, uploadRate));
		tmp_user.setAuthorities(tmp_auths);
                
		um.save(tmp_user);
        }
	public static String readFile(String filename) throws IOException
	{
	       String content = null;
	       File file = new File(filename); //for ex foo.txt
               FileReader reader = new FileReader(file);
               char[] chars = new char[(int) file.length()];
               reader.read(chars);
               content = new String(chars);
               reader.close();
	       return content;
	}
        public static void readUserConfig() throws FtpException
        {
               readUserConfig(new File(homeDir).getAbsolutePath() + "/conf/users.conf");

        }
	public static void readUserConfig(String filename) throws FtpException
	{
		String test = null;
        	try{
        		test = readFile(filename);
        	
	        }
	        catch(java.io.FileNotFoundException e){
	            System.out.println("User Config File not found: " + filename);	

	        
                }
                catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
                        return;
		}
 	       String[] users = test.split("\n");
 	       for(int i = 0; i < users.length; i++){
   		     	String userLine = users[i];
        		if(userLine.startsWith("#")){
      		  		
        		}else{
        			String[] userSettings = userLine.split(";");
        			System.out.println(userLine);
        			System.out.println(userSettings[1]);
        			System.out.println(userSettings.length);
        			if(userSettings.length == 9){
        				String username = userSettings[0];
        				String password = userSettings[1];
        				String homedir = userSettings[2];
        				boolean writeperm = new Boolean(userSettings[3]);
        				int maxlogins = Integer.parseInt(userSettings[4]);
        				int maxloginsperip = Integer.parseInt(userSettings[5]);
        				int downloadrate = Integer.parseInt(userSettings[6]);
        				int uploadrate = Integer.parseInt(userSettings[7]);
                                        boolean enabled = new Boolean(userSettings[8]);
        	                        homedir = homedir.replace("%home%", homeDir);
        	                        addUser(username, password, homedir, writeperm, maxlogins, maxloginsperip, downloadrate, uploadrate, enabled);
        		
    				
        			}
    		    	}
             }
        
	}
        public static int readPortConfig() throws FtpException
        {
          return readPortConfig(new File(homeDir).getAbsolutePath() + "/conf/port.conf");
        }
	public static int readPortConfig(String filename) throws FtpException
        {
		String test = null;
        	try{
        		test = readFile(filename);
        	
	        }
	        catch(java.io.FileNotFoundException e){
	            System.out.println("Port config File not found:" + filename);	
                    int port = 2221;

 	       	    return port;
	        
                }
                catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
                        int port = 2221;
                        return port;
		}
                int port = Integer.parseInt(test.split("\n")[0]);
                return port;
        }
	private static void addShutdownHook(final FtpServer engine) {

		// create shutdown hook
		Runnable shutdownHook = new Runnable() {
			public void run() {
				System.out.println("Stopping server...");
				engine.stop();
			}
		};

		// add shutdown hook
		Runtime runtime = Runtime.getRuntime();
		runtime.addShutdownHook(new Thread(shutdownHook));
	}
}
