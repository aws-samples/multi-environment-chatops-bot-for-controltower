package software.aws.chatops_lex_bot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sunrun.cfnresponse.CfnRequest;
import com.sunrun.cfnresponse.CfnResponse;
import com.sunrun.cfnresponse.Status;

public class AppTest {

    @Test
    public void handleRequest_shouldReturnConstantValue() {
        App function = new App();
        @SuppressWarnings("unchecked")
		CfnResponse<?> result = (CfnResponse<Object>)function.handleRequest(new CfnRequest<Object>(), null);
        assertEquals(result.getStatus() == Status.SUCCESS, result);
    }
}
