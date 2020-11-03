package robotdispatcher;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.force.api.ApiConfig;
import com.force.api.ApiVersion;
import com.force.api.ResourceException;
import com.force.api.ResourceRepresentation;
import com.force.api.http.HttpRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RobotDispatcher {
	
	static final String REQUEST_URI 		= "/services/apexrest/BLLARobotDev/Robot/";
	static final String USERNAME 			= "jeromerobotdev@bluelattice.ca";
	static final String PASSWORD 			= "&iX^aZI92Gin";
	static final String CLIENT_ID 			= "3MVG9l2zHsylwlpSmz9oGfieY3H9A3VYTK0CytEzAYGSMvrd94DTp_xuU6AXJUYkSnkyBlJs0hOQhIAC5lR5U";
	static final String CLIENT_SECRET 		= "31BCB5725B1C9271B67AEB96B0F50BBD75B2D0CE284737D33A42238A597B2DCF";
	static final Integer REQUEST_TIMEOUT 	= 3000;
	
	//static final Logger logger = LoggerFactory.getLogger(RobotDispatcher.class);
	
	public RobotDispatcher() throws Exception {	
		//logger.info("Starting: Environment: {}", System.getProperty("environment"));
		
		FileReader reader = new FileReader(".\\db.Properties");
		Properties p = new Properties();
		p.load(reader);
		
		ApiConfig myCfg = new ApiConfig()
				.setApiVersionString(ApiVersion.DEFAULT_VERSION.toString())
				.setLoginEndpoint(p.getProperty("loginEndpoint"))
			    .setUsername(p.getProperty("username"))
			    .setPassword(p.getProperty("password"))
				.setClientId(p.getProperty("consumerkey"))
			    .setClientSecret(p.getProperty("consumersecret"))
			    .setRequestTimeout(REQUEST_TIMEOUT);
		RobotForceApi api = new RobotForceApi(myCfg);
		Integer requestInterval = Integer.valueOf(p.getProperty("requestIntervalSeconds"));
		

		//logger.info("Endpoint: {}, Username: {}, Interval: {} secconds", api.getSession().getApiEndpoint(), props.getProperty("Username"), requestInterval);
		
		Input input = new Input();
		input.setComment("Salesforce RESTRobot");

		Boolean keepRunning = true;
		do {
			try {
				//logger.info("Making PUT request to {}", REQUEST_URI);
				ResourceRepresentation dr = api.requestApexRest("POST", REQUEST_URI, input);
				System.out.println("Successful");
				//logger.info("Successful Call of Robot");
			} catch (Exception e) {
				System.out.println("Unsuccessful: "+e.getMessage());
				//logger.error("Exception:", e);
			}
			Thread.sleep(requestInterval * 1000);
		}
		while (keepRunning);

		//logger.info("Finished");
	}
		
	static class Input {
		@JsonProperty(value="comment")
		String comment;
		
		public String getComment() { return comment; }
		public void setComment(String comment) { this.comment = comment; }
	}

	public static void main(String[] args) throws Exception {
		

		new RobotDispatcher();
	}

}
