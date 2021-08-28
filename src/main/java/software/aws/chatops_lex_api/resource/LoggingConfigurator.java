package software.aws.chatops_lex_api.resource;

import java.net.URISyntaxException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.io.IoBuilder;

public class LoggingConfigurator {

	private static final Logger	logger	=	LogManager.getLogger(LoggingConfigurator.class);

	private LoggingConfigurator() {}

	public static final void configure() {
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        try {
			context.setConfigLocation( Thread.currentThread().getContextClassLoader().getResource("software/aws/chatops_lex_api/resource/log4j2.xml").toURI() );
		} catch (URISyntaxException e) {
			System.out.println("Cannot start logging framework: Log4j2. Configuration File Not Found. URISyntaxException:"+e.getMessage());
			e.printStackTrace();
		}
        context.reconfigure();
        context.start();
        context.updateLoggers();

		 System.setErr( IoBuilder.forLogger( LogManager.getRootLogger()).setLevel(Level.ERROR).buildPrintStream() );
		 System.setOut( IoBuilder.forLogger( LogManager.getRootLogger()).setLevel(Level.INFO).buildPrintStream() );
		 System.out.println("Just configured log4j2 and redirected System.out and System.err messages to RootLogger.");
		 logger.info("Log4j2 is set.");
	}
}