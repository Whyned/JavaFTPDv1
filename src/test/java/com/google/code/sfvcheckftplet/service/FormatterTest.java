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

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import com.google.code.sfvcheckftplet.Formatter;

public class FormatterTest {

	@Test
	public void testProgressBar() {
		Formatter formatter = new Formatter();
		Random random = new Random();
		int length = random.nextInt(50)+3;
		String bar = formatter.progressBar(Math.random(), length);
		assertEquals(length, bar.length());
		System.out.println(bar);
		
		
		bar = formatter.progressBar(0, length);
		assertFalse(bar.contains("#"));
		assertTrue(bar.contains(":"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testProgressBarFail() {
		Formatter formatter = new Formatter();
		formatter.progressBar(0.5, 2);
	}

}
