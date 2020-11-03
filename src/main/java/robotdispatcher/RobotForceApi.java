package robotdispatcher;

import com.force.api.ApiConfig;
import com.force.api.ApiException;
import com.force.api.ApiSession;
import com.force.api.ApiTokenException;
import com.force.api.Auth;
import com.force.api.ResourceException;
import com.force.api.ResourceRepresentation;
import com.force.api.http.Http;
import com.force.api.http.HttpRequest;
import com.force.api.http.HttpResponse;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * main class for making API calls.
 *
 * This class is cheap to instantiate and throw away. It holds a user's session
 * as state and thus should never be reused across multiple user sessions,
 * unless that's explicitly what you want to do.
 *
 * For web apps, you should instantiate this class on every request and feed it
 * the session information as obtained from a session cookie or similar. An
 * exception to this rule is if you make all API calls as a single API user.
 * Then you can keep a static reference to this class.
 *
 * @author jjoergensen
 *
 */
public class RobotForceApi {

	private final ObjectMapper jsonMapper;

	private static final Logger logger = LoggerFactory.getLogger(RobotForceApi.class);

	final ApiConfig config;
	ApiSession session;
	private boolean autoRenew = false;


	public RobotForceApi(ApiConfig apiConfig) {
		config = apiConfig;
		jsonMapper = config.getObjectMapper();
		session = Auth.authenticate(apiConfig);
		autoRenew  = true;

	}
	
	public ResourceRepresentation requestApexRest(String method, String path, Object input) {
		try {
			return new ResourceRepresentation(apiRequest(new HttpRequest()
					.url(uriApexRest() + path)
					.method(method)
					.header("Accept", "application/json")
					.header("Content-Type", "application/json")
					.content(jsonMapper.writeValueAsBytes(input))),
					jsonMapper);
		} catch (JsonGenerationException e) {
			throw new ResourceException(e);
		} catch (JsonMappingException e) {
			throw new ResourceException(e);
		} catch (IOException e) {
			throw new ResourceException(e);
		}
	}

	private final String uriBase() {
		return(session.getApiEndpoint()+"/services/data/"+config.getApiVersionString());
	}
	
	private final String uriApexRest() {
		return(session.getApiEndpoint()+"/services/apexrest");
	}
	
	private final HttpResponse apiRequest(HttpRequest req) {
		req.setAuthorization("Bearer "+session.getAccessToken());
		req.setRequestTimeout(this.config.getRequestTimeout());
		HttpResponse res = Http.send(req);
		if(res.getResponseCode()==401) {
			// Perform one attempt to auto renew session if possible
			if (autoRenew) {
				logger.debug("Session expired. Refreshing session...");
				if(session.getRefreshToken()!=null) {
					session = Auth.refreshOauthTokenFlow(config, session.getRefreshToken());
				} else {
					session = Auth.authenticate(config);
				}
				if(config.getSessionRefreshListener()!=null) {
					config.getSessionRefreshListener().sessionRefreshed(session);
				}
				req.setAuthorization("Bearer "+session.getAccessToken());
				res = Http.send(req);
			}
		}
		if(res.getResponseCode()>299) {
			if(res.getResponseCode()==401) {
				throw new ApiTokenException(res.getString());
			} else {
				throw new ApiException(res.getResponseCode(), res.getString());
			}
		} else if(req.getExpectedCode()!=-1 && res.getResponseCode()!=req.getExpectedCode()) {
			throw new RuntimeException("Unexpected response from Force API. Got response code "+res.getResponseCode()+
					". Was expecting "+req.getExpectedCode());
		} else {
			return res;
		}
	}
	
	/**
	 * Normalizes the JSON response in case it contains responses from
	 * relationship queries. For e.g.
	 *
	 * <code>
	 * Query:
	 *   select Id,Name,(select Id,Email,FirstName from Contacts) from Account
	 *   
	 * Json Response Returned:
	 * 
	 * {
	 *	  "totalSize" : 1,
	 *	  "done" : true,
	 *	  "records" : [ {
	 *	    "attributes" : {
	 *	      "type" : "Account",
	 *	      "url" : "/services/data/v24.0/sobjects/Account/0017000000TcinJAAR"
	 *	    },
	 *	    "Id" : "0017000000TcinJAAR",
	 *	    "Name" : "test_acc_04_01",
	 *	    "Contacts" : {
	 *	      "totalSize" : 1,
	 *	      "done" : true,
	 *	      "records" : [ {
	 *	        "attributes" : {
	 *	          "type" : "Contact",
	 *	          "url" : "/services/data/v24.0/sobjects/Contact/0037000000zcgHwAAI"
	 *	        },
	 *	        "Id" : "0037000000zcgHwAAI",
	 *	        "Email" : "contact@email.com",
	 *	        "FirstName" : "John"
	 *	      } ]
	 *	    }
	 *	  } ]
	 *	}
	 * </code>
	 * 
	 * Will get normalized to:
	 * 
	 * <code>
	 * {
	 *	  "totalSize" : 1,
	 *	  "done" : true,
	 *	  "records" : [ {
	 *	    "attributes" : {
	 *	      "type" : "Account",
	 *	      "url" : "/services/data/v24.0/sobjects/Account/accountId"
	 *	    },
	 *	    "Id" : "accountId",
	 *	    "Name" : "test_acc_04_01",
	 *	    "Contacts" : [ {
	 *	        "attributes" : {
	 *	          "type" : "Contact",
	 *	          "url" : "/services/data/v24.0/sobjects/Contact/contactId"
	 *	        },
	 *	        "Id" : "contactId",
	 *	        "Email" : "contact@email.com",
	 *	        "FirstName" : "John"
	 *	    } ]
	 *	  } ]
	 *	} 
	 * </code
	 * 
	 * This allows Jackson to deserialize the response into it's corresponding Object representation
	 * 
	 * @param node 
	 * @return
	 */
	private final JsonNode normalizeCompositeResponse(JsonNode node){
		Iterator<Entry<String, JsonNode>> elements = node.fields();
		ObjectNode newNode = JsonNodeFactory.instance.objectNode();
		Entry<String, JsonNode> currNode;
		while(elements.hasNext()){
			currNode = elements.next();

			newNode.set(currNode.getKey(),
						(		currNode.getValue().isObject() && 
								currNode.getValue().get("records")!=null
						)?
								currNode.getValue().get("records"):
									currNode.getValue()
					);
		}
		return newNode;
		
	}
}
