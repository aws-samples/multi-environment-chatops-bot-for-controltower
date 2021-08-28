package software.aws.chatops_lex_api.resource;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.lexruntime.LexRuntimeClient;
import software.amazon.awssdk.services.servicecatalog.ServiceCatalogClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.SsmException;

@Path("/account")
public class AccountVendor {

	static{
		LoggingConfigurator.configure();
	}
	private final static Logger logger = LogManager.getLogger(AccountVendor.class);

	static String BOT_NAME = "ChatOps";
	static String BOT_ALIAS = "Prod";
	static String DYNAMO_TABLE = "chatops_account_vending";
	private static String CHATOPS_APPROVAL_URL_PARAM = "_approval_url";

	static LexRuntimeClient lexClient = null;
	static DynamoDbClient ddbClient = null;
	static SnsClient snsClient = null;
	static SsmClient ssmClient = null;
	static ServiceCatalogClient scClient = null;
	static String CALLBACK_URL = "";
	static String CONFIRM_TOPIC_ARN = "";
	static String SLACK_TOKEN = "NO_SLACK_TOKEN_SET";

	public AccountVendor() {

		logger.info("Logger configured");

		String lexBotRegion = "us-east-1";
		if (System.getenv("LEX_BOT_REGION") != null) {
			lexBotRegion = System.getenv("LEX_BOT_REGION");
		}
		logger.info("Will access lexbot in " + lexBotRegion);
		lexClient = LexRuntimeClient.builder().region(Region.of(lexBotRegion)).build();
		ddbClient = DynamoDbClient.builder().build();
		snsClient = SnsClient.builder().build();
		scClient = ServiceCatalogClient.builder().build();
		ssmClient = SsmClient.builder().build();

		String envName = System.getenv("ENVIRONMENT") == null ? "Dev" : System.getenv("ENVIRONMENT");
		String projectName = System.getenv("PROJECT_NAME") == null ? "chatops-lex" : System.getenv("PROJECT_NAME");
		if( projectName == null ) throw new IllegalArgumentException("Project name cannot be null");
		logger.info("Using Env: " + envName);
		if (!CHATOPS_APPROVAL_URL_PARAM.endsWith(envName)) {
			CHATOPS_APPROVAL_URL_PARAM = CHATOPS_APPROVAL_URL_PARAM.concat("_" + envName);
			CHATOPS_APPROVAL_URL_PARAM = projectName.concat(CHATOPS_APPROVAL_URL_PARAM);
		}
		logger.info("CHATOPS_APPROVAL_URL_PARAM: " + CHATOPS_APPROVAL_URL_PARAM);

		try {

			AccountVendor.CALLBACK_URL = AccountVendor.ssmClient.getParameter(GetParameterRequest.builder()
																				.name(CHATOPS_APPROVAL_URL_PARAM)
																				.build()).parameter().value();
			AccountVendor.DYNAMO_TABLE = System.getenv("DYNAMO_TABLE") == null ? DYNAMO_TABLE : System.getenv("DYNAMO_TABLE");
			AccountVendor.CONFIRM_TOPIC_ARN = System.getenv("CONFIRM_TOPIC_ARN");
			AccountVendor.BOT_NAME = System.getenv("BOT_NAME") != null ?  System.getenv("BOT_NAME") : AccountVendor.BOT_NAME;
			AccountVendor.SLACK_TOKEN = System.getenv("SLACK_TOKEN") == null ? "Slack Token not Set" : System.getenv("SLACK_TOKEN");

			logger.info("Using callback URL: " + AccountVendor.CALLBACK_URL);
			logger.info("Using DYNAMO_TABLE: " + AccountVendor.DYNAMO_TABLE);
			logger.info("Using bot name: " + AccountVendor.BOT_NAME);
			logger.info("Using CONFIRM_TOPIC_ARN: " + AccountVendor.CONFIRM_TOPIC_ARN);

		} catch (SsmException se) {
			logger.info("SSM Exception: " + se.getMessage());
			AccountVendor.CALLBACK_URL = "http://test-url";
		} catch (Exception e) {
			logger.info("Exception msg: " + e.getMessage());
			logger.info(
					"Could not retrieve callbackurl and confirm topic from Parameter Store. In case you are compiling or testing locally please ignore");
		}
	}

	@SuppressWarnings("unchecked")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response vend(String payload) {

		logger.info("JSON: " + payload);
		Gson gson = new Gson();
		@SuppressWarnings("rawtypes")
		Map parameters = gson.fromJson(payload, Map.class);
		String slackChannel = "";
		String text = "";

		Boolean IS_SLACK = Boolean.FALSE;

		if (parameters.containsKey("type") && "url_verification".equals(parameters.get("type").toString())) {
			// this is a slack url callback verification
			Map<String, String> elems = new HashMap<>();
			elems.put("challenge", parameters.get("challenge").toString());
			return Response.status(200).entity(elems).build();

		} else if (parameters.containsKey("type") && "event_callback".equals(parameters.get("type").toString())) {

			logger.info("This is a slack message");
			IS_SLACK = Boolean.TRUE;
			if (!"message"
					.equals(gson.fromJson(payload, JsonObject.class).getAsJsonObject("event").get("type").getAsString())) {
				logger.info("I'm not interested in this event type from slack.");
				return Response.status(200).build();
			}

			text = gson.fromJson(payload, JsonObject.class).getAsJsonObject("event").get("text").getAsString();
			slackChannel = gson.fromJson(payload, JsonObject.class).getAsJsonObject("event").get("channel").getAsString();
			try{
				Util.getUserInfoSlack(parameters, text, payload, gson);
			}catch(Exception e){
				logger.info("We could not find the slack username info who is requesting a new account. Message: "+ e.getMessage());
				return Response.status(200).entity("I'm taking a nap now. Please come back in 10 minutes.").build();
			}
		}

		logger.info("UserName: " + parameters.get("UserEmail"));
		if (!parameters.containsKey("UserEmail") || !parameters.containsKey("UserName") || !parameters.containsKey("UserLastname")) {
			throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("JSON inside the body must have these elements: UserEmail, UserName and UserLastname")
					.build());
		}

		Map<String, String> sessionAttributes = new HashMap<>();
		sessionAttributes.putAll(parameters);
		
		String returnMessage = Util.postLex(sessionAttributes, IS_SLACK, slackChannel);

		Map<String, String> elems = new HashMap<>();
		elems.put("initial-params", payload);
		elems.put("response", returnMessage);
		return Response.status(200).entity(elems).build();
	}

	@GET
	@Path("/confirm/{userId}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response confirm(@PathParam("userId") String userId) {

		logger.info("Confirming request from userId: " + userId);
		Map<String, AttributeValue> accountRequest = Util.getItemDynamo(userId);
		if (accountRequest == null) {
			return Response.status(200).entity(
					"Your request for a new account was EXPIRED.\n Please create a new one.\nThank you!\n\n UserId: "
							+ userId)
					.build();
		}
		try {
			Util.vendAccount(accountRequest);
			return Response.status(200)
					.entity("Your request for a new account was APPROVED. Thank you! UserId: " + userId).build();
		} catch (Exception e) {
			return Response.status(200).entity("There was an error approving your request. Message:  " + e.getMessage())
					.build();
		}
	}
}
