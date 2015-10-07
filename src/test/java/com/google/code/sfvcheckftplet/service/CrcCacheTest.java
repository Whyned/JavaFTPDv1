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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class CrcCacheTest {

	private CrcCache cache;
	
	@Before
	public void init(){
		cache = new CrcCache();
		cache.init();
	}
	
	@After
	public void shutdown(){
		cache.shutdown();
	}
	
	
	@Test
	public void testFileCrcStuff(){
		File test = new File("/a/b/c/junit.test.file");
		cache.putFileCrc(test, 12345L);
		Long value = cache.getFileCrc(test);
		Assert.assertEquals(Long.valueOf(12345), value);
		

		// simulate restart
		cache.shutdown();
		cache = new CrcCache();
		cache.init();
		
		value = cache.getFileCrc(test);
		Assert.assertEquals(Long.valueOf(12345), value);
		
		cache.removeFileCrc(test);
		Long valueDeleted = cache.getFileCrc(test);
		Assert.assertNull(valueDeleted);
	}
}
