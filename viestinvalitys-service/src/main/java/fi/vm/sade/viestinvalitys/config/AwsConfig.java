package fi.vm.sade.viestinvalitys.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * AWS client required for optional feature of downloading messages. S3 client is
 * used to download message attachments that are used to create the EML-format
 * message.
 *
 * <p>Only in use when feature is enabled.
 *
 * <p>The region is always set explicitly, so that client creation does not fail
 * in environments where AWS_REGION is not set (e.g. local runs). Locally,
 * aws.s3.endpoint can point to LocalStack, in which case path-style addressing
 * and static dummy credentials are used, following the AwsUtil approach.
 */
@Configuration
@ConditionalOnProperty(name = "viestinvalitys.features.downloadViesti.enabled", havingValue = "true")
public class AwsConfig {

    @Bean
    public S3Client s3Client(
            @Value("${aws.region:eu-west-1}") String region,
            @Value("${aws.s3.endpoint:}") String endpoint,
            @Value("${aws.s3.path-style-access:false}") boolean pathStyleAccess) {

        var builder = S3Client.builder().region(Region.of(region));

        if (!endpoint.isBlank()) {
            // LocalStack: endpoint override, path-style and dummy credentials.
            builder = builder
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(pathStyleAccess)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("localstack", "localstack")));
        }
        // In other environments than local env, use SDK default credentials chain (ECS task role)

        return builder.build();
    }
}
