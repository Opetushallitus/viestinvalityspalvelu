package fi.oph.viestinvalitus

import com.github.dockerjava.api.model.{ExposedPort, PortBinding}
import com.github.dockerjava.api.model.Ports.Binding
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpPut, HttpUriRequest}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{BeforeAll, BeforeEach, Test, TestInstance}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.{CreateFunctionRequest, CreateFunctionResponse, CreateFunctionUrlConfigRequest, CreateFunctionUrlConfigResponse, Environment, FunctionCode, GetFunctionConfigurationRequest, GetFunctionRequest}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, CreateQueueResponse, DeleteMessageRequest, ReceiveMessageRequest, ReceiveMessageResponse}

import java.io.{File, FileInputStream}
import java.net.URI
import java.nio.charset.StandardCharsets

@Test
@TestInstance(Lifecycle.PER_CLASS)
class AppTest {

    @Container var localstack: LocalStackContainer = new LocalStackContainer(new DockerImageName("localstack/localstack:2.2.0"))
      .withEnv("LAMBDA_DOCKER_FLAGS", "-p 127.0.0.1:5050:5050")
      .withEnv("LAMBDA_DOCKER_NETWORK", "bridge")
      .withEnv("LAMBDA_KEEPALIVE_MS", "0")
      .withExposedPorts(4566)

    @BeforeAll def setUp(): Unit = {
      localstack.start()
    }

    @Test def testSimplePutAndGet(): Unit = {
        val sqsClient: SqsClient = SqsClient
          .builder()
          .endpointOverride(localstack.getEndpoint())
          .credentialsProvider(
            StaticCredentialsProvider.create(
              AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
            )
          )
          .region(Region.of(localstack.getRegion()))
          .build()

        val createQueueResponse: CreateQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
          .queueName("viestinvalituspalvelu-vastaanotto-high")
          .build())

        val lambdaClient: LambdaClient = LambdaClient
          .builder()
          .endpointOverride(localstack.getEndpoint())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
              )
          )
          .region(Region.of(localstack.getRegion()))
          .build();

      val queueUrl: String = new URIBuilder(createQueueResponse.queueUrl()).setHost("localhost.localstack.cloud").setPort(4566).build().toString()
      val endpoint: String = new URIBuilder(localstack.getEndpoint).setHost("localhost.localstack.cloud").setPort(4566).build().toString()

        val createFunctionResponse: CreateFunctionResponse = lambdaClient.createFunction(CreateFunctionRequest.builder()
          .functionName("vastaanotto")
          .code(FunctionCode.builder()
            .zipFile(SdkBytes.fromInputStream(new FileInputStream(new File("target/vastaanotto-0.1-SNAPSHOT.jar"))))
            .build())
          .handler("fi.oph.viestinvalitus.LambdaHandler")
          .runtime("java17")
          .timeout(1500)
          .role("arn:aws:iam::000000000000:role/lambda-role")
          .environment(Environment.builder()
            .variables(java.util.Map.of(
              "localstack.endpoint", endpoint,
              "localstack.region", localstack.getRegion,
              "localstack.accessKey", localstack.getAccessKey,
              "localstack.secretKey", localstack.getSecretKey,
              "localstack.queueUrl", queueUrl,
              "_JAVA_OPTIONS", "-Xshare:off -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:5050"))
            .build())
          .build())

        lambdaClient.waiter().waitUntilFunctionActive(GetFunctionConfigurationRequest.builder().functionName("vastaanotto").build())

        val creationFunctionUrlConfigResponse: CreateFunctionUrlConfigResponse = lambdaClient.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
          .functionName("vastaanotto")
          .build())

      val url: String = new URIBuilder(creationFunctionUrlConfigResponse.functionUrl()).setPort(localstack.getEndpoint.getPort).build().toString()
      val request: HttpPut  = new HttpPut(url + "v2/resource/viesti" );
      request.setEntity(new ByteArrayEntity("{\"heading\":\"testh\",\"content\":\"test\"}".getBytes(StandardCharsets.UTF_8)))
      request.setHeader("Accept", "application/json")
      request.setHeader("Content-Type", "application/json")
      val response: HttpResponse = HttpClientBuilder.create().build().execute(request)

      System.out.println(IOUtils.toString(response.getEntity.getContent))

      val receiveMessageResponse: ReceiveMessageResponse = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(createQueueResponse.queueUrl())
        .build())

      receiveMessageResponse.messages().forEach(message => {
          assertEquals("test message", message.body())

          sqsClient.deleteMessage(DeleteMessageRequest.builder()
            .queueUrl(createQueueResponse.queueUrl())
            .receiptHandle(message.receiptHandle())
            .build())
      })

    }
}


