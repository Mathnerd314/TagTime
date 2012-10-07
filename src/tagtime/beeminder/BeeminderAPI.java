/*
 * Copyright 2011-2012 Joseph Cloutier
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

package tagtime.beeminder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import tagtime.Main;
import tagtime.TagTime;
import tagtime.settings.SettingType;
import tagtime.settings.Settings;

/**
 * TODO: Refactor some methods from BeeminderGraphData into this, and
 * vice versa. Currently, the class names don't particularly correspond
 * to what they're responsible for.
 */
public class BeeminderAPI {
	private static final JSONParser JSON_PARSER = new JSONParser();
	private static final String API_BASE_URL = "https://www.beeminder.com/api/v1";
	
	private final Settings userSettings;
	private final List<BeeminderGraphData> graphData;
	
	public BeeminderAPI(TagTime tagTimeInstance, Settings userSettings) throws ClassCastException {
		this.userSettings = userSettings;
		
		String username = tagTimeInstance.username;
		
		Collection<String> graphDataEntries = userSettings
					.getListValue(SettingType.BEEMINDER_GRAPHS);
		
		graphData = new ArrayList<BeeminderGraphData>(graphDataEntries.size());
		for(String dataEntry : graphDataEntries) {
			graphData.add(new BeeminderGraphData(tagTimeInstance, username, dataEntry));
		}
	}
	
	/**
	 * Submits the current user's data to each registered graph.<br>
	 * TODO: Refactor this into a different class.
	 */
	public void submit() {
		String username = userSettings.username;
		
		File logFile = new File(Main.getDataDirectory().getPath()
					+ "/" + username + ".log");
		
		/*BufferedWriter beeFile = null;
		try {
			beeFile = new BufferedWriter(new FileWriter(
						new File(Main.getDataDirectory().getName()
									+ "/" + username + ".bee")));
		} catch(IOException e) {
			e.printStackTrace();
		}*/

		for(BeeminderGraphData data : graphData) {
			data.submitPings(logFile);
		}
	}
	
	public static long fetchResetDate(HttpClient client,
				String graphName, TagTime tagTimeInstance) {
		JSONArray parsedArray = runGetRequest(client,
					getGraphURL(tagTimeInstance, graphName),
					tagTimeInstance);
		if(parsedArray == null) {
			return 0;
		}
		
		//Unless the API is updated, the request will return one object.
		JSONObject data = (JSONObject) parsedArray.get(0);
		if(data.containsKey("reset")) {
			return (Long) data.get("reset");
		}
		
		return 0;
	}
	
	public static List<DataPoint> fetchAllDataPoints(HttpClient client,
				String graphName, TagTime tagTimeInstance) {
		JSONArray parsedData = runGetRequest(client,
					getDataURL(tagTimeInstance, graphName),
					tagTimeInstance);
		if(parsedData == null) {
			return null;
		}
		
		//convert the data to a set of data points
		List<DataPoint> dataPoints = new ArrayList<DataPoint>();
		DataPoint dataPoint;
		DataPoint prevDataPoint = null;
		int insertIndex;
		
		JSONObject jsonDataPoint;
		for(Object inputDataPoint : parsedData) {
			if(inputDataPoint instanceof JSONObject) {
				jsonDataPoint = (JSONObject) inputDataPoint;
				dataPoint = new DataPoint((String) jsonDataPoint.get("id"),
										(Long) jsonDataPoint.get("timestamp"),
										(Double) jsonDataPoint.get("value"),
										(String) jsonDataPoint.get("comment"));
				
				//insert the new data point such that all data points are in order
				if(prevDataPoint != null && prevDataPoint.timestamp > dataPoint.timestamp) {
					for(insertIndex = dataPoints.size() - 2; insertIndex >= 0; insertIndex--) {
						if(dataPoints.get(insertIndex).timestamp <= dataPoint.timestamp) {
							break;
						}
					}
					dataPoints.add(insertIndex + 1, dataPoint);
				} else {
					dataPoints.add(dataPoint);
				}
				
				prevDataPoint = dataPoint;
			}
		}
		
		return dataPoints;
	}
	
	/**
	 * Creates a new data point on Beeminder.
	 * @return Whether the request completed successfully. If this is
	 *         false, then Beeminder is probably inaccessible, and no
	 *         more requests should be sent for now.
	 */
	public static boolean createDataPoint(HttpClient client, TagTime tagTimeInstance,
				String graphName, DataPoint dataPoint, NumberFormat hourFormatter) {
		if(runPostRequest(client, tagTimeInstance,
					getDataURL(tagTimeInstance, graphName),
					buildPostData(new String[] {
								"timestamp", Long.toString(dataPoint.timestamp),
								"value", hourFormatter.format(dataPoint.hours)}))) {
			return true;
		}
		
		System.err.println("Unable to submit your data to Beeminder " +
					"graph " + graphName + ". Please try again later.");
		return false;
	}
	
	/**
	 * Creates a new data point on Beeminder.
	 * @return Whether the request completed successfully. If this is
	 *         false, then Beeminder is probably inaccessible, and no
	 *         more requests should be sent for now.
	 */
	public static boolean updateDataPoint(HttpClient client, TagTime tagTimeInstance,
				String graphName, DataPoint dataPoint, NumberFormat hourFormatter) {
		List<NameValuePair> postData = buildPostData(new String[] {
					"timestamp", Long.toString(dataPoint.timestamp),
					"value", hourFormatter.format(dataPoint.hours)});
		
		if(dataPoint.comment.length() > 0) {
			postData.add(new BasicNameValuePair("comment", dataPoint.comment));
		}
		
		if(runPutRequest(client, tagTimeInstance,
					getDataPointURL(tagTimeInstance, graphName, dataPoint.id),
					postData)) {
			return true;
		}
		
		System.err.println("Unable to submit your data to Beeminder " +
					"graph " + graphName + ". Please try again later.");
		return false;
	}
	
	/**
	 * Removes a data point from Beeminder.
	 * @return Whether the request completed successfully. If this is
	 *         false, then Beeminder is probably inaccessible, and no
	 *         more requests should be sent for now.
	 */
	public static boolean deleteDataPoint(HttpClient client, TagTime tagTimeInstance,
				String graphName, DataPoint dataPoint) {
		if(runDeleteRequest(client, tagTimeInstance,
					getDataPointURL(tagTimeInstance, graphName, dataPoint.id))) {
			return true;
		}
		
		System.err.println("Unable to submit your data to Beeminder " +
					"graph " + graphName + ". Please try again later.");
		return false;
	}
	
	private static boolean runDeleteRequest(HttpClient client, TagTime tagTimeInstance,
				String targetURL) {
		HttpDelete deleteRequest = new HttpDelete(targetURL
					+ getAuthTokenToAppend(tagTimeInstance));
		
		System.out.println("DELETE " + deleteRequest.getURI());
		
		HttpResponse response;
		try {
			response = client.execute(deleteRequest);
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
		
		StatusLine status = response.getStatusLine();
		
		System.out.println("Response: " + status.getStatusCode()
					+ " " + status.getReasonPhrase());
		
		try {
			EntityUtils.consume(response.getEntity());
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	/**
	 * Runs a put request.
	 * @return Whether the request completed successfully. If this is
	 *         false, then Beeminder is probably inaccessible, and no
	 *         more requests should be sent for now.
	 */
	private static boolean runPutRequest(HttpClient client, TagTime tagTimeInstance,
				String dataURL, List<NameValuePair> postData) {
		//add the authorization token
		postData.add(new BasicNameValuePair("auth_token",
					tagTimeInstance.settings.getStringValue(SettingType.AUTH_TOKEN)));
		
		//build the request
		HttpPut putRequest = new HttpPut(dataURL);
		HttpResponse response;
		
		try {
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(postData);
			System.out.println("PUT " + dataURL + "?"
						+ new BufferedReader(new InputStreamReader(entity.getContent()))
									.readLine());
			
			putRequest.setEntity(entity);
			
			response = client.execute(putRequest);
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
		
		StatusLine status = response.getStatusLine();
		
		System.out.println("Response: " + status.getStatusCode()
					+ " " + status.getReasonPhrase());
		
		try {
			EntityUtils.consume(response.getEntity());
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	/**
	 * Runs a post request.
	 * @return Whether the request completed successfully. If this is
	 *         false, then Beeminder is probably inaccessible, and no
	 *         more requests should be sent for now.
	 */
	private static boolean runPostRequest(HttpClient client, TagTime tagTimeInstance,
				String dataURL, List<NameValuePair> postData) {
		//add the authorization token
		postData.add(new BasicNameValuePair("auth_token",
					tagTimeInstance.settings.getStringValue(SettingType.AUTH_TOKEN)));
		
		//build the request
		HttpPost postRequest = new HttpPost(dataURL);
		HttpResponse response;
		
		try {
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(postData);
			System.out.println("POST " + dataURL + "?"
						+ new BufferedReader(new InputStreamReader(entity.getContent()))
									.readLine());
			
			postRequest.setEntity(entity);
			
			response = client.execute(postRequest);
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
		
		StatusLine status = response.getStatusLine();
		
		System.out.println("Response: " + status.getStatusCode()
					+ " " + status.getReasonPhrase());
		
		try {
			EntityUtils.consume(response.getEntity());
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	private static JSONArray runGetRequest(HttpClient client, String url,
				TagTime tagTimeInstance) {
		HttpGet getRequest = new HttpGet(
					url + getAuthTokenToAppend(tagTimeInstance));
		HttpResponse response;
		OutputStream data;
		
		//retrieve the data
		try {
			response = client.execute(getRequest);
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
		
		if(response.getStatusLine().getStatusCode() == 401) {
			System.err.println("Invalid authorization token. Visit " +
						"https://www.beeminder.com/api/v1/auth_token.json to " +
						"get your token, then add it to your settings file.");
			return null;
		}
		
		try {
			data = new ByteArrayOutputStream(response.getEntity().getContent().available());
			response.getEntity().writeTo(data);
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
		
		//parse the data (TODO: is it really necessary to use a
		//full-featured json parser here?)
		Object parseResult;
		JSONArray parsedArray = null;
		try {
			parseResult = JSON_PARSER.parse(data.toString());
		} catch(ParseException e) {
			System.err.println(data.toString());
			e.printStackTrace();
			parseResult = null;
		}
		
		//close the stream used to read the response
		try {
			data.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		try {
			EntityUtils.consume(response.getEntity());
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		//JSONParser.parse() can return multiple object types
		//TODO: (Possibly) find a better parser.
		if(parseResult instanceof JSONArray) {
			parsedArray = (JSONArray) parseResult;
		} else if(parseResult instanceof JSONObject) {
			parsedArray = new JSONArray();
			
			//How am I even supposed to fix this warning without changing
			//the JSONArray source code?
			parsedArray.add(parseResult);
		} else {
			System.out.println("Unknown result from JSON parser: " + parseResult);
		}
		
		return parsedArray;
	}
	
	/**
	 * @param dataPairs The parameters and values to post, in string
	 *            form. They will be parsed in order, like so: [param0,
	 *            value0, param1, value1, ...], and if there are an odd
	 *            number, the last will be ignored. These parameters do
	 *            not need to include the authorization token; that will
	 *            be added.
	 * @return The data pairs, in the correct format to be used as an
	 *         HTML entity.
	 */
	private static List<NameValuePair> buildPostData(String[] dataPairs) {
		List<NameValuePair> postData = new ArrayList<NameValuePair>();
		for(int i = 0; i < dataPairs.length - 1; i += 2) {
			postData.add(new BasicNameValuePair(dataPairs[i], dataPairs[i + 1]));
		}
		
		return postData;
	}
	
	private static String getGraphURL(TagTime tagTimeInstance, String graphName) {
		return API_BASE_URL + "/users/" + tagTimeInstance.username
					+ "/goals/" + graphName + ".json";
	}
	
	private static String getDataURL(TagTime tagTimeInstance, String graphName) {
		return API_BASE_URL + "/users/" + tagTimeInstance.username
					+ "/goals/" + graphName + "/datapoints.json";
	}
	
	private static String getDataPointURL(TagTime tagTimeInstance, String graphName,
									String dataPointID) {
		return API_BASE_URL + "/users/" + tagTimeInstance.username
					+ "/goals/" + graphName
					+ "/datapoints/" + dataPointID + ".json";
	}
	
	private static String getAuthTokenToAppend(TagTime tagTimeInstance) {
		return "?auth_token=" + tagTimeInstance.settings.getStringValue(SettingType.AUTH_TOKEN);
	}
}
