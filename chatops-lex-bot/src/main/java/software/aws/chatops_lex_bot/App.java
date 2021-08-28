package software.aws.chatops_lex_bot;

import java.security.SecureRandom;
import java.util.Arrays;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sunrun.cfnresponse.CfnRequest;
import com.sunrun.cfnresponse.CfnResponseSender;
import com.sunrun.cfnresponse.Status;

import software.amazon.awssdk.services.lexmodelbuilding.LexModelBuildingClient;
import software.amazon.awssdk.services.lexmodelbuilding.model.ContentType;
import software.amazon.awssdk.services.lexmodelbuilding.model.EnumerationValue;
import software.amazon.awssdk.services.lexmodelbuilding.model.FulfillmentActivityType;
import software.amazon.awssdk.services.lexmodelbuilding.model.Intent;
import software.amazon.awssdk.services.lexmodelbuilding.model.Locale;
import software.amazon.awssdk.services.lexmodelbuilding.model.Message;
import software.amazon.awssdk.services.lexmodelbuilding.model.PreconditionFailedException;
import software.amazon.awssdk.services.lexmodelbuilding.model.ProcessBehavior;
import software.amazon.awssdk.services.lexmodelbuilding.model.Prompt;
import software.amazon.awssdk.services.lexmodelbuilding.model.PutBotResponse;
import software.amazon.awssdk.services.lexmodelbuilding.model.PutIntentResponse;
import software.amazon.awssdk.services.lexmodelbuilding.model.Slot;
import software.amazon.awssdk.services.lexmodelbuilding.model.SlotConstraint;
public class App implements RequestHandler<CfnRequest<Object>, Object> {
    

    private final static String CHATOPS_BOT_NAME = "ChatOps";
    private final static String CHATOPS_INTENT_ACCT_VENDING_NAME = "AWSAccountVending"; 
    
    private final LexModelBuildingClient lexBuilderClient;
    private LambdaLogger    logger; 

    public App() {
        lexBuilderClient = DependencyFactory.lexBuilderClient();
    }

    public Object handleRequest(final CfnRequest<Object> input, final Context context) {

        logger  =   context.getLogger();
        logger.log("Starting to "+input.getRequestType()+" the ChatOps Lex Bot");
        logger.log("Adding Slot Type:: AccountTypeValues");
        
        try {
	        if( input.getRequestType().toLowerCase().indexOf("create") != -1 || input.getRequestType().toLowerCase().indexOf("update") != -1 ) { 
	        	
	        	createLexBot();	        	        
	        }else if( input.getRequestType().toLowerCase().indexOf("delete") != -1) {
	
        		deleteLexBot();
	        }
	        CfnResponseSender sender = new CfnResponseSender();
	        // generate your physical id
	        sender.send(input, Status.SUCCESS, context, "Deployed ChatOps Lex bot", null, ""+SecureRandom.getInstanceStrong().nextDouble()*10000);
	        
        }catch(Exception e) {

	        CfnResponseSender sender = new CfnResponseSender();
            try{
	            // generate your physical id
	            sender.send(input, Status.FAILED, context, "Failed to delete the ChatOps Lex Bot. You need to manually delete the service configuration. Exception: "+e.getMessage(), null, ""+SecureRandom.getInstanceStrong().nextDouble()*10000);
            }catch(java.security.NoSuchAlgorithmException ee){
                logger.log(e.getMessage());
                sender.send(input, Status.FAILED, context, "Failed to delete the ChatOps Lex Bot. You need to manually delete the service configuration. Exception: "+e.getMessage(), null, "98765" );
            }
        }
        return input;
    }
    
    
    private void createLexBot() {
    	
        try {
            //SlotType AccountTypeValues
            lexBuilderClient.putSlotType(builder-> builder.name("AccountTypeValues")
                                                            .enumerationValues(EnumerationValue.builder().value("Production").build(), 
                                                                                EnumerationValue.builder().value("Sandbox").build())
                                                            .build());
        }catch(PreconditionFailedException e) {
            logger.log("It seems the AccountTypeValues already exist.");
        }
        
        logger.log("Adding Slot Type:: AccountOUValues");
        try {
            //SlotType AccountOUValues
            lexBuilderClient.putSlotType(builder-> builder.name("AccountOUValues")
                    .enumerationValues(EnumerationValue.builder().value("Experiment").build(), 
                                        EnumerationValue.builder().value("Demos").build())
                    .build());
        }catch(PreconditionFailedException e) {
            logger.log("It seems the AccountOUValues already exist.");
        }
        
        
        logger.log("Adding Intent:: "+App.CHATOPS_INTENT_ACCT_VENDING_NAME);
        //Intent
        PutIntentResponse intentResp = lexBuilderClient.putIntent(builder-> builder
                                                .description("Intent responsible for the account vending conversation")
                                                .name(App.CHATOPS_INTENT_ACCT_VENDING_NAME)
                                                .fulfillmentActivity(act->act.type(FulfillmentActivityType.RETURN_INTENT).build()) //check here if builds but fails at the end
                                                .sampleUtterances("Hi I want a new account please", "Hi I would like to get an account", "I need a new account on AWS", "I want to create a new account", "I would like to create a new account", "I would like to get a new AWS account") // "I would like to create a ·{AccountType}· account"
                                                .confirmationPrompt(Prompt.builder().maxAttempts(4).messages(Arrays.asList(new Message[] {Message.builder().content("[UserName], are you sure you want to create a {AccountType} account associated to the OU {AccountOU} using root accout email {RootEmail}?").contentType(ContentType.PLAIN_TEXT).build()})).build())
                                                .rejectionStatement(b-> b.messages(Message.builder().contentType(ContentType.PLAIN_TEXT).content("Okay [UserName]. Your order won't be placed.").build()).build())
                                                .slots(Slot.builder().priority(1).slotConstraint(SlotConstraint.REQUIRED).slotType("AccountTypeValues").slotTypeVersion("$LATEST").name("AccountType").valueElicitationPrompt(Prompt.builder().maxAttempts(4).messages(Message.builder().contentType(ContentType.PLAIN_TEXT).content("Hi [UserName], what account type do you want? Production or Sandbox?").build()).build()).build(),
                                                        Slot.builder().priority(2).slotConstraint(SlotConstraint.REQUIRED).slotType("AMAZON.EmailAddress").name("RootEmail").valueElicitationPrompt(Prompt.builder().maxAttempts(4).messages(Message.builder().contentType(ContentType.PLAIN_TEXT).content("[UserName], what e-mail should I use for the root account? Hint: use a restricted access mailbox").build()).build()).build(),
                                                        Slot.builder().priority(3).slotConstraint(SlotConstraint.REQUIRED).slotType("AccountOUValues").slotTypeVersion("$LATEST").name("AccountOU").valueElicitationPrompt(Prompt.builder().maxAttempts(4).messages(Message.builder().contentType(ContentType.PLAIN_TEXT).content("[UserName], what is the OU this account should belong to? Hint: choose Experiment or Demos").build()).build()).build()
                                                        )
                                                .build());
        
        logger.log("Adding LexBot:: "+CHATOPS_BOT_NAME);
        //Bot name 
        PutBotResponse resp =   lexBuilderClient.putBot(builder-> builder.name(App.CHATOPS_BOT_NAME)    
                                            .childDirected(Boolean.FALSE)
                                            .clarificationPrompt(Prompt.builder().maxAttempts(4).messages(Message.builder().content("I don't understand. What would you like to do?").contentType(ContentType.PLAIN_TEXT).build()).build())
                                            .description("Control Tower Account Vending Intent")
                                            .detectSentiment(Boolean.FALSE)
                                            .enableModelImprovements(Boolean.TRUE)
                                            .idleSessionTTLInSeconds(600)
                                            .abortStatement(b-> b.messages(Message.builder().contentType(ContentType.PLAIN_TEXT).content("[UserName] I've given up.").build()).build())
                                            .locale(Locale.EN_US)
                                            .processBehavior(ProcessBehavior.BUILD)
                                            .intents(Intent.builder().intentName(App.CHATOPS_INTENT_ACCT_VENDING_NAME).intentVersion(intentResp.version()).build())
                                            .build());      

        logger.log("Adding LexBot Alias:: "+App.CHATOPS_INTENT_ACCT_VENDING_NAME);
        //create bot alias
        lexBuilderClient.putBotAlias(builder-> builder.botName(CHATOPS_BOT_NAME).botVersion(resp.version()).description("Production alias of the chatops bot").name("Prod").build());
        
    }
    
    public void deleteLexBot() throws InterruptedException {
    	
    	//delete alias
    	lexBuilderClient.deleteBotAlias(request->request.botName(App.CHATOPS_BOT_NAME).name("Prod").build());
    	Thread.sleep(5000);
    	
    	//delete bot
    	lexBuilderClient.deleteBot(request->request.name(App.CHATOPS_BOT_NAME).build());
    	Thread.sleep(5000);
    	
    	//delete intent
    	lexBuilderClient.deleteIntent(request->request.name(App.CHATOPS_INTENT_ACCT_VENDING_NAME).build());
    	Thread.sleep(5000);
    	
    	//delete slot types
    	lexBuilderClient.deleteSlotType(request->request.name("AccountOUValues").build());
    	Thread.sleep(5000);
    	lexBuilderClient.deleteSlotType(request->request.name("AccountTypeValues").build());
    }

}