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

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpSession;

class DefaultSessionWriter implements SessionWriter {
	private final FtpSession session;
	private final int defaultReplyCode;
	
	public DefaultSessionWriter(final FtpSession session, final int defaultReplyCode) {
		this.session = session;
		this.defaultReplyCode = defaultReplyCode; 
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println()
	 */
	public void println() throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, ""));
		}
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println(java.lang.String)
	 */
	public void println(String message) throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, message));
		}
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println(java.lang.String[])
	 */
	public void println(String[] messages) throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, messages));
		}
	}
}