/*
 * Copyright 2011 Joseph Cloutier, Daniel Reeves, Bethany Soule
 * 
 * This file is part of TagTime.
 * 
 * TagTime is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * TagTime is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with TagTime. If not, see <http://www.gnu.org/licenses/>.
 */

package tagtime.log;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import tagtime.Main;
import tagtime.TagTime;
import tagtime.quartz.RandomizedTrigger;
import tagtime.util.BackwardsAccessFile;

/**
 * Keeps a persistent log of all tag data, and (optionally) submits the
 * data to Beeminder. It additionally tracks what data has and has not
 * been submitted yet, so if the user is offline and Beeminder can't be
 * reached, the data can be submitted later.
 */
public class Log {
	public final TagTime tagTimeInstance;
	
	private final BackwardsAccessFile logFile;
	
	private long lastTimestamp = -1;
	
	public Log(TagTime tagTimeInstance) throws IOException {
		this.tagTimeInstance = tagTimeInstance;
		
		File filePath = new File(Main.getDataDirectory().getPath() + "/" +
					tagTimeInstance.settings.username + ".log");
		
		//this will create the file if necessary
		logFile = new BackwardsAccessFile(filePath, "rw");
		
		findLastTimestamp();
	}
	
	/**
	 * Records the given ping in the log.
	 * @param timestamp The time, in milliseconds, corresponding to the
	 *            data. Assuming this data was generated by a standard
	 *            tag popup window, this should be the time the window
	 *            was created.
	 * @param data The data to log. This should include both
	 *            user-generated tags and any automatically-generated
	 *            data, but it does not need to include a timestamp.
	 */
	public void log(long timestamp, String data) {
		//convert to Unix time (that is, use seconds, not milliseconds)
		long timestampInSeconds = timestamp / 1000;
		
		StringBuffer extraData = null;
		
		try {
			logFile.seek(logFile.length());
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		if(timestampInSeconds > lastTimestamp) {
			lastTimestamp = timestampInSeconds;
		} else {
			//special case: check if this ping goes immediately before
			//the final line of the file
			long prevTimestamp = timestampInSeconds + 1;
			try {
				logFile.seekLastLine("");
				String prevLine = logFile.readPreviousLine("0123456789");
				prevTimestamp = Long.parseLong(prevLine.substring(0,
								prevLine.indexOf(' ')));
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			//if it does, simply place it there
			if(prevTimestamp < timestampInSeconds) {
				//the pointer is already in the correct spot
			}

			//if not, search from the beginning
			else {
				try {
					long nextTimestamp = 0;
					logFile.seek(0);
					String line;
					
					while(nextTimestamp < timestampInSeconds) {
						line = logFile.readLine();
						try {
							nextTimestamp = Long.parseLong(line.substring(0,
											line.indexOf(' ')));
						} catch(NumberFormatException e) {}
					}
					
					logFile.seekLineStart("0123456789");
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
			
			//copy all data following the file pointer, so that the
			//current data can be inserted without overwriting anything
			try {
				long insertionPosition = logFile.getFilePointer();
				extraData = new StringBuffer();
				String line;
				
				while((line = logFile.readLine()) != null) {
					extraData.append(line);
					extraData.append('\n');
				}
				
				logFile.seek(insertionPosition);
			} catch(IOException e) {
				e.printStackTrace();
				try {
					logFile.seek(logFile.length());
				} catch(IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		
		//combine the timestamp and tags
		String annotatedData = Long.toString(timestampInSeconds) + " " + data;
		
		//pad the string with spaces until it is 55 characters long
		int paddingNeeded = 55 - annotatedData.length();
		if(paddingNeeded > 0) {
			annotatedData += String.format("%1$" + paddingNeeded + "c", ' ');
		} else {
			annotatedData += " ";
		}
		
		//add a human-readable timestamp and end the line
		annotatedData += "[" + DateFormat.getDateTimeInstance().format(timestamp) + "]\n";
		
		//write the data to the file
		try {
			logFile.writeBytes(annotatedData);
			
			//if the line needed to be inserted, re-write all following lines
			if(extraData != null) {
				logFile.writeBytes(extraData.toString());
			}
		} catch(IOException e) {
			System.err.println("Unable to write this line to the log file:");
			System.err.println(annotatedData);
			e.printStackTrace();
		}
	}
	
	private void findLastTimestamp() {
		try {
			//get the final line with a digit on it
			String timestamp = logFile.readLastLine("0123456789");
			
			if(timestamp == null) {
				return;
			}
			
			timestamp = timestamp.substring(0, timestamp.indexOf(' '));
			lastTimestamp = Long.parseLong(timestamp);
		} catch(IOException e) {}
	}
	
	/**
	 * @return The last recorded timestamp in the log file. Returns -1 if
	 *         there are no recorded timestamps.
	 */
	public long getLastTimestamp() {
		return lastTimestamp;
	}
	
	/**
	 * Logs all pings that were skipped since the latest entry in the log
	 * file, marking them as "afk RETRO", optionally with more tags.
	 * @param extraTags Additional tags to add between "afk" and "RETRO".
	 *            This string does not need to start or end with a space.
	 * @param until The time at which to stop logging missed pings.
	 */
	public void logMissedPings(String extraTags, long until) {
		//determine the string to mark missed pings with
		String tags = "afk";
		
		if(extraTags != null && !extraTags.equals("")) {
			tags += " " + extraTags;
		}
		
		RandomizedTrigger trigger = tagTimeInstance.trigger;
		long lastPing = getLastTimestamp();
		
		if(lastPing != -1) {
			//lastPing was rounded down when converted to seconds, so if
			//we don't add 1, it will most likely (999/1000) repeat a ping
			Date ping = new Date((lastPing + 1) * 1000);
			
			for(ping = trigger.getFireTimeAfter(ping, true); ping.getTime() < until; ping =
						trigger.getFireTimeAfter(ping, true)) {
				logRetro(ping.getTime(), tags);
			}
		}
	}
	
	/**
	 * Logs an automatically-generated message.
	 * @param timestamp The time to record in the log file.
	 * @param data The automatically-generated tags to log, not including
	 *            the "RETRO" tag at the end.
	 */
	public void logRetro(long timestamp, String data) {
		log(timestamp, data + " RETRO");
	}
}
