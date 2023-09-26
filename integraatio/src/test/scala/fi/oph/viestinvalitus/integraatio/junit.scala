package fi.oph.viestinvalitus.integraatio

import com.github.dockerjava.api.model.Ports.Binding
import com.github.dockerjava.api.model.{ExposedPort, PortBinding}
import fi.oph.viestinvalitus.integraatio.OphPostgresContainer
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpPut, HttpUriRequest}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{BeforeAll, BeforeEach, Test, TestInstance}
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.{GenericContainer, Network, PostgreSQLContainer}
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.lambda.{LambdaAsyncClient, LambdaClient}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*
import slick.jdbc.PostgresProfile.api.*

import java.io.{File, FileInputStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.ExecutionContext.Implicits.global

@Test
@TestInstance(Lifecycle.PER_CLASS)
class AppTest {

  val HIGH_PRIORITY_QUEUE_NAME: String = "viestinvalituspalvelu-vastaanotto-high"

  val network = Network.newNetwork();

  @Container var localstack: LocalStackContainer = new LocalStackContainer(new DockerImageName("localstack/localstack:2.2.0"))
    .withEnv("LAMBDA_DOCKER_NETWORK", network.getId)
    .withEnv("LAMBDA_KEEPALIVE_MS", "0")
    .withNetwork(network)
    .withExposedPorts(4566)

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitus")
    .withUsername("viestinvalitus")
    .withPassword("viestinvalitus")
    .withNetwork(network)
    .withNetworkAliases("postgres")

  @BeforeAll def setUp(): Unit = {
    localstack.start()
    postgres.start()
  }

  val driver = "org.postgresql.Driver"

  private def getSqsClient(localstack: LocalStackContainer): SqsClient = {
    SqsClient
      .builder()
      .endpointOverride(localstack.getEndpoint())
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
        )
      )
      .region(Region.of(localstack.getRegion()))
      .build()
  }

  private def getAsyncLambdaClient(localstack: LocalStackContainer): LambdaAsyncClient = {
    LambdaAsyncClient
      .builder()
      .endpointOverride(localstack.getEndpoint())
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
        )
      )
      .region(Region.of(localstack.getRegion()))
      .build()
  }

  private def getLambdaClient(localstack: LocalStackContainer): LambdaClient = {
    LambdaClient
      .builder()
      .endpointOverride(localstack.getEndpoint())
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
        )
      )
      .region(Region.of(localstack.getRegion()))
      .build();
  }

  private def createFunction[A](
    localstack: LocalStackContainer,
    name: String,
    handler: Class[A],
    file: File,
    env: java.util.Map[String, String],
    debugPort: Option[Int]
  ): CompletableFuture[CreateFunctionResponse] = {
    val lambdaAsyncClient = this.getAsyncLambdaClient(localstack)
    val endpoint: String = new URIBuilder(localstack.getEndpoint).setHost("localhost.localstack.cloud").setPort(4566).build().toString()

    val variables: util.Map[String, String] = new util.HashMap()
    variables.put("localstack.endpoint", endpoint)
    variables.put("localstack.region", localstack.getRegion)
    variables.put("localstack.accessKey", localstack.getAccessKey)
    variables.put("localstack.secretKey", localstack.getSecretKey)
    if(debugPort.isDefined) {
      variables.put("_JAVA_OPTIONS", "-Xshare:off -agentlib:jdwp=transport=dt_socket,server=n,address=host.docker.internal:" + debugPort.get + ",suspend=y,onuncaught=n")
    }
    variables.putAll(env)

    val createRequest = CreateFunctionRequest.builder()
      .functionName(name)
      .code(FunctionCode.builder()
        .zipFile(SdkBytes.fromInputStream(new FileInputStream(file)))
        .build())
      .handler(handler.getCanonicalName)
      .runtime("java17")
      .timeout(1500)
      .role("arn:aws:iam::000000000000:role/lambda-role")
      .environment(Environment.builder()
        .variables(variables)
        .build())
      .build()

    val createResponse = lambdaAsyncClient.createFunction(createRequest)

    createResponse.thenApply(cr => {
      lambdaAsyncClient.waiter().waitUntilFunctionActive(GetFunctionConfigurationRequest.builder()
        .functionName(name)
        .build).get
      cr
    })
  }

  def createQueue() = {
    val sqsClient: SqsClient = this.getSqsClient(localstack)
    val createQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
      .queueName(HIGH_PRIORITY_QUEUE_NAME)
      .build())

    new URIBuilder(createQueueResponse.queueUrl()).setHost("localhost.localstack.cloud").setPort(4566).build().toString()
  }

  def createVastaanOtto(queueUrl: String) = {
    val createFunctionResponse = this.createFunction(
      localstack,
      "vastaanotto",
      classOf[fi.oph.viestinvalitus.vastaanotto.LambdaHandler],
      new File("../vastaanotto/target/vastaanotto-0.1-SNAPSHOT.jar"),
      java.util.Map.of("localstack.queueUrl", queueUrl), Option.empty) //,
    //Option.apply(5050))

    val lambdaClient = this.getLambdaClient(localstack)
    createFunctionResponse.thenApply(r =>
      lambdaClient.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
        .functionName(r.functionName())
        .build()))
  }

  def createTallennus(queueUrl: String) = {
    val createTallennusFunctionResponse = this.createFunction(
      localstack,
      "tallennus",
      classOf[fi.oph.viestinvalitus.tallennus.LambdaHandler],
      new File("../tallennus/target/tallennus-0.1-SNAPSHOT.jar"),
      java.util.Map.of(
        "postgres.host", "postgres",
        "postgres.port", 5432.toString,
        "postgres.username", postgres.getUsername(),
        "postgres.password", postgres.getPassword(),
        "localstack.queueUrl", queueUrl),
      Option.apply(5051))

    val lambdaClient = this.getLambdaClient(localstack)
    createTallennusFunctionResponse.thenApply(r =>
      lambdaClient.createEventSourceMapping(CreateEventSourceMappingRequest.builder()
        .functionName(r.functionName)
        .eventSourceArn("arn:aws:sqs:" + localstack.getRegion + ":000000000000:" + HIGH_PRIORITY_QUEUE_NAME)
        .build()))
  }

  def createDb(host: String, port: Int, dbName: String, user: String, pwd: String) = {
    val onlyHostNoDbUrl = s"jdbc:postgresql://$host:$port/"
    val ds: Database = Database.forURL(onlyHostNoDbUrl, user = user, password = pwd, driver = driver)
    Await.result(ds.run(sqlu"CREATE DATABASE #$dbName"), Duration(5, TimeUnit.SECONDS))
  }

  def createDatabase() = {
    createDb(postgres.getHost, postgres.getMappedPort(5432), "test", "viestinvalitus", "viestinvalitus")
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerName(postgres.getHost)
    ds.setDatabaseName("test")
    ds.setPortNumber(postgres.getMappedPort(5432))
    ds.setUser(postgres.getUsername)
    ds.setPassword(postgres.getPassword)
    val db = Database.forDataSource(ds, Option.empty)

    val viestit = TableQuery[Viestit]
    val setup = DBIO.seq(
      // Create the tables, including primary and foreign keys
      (viestit.schema).create,
    )
    db.run(setup).map(Unit => db)
  }

  @Test def testTallennus(): Unit = {
    val queueUrl = createQueue()
    val createEventSourceMappingResponse = createTallennus(queueUrl)

    val db = Await.result(createDatabase(), 5.seconds)
    createEventSourceMappingResponse.get

    getSqsClient(localstack).sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody("test message")
      .build())

    Thread.sleep(60*1000)

    val viestit = TableQuery[Viestit]
    Await.ready(db.run(viestit.result).map(_.foreach {
      case (id, heading) =>
        println("  " + id + "\t" + heading)
    }), 5.seconds)
  }

  @Test def testFullFlow(): Unit = {
    val queueUrl = createQueue()
    val createFunctionUrlConfigResponse = createVastaanOtto(queueUrl)
    val createEventSourceMappingResponse = createTallennus(queueUrl)

    val db = Await.result(createDatabase(), 5.seconds)
    createFunctionUrlConfigResponse.get
    createEventSourceMappingResponse.get

    val url: String = new URIBuilder(createFunctionUrlConfigResponse.get().functionUrl()).setPort(localstack.getEndpoint.getPort).build().toString()
    val request: HttpPut  = new HttpPut(url + "v2/resource/viesti" );
    request.setEntity(new ByteArrayEntity("{\"heading\":\"testh\",\"content\":\"test\"}".getBytes(StandardCharsets.UTF_8)))
    request.setHeader("Accept", "application/json")
    request.setHeader("Content-Type", "application/json")
    val response: HttpResponse = HttpClientBuilder.create().build().execute(request)

    System.out.println(IOUtils.toString(response.getEntity.getContent))
    Thread.sleep(60*1000)

    val viestit = TableQuery[Viestit]
    Await.ready(db.run(viestit.result).map(_.foreach {
      case (id, heading) =>
        println("  " + id + "\t" + heading)
    }), 5.seconds)
  }
}

