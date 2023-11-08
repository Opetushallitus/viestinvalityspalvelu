package fi.oph.viestinvalitys.integraatio

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.github.dockerjava.api.model.Ports.Binding
import com.github.dockerjava.api.model.{ExposedPort, PortBinding}
import com.redis.testcontainers.RedisContainer
import fi.oph.viestinvalitys.db.Viestipohjat
import fi.oph.viestinvalitys.vastaanotto.LambdaHandler
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpPut, HttpUriRequest}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.*
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.containers.{GenericContainer, Network, PostgreSQLContainer}
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import slick.jdbc.PostgresProfile.api.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.retry.backoff.{BackoffStrategy, FixedDelayBackoffStrategy}
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.lambda.{LambdaAsyncClient, LambdaClient}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListBucketsRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*

import java.io.{File, FileInputStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}

@Test
@TestInstance(Lifecycle.PER_CLASS)
class AppTest {

  val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  val HIGH_PRIORITY_QUEUE_NAME: String = "viestinvalityspalvelu-vastaanotto-high"

  val network = Network.newNetwork();

  @Container var localstack: LocalStackContainer = new LocalStackContainer(new DockerImageName("localstack/localstack:2.2.0"))
    .withEnv("LAMBDA_DOCKER_NETWORK", network.getId)
    .withEnv("LAMBDA_KEEPALIVE_MS", "60000")
    .withEnv("LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT", "120")
    .withServices(Service.SQS, Service.LAMBDA)    .withNetwork(network)
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))
    .withExposedPorts(4566)

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("viestinvalitys")
    .withPassword("viestinvalitys")
    .withNetwork(network)
    .withNetworkAliases("postgres")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))

  @Container var redis = new RedisContainer(DockerImageName.parse("redis:6.2.6"))
    .withNetwork(network)
    .withNetworkAliases("redis")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))

  @BeforeAll def setUp(): Unit = {
    localstack.start()
    postgres.start()
    redis.start()
  }

  @AfterAll def tearDown(): Unit = {
    // tämä sammuttaa localstacking (eikä tapa) jolloin lambda containerit poistetaan kuten pitääkin
    localstack.getDockerClient().stopContainerCmd(localstack.getContainerId()).exec();
    postgres.stop()
  }

  @Test def testReplace(): Unit = {
    Assertions.assertEquals("https://virkailija.hahtuvaopintopolku.fi/abc", "https://null.execute-api.eu-west-1.amazonaws.com/abc".replaceAll("null\\.execute-api\\.eu-west-1\\.amazonaws\\.com", "virkailija.hahtuvaopintopolku.fi"))
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
        .s3Bucket("hot-reload")
        .s3Key(file.getCanonicalPath)
        .build())
      .handler(handler.getCanonicalName)
      .runtime("java17")
      .architectures(Architecture.ARM64)
      .memorySize(512)
      .timeout(1500)
      .role("arn:aws:iam::000000000000:role/lambda-role")
      .environment(Environment.builder()
        .variables(variables)
        .build())
      .build()

    val createResponse = lambdaAsyncClient.createFunction(createRequest)

    createResponse.thenApply(cr => {
      lambdaAsyncClient.waiter().waitUntilFunctionActive(
        GetFunctionConfigurationRequest.builder()
          .functionName(name)
          .build,
        WaiterOverrideConfiguration.builder()
          .backoffStrategy(FixedDelayBackoffStrategy.create(java.time.Duration.ofMillis(250)))
          .build()
      ).get
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
      classOf[LambdaHandler],
      new File("../vastaanotto/target/hot"),
      java.util.Map.of(
        "localstack.queueUrl", queueUrl,
        "spring_redis_host", "redis",
        "spring_redis_port", "6379"
      ),
      Option.apply(5050))

    val lambdaClient = this.getLambdaClient(localstack)
    createFunctionResponse.thenApply(r =>
      lambdaClient.createFunctionUrlConfig(CreateFunctionUrlConfigRequest.builder()
        .functionName(r.functionName())
        .build()))
  }

/*
  def createTallennus(queueUrl: String) = {
    val createTallennusFunctionResponse = this.createFunction(
      localstack,
      "tallennus",
      classOf[fi.oph.viestinvalitys.tallennus.LambdaHandler],
      new File("../tallennus/target/hot"), //tallennus-0.1-SNAPSHOT.jar"),
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
*/

  def createDb(host: String, port: Int, dbName: String, user: String, pwd: String) = {
    val onlyHostNoDbUrl = s"jdbc:postgresql://$host:$port/"
    val ds: Database = Database.forURL(onlyHostNoDbUrl, user = user, password = pwd, driver = driver)
    Await.result(ds.run(sqlu"CREATE DATABASE #$dbName"), Duration(5, TimeUnit.SECONDS))
  }

  def createDatabase() = {
    createDb(postgres.getHost, postgres.getMappedPort(5432), "test", "viestinvalitys", "viestinvalitys")
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerName(postgres.getHost)
    ds.setDatabaseName("test")
    ds.setPortNumber(postgres.getMappedPort(5432))
    ds.setUser(postgres.getUsername)
    ds.setPassword(postgres.getPassword)
    val db = Database.forDataSource(ds, Option.empty)

    val viestit = TableQuery[Viestipohjat]
    val setup = DBIO.seq(
      // Create the tables, including primary and foreign keys
      (viestit.schema).create,
    )
    db.run(setup).map(Unit => db)
  }

  @Test def testLocalStackStartup() = {
    val queueUrl = createQueue()
    val createFunctionUrlConfigResponse = createVastaanOtto(queueUrl)
    val abc = createFunctionUrlConfigResponse.get



    /*
    val s3Client = S3Client
      .builder()
      .endpointOverride(localstack.getEndpoint())
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
        )
      )
      .region(Region.of(localstack.getRegion()))
      .build();

    val bucketsResponse = s3Client.listBuckets(ListBucketsRequest.builder()
      .build())
*/
  }

  @Test def testVastaanotto(): Unit = {
    val queueUrl = createQueue()
    val createFunctionUrlConfigResponse = createVastaanOtto(queueUrl)
    createFunctionUrlConfigResponse.get

    val url: String = new URIBuilder(createFunctionUrlConfigResponse.get().functionUrl()).setPort(localstack.getEndpoint.getPort).build().toString()
    val request: HttpPut = new HttpPut(url + "v2/resource/viesti");
    request.setEntity(new ByteArrayEntity("{\"heading\":\"test1\",\"content\":\"test1\"}".getBytes(StandardCharsets.UTF_8)))
    request.setHeader("Accept", "application/json")
    request.setHeader("Content-Type", "application/json")
    val response: HttpResponse = HttpClientBuilder.create().build().execute(request)
    System.out.println(IOUtils.toString(response.getEntity.getContent))

    val request2: HttpPut = new HttpPut(url + "v2/resource/viesti");
    request2.setEntity(new ByteArrayEntity("{\"heading\":\"test2\",\"content\":\"test2\"}".getBytes(StandardCharsets.UTF_8)))
    request2.setHeader("Accept", "application/json")
    request2.setHeader("Content-Type", "application/json")
    val response2: HttpResponse = HttpClientBuilder.create().build().execute(request2)
    System.out.println(IOUtils.toString(response2.getEntity.getContent))
  }


/*
  @Test def testTallennus(): Unit = {
    val queueUrl = createQueue()

    val db = Await.result(createDatabase(), 5.seconds)

    getSqsClient(localstack).sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody("test message 1")
      .build())

    getSqsClient(localstack).sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody("test message 2")
      .build())

    val createEventSourceMappingResponse = createTallennus(queueUrl)
    createEventSourceMappingResponse.get

    Thread.sleep(60*1000)

    val viestit = TableQuery[Viestit]
    Await.ready(db.run(viestit.result).map(_.foreach {
      case (id, heading) =>
        println("  " + id + "\t" + heading)
    }), 5.seconds)
  }
*/

  @Test def testFullFlow(): Unit = {
    val queueUrl = createQueue()
    val createFunctionUrlConfigResponse = createVastaanOtto(queueUrl)
    //val createEventSourceMappingResponse = createTallennus(queueUrl)

    val db = Await.result(createDatabase(), 5.seconds)
    createFunctionUrlConfigResponse.get
    //createEventSourceMappingResponse.get

    val url: String = new URIBuilder(createFunctionUrlConfigResponse.get().functionUrl()).setPort(localstack.getEndpoint.getPort).build().toString()
    val request: HttpPut  = new HttpPut(url + "v2/resource/viesti" );
    request.setEntity(new ByteArrayEntity("{\"heading\":\"testh\",\"content\":\"test\"}".getBytes(StandardCharsets.UTF_8)))
    request.setHeader("Accept", "application/json")
    request.setHeader("Content-Type", "application/json")
    val response: HttpResponse = HttpClientBuilder.create().build().execute(request)

    System.out.println(IOUtils.toString(response.getEntity.getContent))
    Thread.sleep(60*1000)

    val viestipohjat = TableQuery[Viestipohjat]
    Await.ready(db.run(viestipohjat.result).map(_.foreach {
      case(id, heading) =>
        println("  " + id + "\t" + heading)
    }), 5.seconds)
  }
}


