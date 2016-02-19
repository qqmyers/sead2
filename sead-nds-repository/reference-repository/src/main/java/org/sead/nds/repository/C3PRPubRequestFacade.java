/*
 * Copyright 2015 University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author myersjd@umich.edu
 */

package org.sead.nds.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Properties;

import javax.ws.rs.core.MediaType;

import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class C3PRPubRequestFacade {

	private static final Logger log = Logger
			.getLogger(C3PRPubRequestFacade.class);
	BasicCookieStore cookieStore = new BasicCookieStore();
	private int timeout = 5;
	private RequestConfig config = RequestConfig.custom()
	  .setConnectTimeout(timeout * 1000)
	  .setConnectionRequestTimeout(timeout * 1000)
	  .setSocketTimeout(timeout * 1000).build();
	
	private CloseableHttpClient client = HttpClientBuilder.create()
			.setDefaultCookieStore(cookieStore).setDefaultRequestConfig(config).build();
	Properties props = null;
	String RO_ID = null;
	String bearerToken = null;
	
	public static final String SUCCESS_STAGE = "Success";
	public static final String FAILURE_STAGE = "Failure";
	public static final String PENDING_STAGE = "Pending";
	public static final String PROBLEM_STAGE = "Problem";

	private JSONObject request = null;
	private JSONObject oremap = null;

	public C3PRPubRequestFacade(String RO_ID, Properties props) {
		this.RO_ID = RO_ID;
		this.props = props;
	}

	private String proxyIfNeeded(String urlString) {

		if (props.containsKey("JSESSIONID")) {
			return props.getProperty("c3pr.address")
					+ urlString.substring(urlString.indexOf("api"));
		} else {
			return urlString;
		}
	}

	// Logic to decide if this is a container -
	// first check for children, then check for source-specific type indicators
	boolean childIsContainer(int index) {
		JSONObject item = getOREMap().getJSONObject("describes")
				.getJSONArray("aggregates").getJSONObject(index);
		if (getChildren(item).length() != 0) {
			return true;
		}
		Object o = item.get("@type");
		if (o != null) {
			if (o instanceof JSONArray) {
				for (int i = 0; i < ((JSONArray) o).length(); i++) {
					if ("http://cet.ncsa.uiuc.edu/2007/Collection"
							.equals(((JSONArray) o).getString(i))) {
						return true;
					}
					// Check for Clowder type
				}
			} else if (o instanceof String) {
				if ("http://cet.ncsa.uiuc.edu/2007/Collection"
						.equals((String) o)) {
					return true;
				}
				// Check for Clowder type
			}
		}
		return false;
	}

	// Get's all "Has Part" children, standardized to send an array with 0,1, or
	// more elements
	JSONArray getChildren(JSONObject parent) {
		Object o = null;
		try {
			o = parent.get("Has Part");
		} catch (JSONException e) {
			// Doesn't exist - that's OK
		}
		if (o == null) {
			return new JSONArray();
		} else {
			if (o instanceof JSONArray) {
				return (JSONArray) o;
			} else if (o instanceof String) {
				return new JSONArray("[	" + (String) o + " ]");
			}
			log.error("Error finding children: " + o.toString());
			return new JSONArray();
		}
	}

	JSONObject getPublicationRequest() {
		if (request == null) {
			String c3prServer = props.getProperty("c3pr.address");
			HttpGet getPubRequest;
			try {
				log.debug("Retrieving: " + c3prServer + "api/researchobjects/"
						+ URLEncoder.encode(RO_ID, "UTF-8"));
				getPubRequest = new HttpGet(c3prServer + "api/researchobjects/"
						+ URLEncoder.encode(RO_ID, "UTF-8"));

				if (props.containsKey("JSESSIONID")) {
					// Proxy Mode
					log.debug("Adding: " + props.getProperty("JSESSIONID"));

					BasicClientCookie cookie = new BasicClientCookie(
							"JSESSIONID", props.getProperty("JSESSIONID"));
					URL c3pr = new URL(c3prServer);

					cookie.setDomain(c3pr.getHost());
					cookie.setPath("/");
					cookie.setSecure(c3pr.getProtocol().equalsIgnoreCase(
							"https") ? true : false);
					cookieStore.addCookie(cookie);
				}
				getPubRequest.addHeader("accept", "application/json");

				CloseableHttpResponse response = client.execute(getPubRequest);

				if (response.getStatusLine().getStatusCode() == 200) {
					String mapString = EntityUtils.toString(response
							.getEntity());
					log.trace(mapString);
					request = new JSONObject(mapString);

				}
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				log.error("Unable to retrieve pub request document", e);
				e.printStackTrace();
			}

			// Set the bearerToekn for any calls to the originating space
			// related to
			// this RO
			if (request.has("Bearer Token")) {
				bearerToken = request.getString("Bearer Token");
			} else if (props.containsKey("bearertoken.default")) {
				bearerToken = props.getProperty("bearertoken.default");
			}
		}
		return request;
	}

	private URI getOREMapURI() {
		try {
			return new URI(proxyIfNeeded(request.getJSONObject("Aggregation")
					.getString("@id")));
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public JSONObject getOREMap() {
		if (oremap == null) {

			log.debug("Retreiving: " + getOREMapURI().toString());
			HttpGet getMap = createNewGetRequest(getOREMapURI(),
					MediaType.APPLICATION_JSON);
			try {
				CloseableHttpResponse response = client.execute(getMap);

				if (response.getStatusLine().getStatusCode() == 200) {
					String mapString = EntityUtils.toString(response
							.getEntity());
					log.trace("OREMAP: " + mapString);
					oremap = new JSONObject(mapString);
				}
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				log.error("Unable to retrieve OREMap", e);
				e.printStackTrace();
			}
		}
		return oremap;
	}

	private HttpGet createNewGetRequest(URI url, String returnType) {

		HttpGet request = null;

		if (bearerToken != null) {
			// Clowder Kluge - don't add key once Auth header is accepted
			try {
				request = new HttpGet(new URI(url.toURL().toString() + "?key="
						+ bearerToken));
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			request.addHeader("Authorization", "Bearer " + bearerToken);
		} else {
			request = new HttpGet(url);
		}
		if (returnType != null) {
			request.addHeader("accept", returnType);
		}
		return request;
	}

	InputStreamSupplier getInputStreamSupplier(final String uri) {

		return new InputStreamSupplier() {
			public InputStream get() {
				try {
					HttpGet getMap = createNewGetRequest(new URI(uri), null);
					log.trace("Retrieving: " + uri);
					CloseableHttpResponse response;
					response = client.execute(getMap);
					if (response.getStatusLine().getStatusCode() == 200) {
						log.trace("Retrieved: " + uri);
						return response.getEntity().getContent();
					}
					log.debug("Status: " + response.getStatusLine().getStatusCode());
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					log.error("Unable to retrieve file: " + uri, e);
					e.printStackTrace();
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				return null;
			}
		};
	}

	String getCreatorsString(String[] creators) {
		StringBuffer sBuffer = new StringBuffer();
		if ((creators != null) && (creators.length != 0)) {

			boolean first = true;
			for (int i = 0; i < creators.length; i++) {
				if (!first) {
					sBuffer.append(", ");
				} else {
					first = false;
				}
				sBuffer.append(creators[i]);
			}
		}
		return sBuffer.toString();
	}

	String[] normalizeValues(Object cObject) {

		ArrayList<String> creatorList = new ArrayList<String>();
		if (cObject instanceof String) {
			creatorList.add((String) cObject);
		} else if (cObject instanceof JSONArray) {
			for (int i = 0; i < ((JSONArray) cObject).length(); i++) {
				creatorList.add(((JSONArray) cObject).getString(i));
			}
		} else {
			log.warn("\"Creator\" element is not a string or array of strings");
		}
		return creatorList.toArray(new String[creatorList.size()]);
	}

	JSONArray expandPeople(String[] people) {

		JSONArray peopleArray = new JSONArray();
		if ((people != null) && (people.length != 0)) {
			String c3prServer = props.getProperty("c3pr.address");
			for (int i = 0; i < people.length; i++) {
				log.debug("Expanding: " + people[i]);
				try {
					log.debug(URLEncoder.encode(people[i], "UTF-8"));
				} catch (UnsupportedEncodingException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				HttpGet getPerson;
				try {
					getPerson = new HttpGet(c3prServer + "api/people/"
							+ URLEncoder.encode(people[i], "UTF-8"));

					getPerson.addHeader("accept", "application/json");
					log.trace("getPerson created" + getPerson.getURI());
					CloseableHttpResponse response = client.execute(getPerson);

					if (response.getStatusLine().getStatusCode() == 200) {
						String mapString = EntityUtils.toString(response
								.getEntity());
						log.trace("Expanded: " + mapString);
						peopleArray.put(new JSONObject(mapString));

					} else {
						log.trace("Adding unexpanded person: " + people[i]);
						peopleArray.put(people[i]);
					}
					//Required to avoid some calls hanging in execute() even though we do not reuse the HttpGet object
					getPerson.reset();

				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException ia) {
					ia.printStackTrace();
				} 
			}
			log.debug("Expansion complete");
		}
		return peopleArray;
	}

	boolean echoToConsole = false;

	public void setEchoStatusToConsole(boolean val) {
		echoToConsole = val;
	}

	public void sendStatus(String stage, String message) {

		String c3prServer = props.getProperty("c3pr.address");
		try {
			String statusUrl = c3prServer + "api/researchobjects/"
					+ URLEncoder.encode(RO_ID, "UTF-8") + "/status";

			log.debug("Posting status to: " + statusUrl);
			HttpPost postStatus = new HttpPost(statusUrl);
			if (props.containsKey("JSESSIONID")) {
				// Proxy Mode
				log.debug("Adding: " + props.getProperty("JSESSIONID"));

				BasicClientCookie cookie = new BasicClientCookie("JSESSIONID",
						props.getProperty("JSESSIONID"));
				URL c3pr = new URL(c3prServer);

				cookie.setDomain(c3pr.getHost());
				cookie.setPath("/");
				cookie.setSecure(c3pr.getProtocol().equalsIgnoreCase("https") ? true
						: false);
				cookieStore.addCookie(cookie);
			}
			postStatus.addHeader("accept", MediaType.APPLICATION_JSON);
			String statusString = "{\"reporter\":\""
					+ Repository.getID() + "\", \"stage\":\"" + stage
					+ "\", \"message\":\"" + message + "\"}";
			StringEntity status = new StringEntity(statusString);
			log.trace("Status: " + statusString);
			postStatus.addHeader("content-type",
					MediaType.APPLICATION_JSON);
			postStatus.setEntity(status);

			CloseableHttpResponse response = client.execute(postStatus);

			if (response.getStatusLine().getStatusCode() == 200) {
				log.debug("Status Successfully posted");
			} else {
				log.warn("Failed to post status, response code: "
						+ response.getStatusLine().getStatusCode());
			}
			// Must consume entity to allow connection to be released
			// If this line is not here, the third try to send status will result in a 
			// org.apache.http.conn.ConnectionPoolTimeoutException: Timeout waiting for connection from pool
			// (or a blocked call/hund program if timeouts weren't set
			EntityUtils.consumeQuietly(response.getEntity());
			
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			log.error("Error posting status.", e);

			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			log.error("Error posting status.", e);

			e.printStackTrace();
		} catch (IOException e) {
			log.error("Error posting status.", e);
			e.printStackTrace();
		} catch(Exception e) {
			log.error("Odd Error posting status.", e);
			e.printStackTrace();

		}
		
		if (echoToConsole) {
			System.out
					.println("*********************Status Message******************************");
			System.out.println("Reporter: " + Repository.getID() + ", Stage: "
					+ stage);
			System.out.println("Message Text: " + message);
			System.out
					.println("*****************************************************************");
		}
	}
}