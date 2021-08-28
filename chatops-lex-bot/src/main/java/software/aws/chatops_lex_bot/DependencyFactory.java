
package software.aws.chatops_lex_bot;

import software.amazon.awssdk.services.lexmodelbuilding.LexModelBuildingClient;

/**
 * The module containing all dependencies required by the {@link App}.
 */
public class DependencyFactory {

    private DependencyFactory() {}

    /**
     * @return an instance of S3Client
     */
    public static LexModelBuildingClient lexBuilderClient() {
        return LexModelBuildingClient.builder()
                       .build();
    }
}
