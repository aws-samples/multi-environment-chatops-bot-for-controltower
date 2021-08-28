package software.aws.chatops_lex_api.resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lexruntime.model.DialogState;
import software.amazon.awssdk.services.lexruntime.model.GetSessionRequest;
import software.amazon.awssdk.services.lexruntime.model.NotFoundException;
import software.amazon.awssdk.services.lexruntime.model.PostTextRequest;
import software.amazon.awssdk.services.lexruntime.model.PostTextResponse;
import software.amazon.awssdk.services.servicecatalog.model.DescribeProductAsAdminRequest;
import software.amazon.awssdk.services.servicecatalog.model.DescribeProductAsAdminResponse;
import software.amazon.awssdk.services.servicecatalog.model.ListLaunchPathsRequest;
import software.amazon.awssdk.services.servicecatalog.model.ListLaunchPathsResponse;
import software.amazon.awssdk.services.servicecatalog.model.ProvisionProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.ProvisionProductResponse;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningParameter;
import software.amazon.awssdk.services.servicecatalog.model.SearchProductsAsAdminRequest;
import software.amazon.awssdk.services.servicecatalog.model.SearchProductsAsAdminResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class Util {

	private static final Logger logger	=	LogManager.getLogger(Util.class);

    private final static String USER_AGENT = "Mozilla/5.0";
	private final static int MAX_FILE_SIZE = 1024*1024*2;
	
	public static String sendGet(String url, Map<String, String > headers) throws Exception {

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //Request header
        con.setRequestProperty("User-Agent", Util.USER_AGENT);
        for(String key: headers.keySet()) {
        	con.setRequestProperty(key, headers.get(key));
        }
        logger.info("\nSending 'GET' request to URL : " + url);
        int responseCode = con.getResponseCode();
        logger.info("Response Code : " + responseCode);

        StringBuffer response = new StringBuffer();
        try {       
	        try( BufferedReader in = new BufferedReader(
	                new InputStreamReader(con.getInputStream())) ){
	        	
		        String inputLine;
		
		        while ((inputLine = in.readLine()) != null) {
					if(response.length()>=MAX_FILE_SIZE){
						throw new RuntimeException("Response is too big");
					}				
					if( inputLine.length() <= MAX_FILE_SIZE)	{
		            	response.append(inputLine);
					}else{
						throw new IllegalArgumentException("Response is too big");
					}
		        }
	        }
	        con.disconnect();
        }catch(Exception e) {
        	logger.info("Exception in getSend: "+e.getMessage());
        }
        String slackResponse	=	response.toString();
        if( slackResponse.contains("invalid_auth")) {
        	
        	logger.info("Response from Slack: "+slackResponse);
        	logger.info("You are using the following token: "+headers);
        	throw new Exception("Slack token is invalid or you are missing grants. Double check the Token, event subscription or OAuth configuration in your slack application");
        	
        }
        return slackResponse;
    }
	
	private static String post(String url, Map<String,String> headers, String body) throws Exception {
		
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        //Request method
        con.setRequestMethod("POST");        
        //Request header
        con.setRequestProperty("User-Agent", Util.USER_AGENT);
        for(String key: headers.keySet()) {
        	con.setRequestProperty(key, headers.get(key));
        }
        logger.info("\nSending HTTP 'POST' to URL: " + url);
        //send output
        con.setDoOutput(true);
    	try(OutputStream os = con.getOutputStream()) {
    	    byte[] input = body.getBytes("utf-8");
    	    os.write(input, 0, input.length);			
    	}
        int responseCode = con.getResponseCode();        
        logger.info("Response Code : " + responseCode);

        StringBuffer response = new StringBuffer();
        try( BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream())) ){
        	
	        String inputLine;
	
	        while ((inputLine = in.readLine()) != null) {
				if(response.length()>=MAX_FILE_SIZE){
					throw new RuntimeException("Response is too big");
				}
				if( inputLine.length() <= MAX_FILE_SIZE ){
	            	response.append(inputLine);
				}else{
					throw new IllegalArgumentException("Response is too big");
				}
	        }
        }
        con.disconnect();
        return response.toString();
	}
	
	public static String postSlack(String token, String channel, String text) throws Exception {
		
		Map<String, String> httpHeaders	=	new HashMap<>();
		httpHeaders.put("Content-type", "application/json");
		httpHeaders.put("Authorization", "Bearer "+token);
		
		return Util.post("https://slack.com/api/chat.postMessage", httpHeaders, String.format("{\"channel\": \"%s\", \"text\": \"%s\"  }", channel, text));
	}
	
    public static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
        );
    
    public static List<String> getEmails(String input) {
        List<String> emails = new ArrayList<>();
        Matcher matcher = EMAIL_ADDRESS_PATTERN.matcher(input);
        while (matcher.find()) {
            int matchStart = matcher.start(0);
            int matchEnd = matcher.end(0);
            emails.add(input.substring(matchStart, matchEnd));
        }
        return emails;
    }
    
    public static void putItemDynamo(Map<String,String> row) {

        HashMap<String,AttributeValue> item_values = new HashMap<String,AttributeValue>();
        item_values.put("expiration", AttributeValue.builder().n( ""+ ((System.currentTimeMillis() / 1000L)+(3600*48)) ).build()); //48 hours expiration
        
        for(String elemName: row.keySet()) {
        	item_values.put(elemName, AttributeValue.builder().s(row.get(elemName)).build());
        }
              
        PutItemRequest request = PutItemRequest.builder()
  		.tableName(AccountVendor.DYNAMO_TABLE)
  		.item(item_values)
  		.build();
      
        try {
            AccountVendor.ddbClient.putItem(request);
        } catch (ResourceNotFoundException e) {
            logger.info("Table "+AccountVendor.DYNAMO_TABLE+" cannot be found");
            logger.info("Be sure that it exists and that you've typed its name correctly!");
        } catch (DynamoDbException e) {
            logger.info(e.getMessage());
        }

    }
    
    public static Map<String, AttributeValue> getItemDynamo(final String userId){
    	
		if( userId == null || "".equals(userId.trim())){
			return null;
		}
		QueryResponse response = AccountVendor.ddbClient.query( QueryRequest.builder()
												.tableName(AccountVendor.DYNAMO_TABLE)
												.keyConditionExpression("UserId = :pk")
												.filterExpression("expiration > :rightnow")
												.expressionAttributeValues(new HashMap<String, AttributeValue>(){							
													private static final long serialVersionUID = 3386465411339441839L;
													{
														put(":pk", AttributeValue.builder().s(userId).build());
														put(":rightnow", AttributeValue.builder().n( ""+(System.currentTimeMillis()/1000L) ).build());
													}
												})
												.build() );
		if( response.items().size() == 0){
			logger.info("Could not find account request for approval. UserId: "+userId);
			return null;
		}else{
			Map<String, AttributeValue> item	=	response.items().iterator().next();
			return item;
		}    	
    }	
    
    
    public static void vendAccount(final Map<String, AttributeValue> accountRequest) {
    	
    	//add date to provisioned product name
    	LocalDateTime theDay = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
    	String yyyyMMdd = theDay.format(formatter);
    	
    	
    	Collection<ProvisioningParameter> parameters	=	new ArrayList<>();
    	
    	parameters.add(ProvisioningParameter.builder().key("SSOUserEmail").value(accountRequest.get("UserEmail").s()).build());
    	parameters.add(ProvisioningParameter.builder().key("AccountEmail").value(accountRequest.get("RootEmail").s()).build());
    	parameters.add(ProvisioningParameter.builder().key("SSOUserFirstName").value(accountRequest.get("UserName").s()).build());
    	parameters.add(ProvisioningParameter.builder().key("SSOUserLastName").value(accountRequest.get("UserLastname").s()).build());
    	parameters.add(ProvisioningParameter.builder().key("ManagedOrganizationalUnit").value(accountRequest.get("AccountOU").s()).build());
    	parameters.add(ProvisioningParameter.builder().key("AccountName").value(accountRequest.get("AccountType").s()+"Account."+accountRequest.get("UserEmail").s().substring(0,accountRequest.get("UserEmail").s().indexOf("@")).replaceAll("\\s+","").replace("+", "_")+"."+yyyyMMdd).build());    	
    	

    	String productId	=	null;
    	//search product    	
    	Map<String,Collection<String>> filter = new HashMap<>();
    	Collection<String> values = new ArrayList<>();
    	values.add("AWS Control Tower Account Factory");
    	filter.put("FullTextSearch", values);
    	SearchProductsAsAdminResponse r = AccountVendor.scClient.searchProductsAsAdmin(SearchProductsAsAdminRequest.builder().filtersWithStrings(filter).build());
    	if( r.productViewDetails().size() > 0 ) {
    		
    		productId	=	r.productViewDetails().get(0).productViewSummary().productId();
    		logger.info("Found AWS Control Tower Account Factory with productId: "+productId);
    	}else {
    		logger.info("Could not find product AWS Control Tower Account Factory in the Service Catalog of the AWS Account");
    		logger.info("NOT vending new account. Please fix this and create a new request.");
    		return;
    	}
    	//describe product
    	String artifactId	=	null;
    	DescribeProductAsAdminResponse prodResponse = AccountVendor.scClient.describeProductAsAdmin(DescribeProductAsAdminRequest.builder().id(productId).build());
    	if( prodResponse.provisioningArtifactSummaries().size() > 0) {
    		
    		//always get the latest because it is the most recent version
    		artifactId	=	prodResponse.provisioningArtifactSummaries().get(prodResponse.provisioningArtifactSummaries().size()-1).id();
    		logger.info("AWS Control Tower Account Factory Artifact Id: "+artifactId);
    	}else {
    		logger.info("Could not find artifact id of product id="+productId+" inside Service Catalog of the account");
    	}
    	//get Launch Path
    	String launchPathId	=	null;
    	ListLaunchPathsResponse pathResponse = AccountVendor.scClient.listLaunchPaths(ListLaunchPathsRequest.builder().productId(productId).build());
    	if( pathResponse.launchPathSummaries().size() > 0 ) {
    		
    		launchPathId	=	pathResponse.launchPathSummaries().get(0).id();
    		logger.info("AWS Control Tower Account Factory Launch Path Id: "+launchPathId);
    	}else {
    		logger.info("Could not find launch path for product AWS Control Tower Account Factory ProductId: "+productId);
    	}
    	
    	//provision product using the product id
    	ProvisionProductResponse response =  AccountVendor.scClient.provisionProduct(ProvisionProductRequest.builder()
//    																		.productName("AWS Control Tower Account Factory")
    																		.productId(productId)
    																		.pathId(launchPathId)
    																		.provisionToken(accountRequest.get("UserId").s())
    																		.provisionedProductName(accountRequest.get("AccountType").s()+".Account."+accountRequest.get("UserEmail").s().substring(0,accountRequest.get("UserEmail").s().indexOf("@")).replace("+", "_")+"."+yyyyMMdd)
    																		.provisioningArtifactId(artifactId)
    																		.provisioningParameters(parameters)
    																		.build());
    	    	
    	logger.info("New account vended for user: "+accountRequest.get("UserEmail").s()+" and requestId: "+response.responseMetadata().requestId());
    }

	public static void getUserInfoSlack(final Map<String,String> parameters, 
										String userInput, 
										final String payload, 
										final Gson gson) throws Exception{

		String url = "https://slack.com/api/users.info";
		String userid = gson.fromJson(payload, JsonObject.class).getAsJsonObject("event").get("user").getAsString();																													
		if (userInput != null && userInput.contains("mailto")) {
			userInput = Util.getEmails(userInput).get(0);
			logger.info("Successfully parsed email from Slack:" + userInput);
		}

		String userDetails = Util.sendGet(url + "?user=" + userid,
				Collections.singletonMap("Authorization", "Bearer " + AccountVendor.SLACK_TOKEN));
		String realName = gson.fromJson(userDetails, JsonObject.class).getAsJsonObject("user")
				.getAsJsonObject("profile").get("real_name").getAsString();
		String email = gson.fromJson(userDetails, JsonObject.class).getAsJsonObject("user")
				.getAsJsonObject("profile").get("email").getAsString();

		parameters.clear();
		parameters.put("UserEmail", email);
		parameters.put("UserName", realName.trim().split(" ").length > 1 ? realName.trim().split(" ")[0] : realName.trim());
		parameters.put("UserLastname", realName.trim().split(" ").length > 1 ? realName.trim().split(" ")[1] : realName.trim());
		parameters.put("UserInput", userInput);
	}

	public static String postLex(final Map<String,String> sessionAttributes, 
									final Boolean IS_SLACK, 
									final String slackChannel){

		PostTextResponse response = null;
		try {
			AccountVendor.lexClient
					.getSession(GetSessionRequest
							.builder().botAlias(AccountVendor.BOT_ALIAS).botName(AccountVendor.BOT_NAME).userId(sessionAttributes
									.get("UserEmail").substring(0, sessionAttributes.get("UserEmail").indexOf("@")))
							.build());

			logger.info("Not setting session attributes");
			response = AccountVendor.lexClient.postText(PostTextRequest.builder().botName(AccountVendor.BOT_NAME).botAlias(AccountVendor.BOT_ALIAS)
					.userId(sessionAttributes.get("UserEmail").substring(0,
							sessionAttributes.get("UserEmail").indexOf("@")))
					.inputText(sessionAttributes.get("UserInput").replaceAll("/[^\u0000-\u007F]+/g", ""))
					// .sessionAttributes(sessionAttributes)
					.build());

		} catch (NotFoundException ne) {
			logger.info("SETTING session attributes");
			response = AccountVendor.lexClient.postText(PostTextRequest.builder().botName(AccountVendor.BOT_NAME).botAlias(AccountVendor.BOT_ALIAS)
					.userId(sessionAttributes.get("UserEmail").substring(0,
							sessionAttributes.get("UserEmail").indexOf("@")))
					.inputText(sessionAttributes.get("UserInput").replaceAll("/[^\u0000-\u007F]+/g", ""))
					.sessionAttributes(sessionAttributes).build());
		}

		if (response.hasSessionAttributes()) {
			logger.info("Current session attributes: " + response.sessionAttributes());
		}
		return Util.processResponse(response, IS_SLACK, slackChannel);
	}

	public static String processResponse(final PostTextResponse response, final Boolean IS_SLACK, final String slackChannel){

		String returnMessage	=	response.message();
		if (response.dialogState() == DialogState.FULFILLED
				|| response.dialogState() == DialogState.READY_FOR_FULFILLMENT) {

			logger.info("END OF CONVERSATION");
			returnMessage = "New account request created";
			Map<String, String> accountRequest = new HashMap<>();
			accountRequest.putAll(response.sessionAttributes());
			accountRequest.putAll(response.slots());
			accountRequest.put("UserId", UUID.randomUUID().toString());
			accountRequest.remove("UserInput");
			Util.putItemDynamo(accountRequest);

			logger.info("Sending notification to this topic: " + AccountVendor.CONFIRM_TOPIC_ARN);
			AccountVendor.snsClient.publish(PublishRequest.builder().subject("New AWS account request for your approval.")
					.message(MessageFormat.format(
							"{0} {1} has requested a new aws account with the following features: \nAccountType: {2}\nAccountOU: {3}\nRootEmail: {4}\n\n Please approve using the link below, otherwise this request will auto expire in 48 hours\n\n Approve here: {5}",
							accountRequest.get("UserName"), accountRequest.get("UserLastname"),
							accountRequest.get("AccountType"), accountRequest.get("AccountOU"),
							accountRequest.get("RootEmail"),
							AccountVendor.CALLBACK_URL + "/" + accountRequest.get("UserId")))
					.topicArn(AccountVendor.CONFIRM_TOPIC_ARN).build());
		} else {

			logger.info("Slots: " + response.slots());
			logger.info("Slots to elicit: " + response.slotToElicit());
			logger.info("Current dialog state: " + response.dialogState().toString());
		}

		if (IS_SLACK) {
			try {
				logger.info("Posting message in Slack");
				Util.postSlack(AccountVendor.SLACK_TOKEN, slackChannel, returnMessage);
			} catch (Exception e) {
				logger.info("ERROR: could not post message in slack. Messaage: " + e.getMessage());
			}
		}		
		return returnMessage;
	}
}
