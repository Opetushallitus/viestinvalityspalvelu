package fi.vm.sade.viestinvalitys.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class LahetysAwsConfig {

  @Bean
  public SesClient sesClient(@Value("${aws.region:eu-west-1}") String region) {
    return SesClient.builder().region(Region.of(region)).build();
  }

  @Bean
  public CloudWatchClient cloudWatchClient(@Value("${aws.region:eu-west-1}") String region) {
    return CloudWatchClient.builder().region(Region.of(region)).build();
  }

  @Bean
  @ConditionalOnMissingBean(S3Client.class)
  public S3Client s3Client(
          @Value("${aws.region:eu-west-1}") String region,
          @Value("${aws.s3.endpoint:}") String endpoint) {
    var builder = S3Client.builder().region(Region.of(region));
    if (!endpoint.isBlank()) {
      builder =
              builder
                      .endpointOverride(URI.create(endpoint))
                      .forcePathStyle(true)
                      .credentialsProvider(
                              StaticCredentialsProvider.create(
                                      AwsBasicCredentials.create("localstack", "localstack")));
    }
    return builder.build();
  }
}
