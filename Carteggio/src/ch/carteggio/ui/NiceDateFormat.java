/*******************************************************************************
 * Copyright (c) 2014, Lorenzo Keller
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ch.carteggio.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.annotation.SuppressLint;

public class NiceDateFormat {

	@SuppressLint("SimpleDateFormat")
	public static String niceDate(Date date) {
		
		Calendar lastActivityCalendar = Calendar.getInstance();		
		lastActivityCalendar.setTime(date);
		
		Calendar nowCalendar = Calendar.getInstance();
		
		if ( lastActivityCalendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR)  && 
				lastActivityCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) ) {
		
			SimpleDateFormat format = new SimpleDateFormat("HH:mm");		
			return format.format(date);					
			
		} else {
			
			SimpleDateFormat format = new SimpleDateFormat("yy/MM/dd HH:mm");		
			return format.format(date);			
			
		}	
	}
	
}
