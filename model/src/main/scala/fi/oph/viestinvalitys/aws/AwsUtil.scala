package fi.oph.viestinvalitys.aws

import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil, Mode}
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.{ContainerCredentialsProvider, HttpCredentialsProvider, SystemPropertyCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, DeleteMessageRequest}
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient

import scala.jdk.CollectionConverters.*
import java.net.URI
import java.util.stream.Collectors

object AwsUtil {

  val mode = ConfigurationUtil.getMode()

  def getCredentialsProvider(): HttpCredentialsProvider =
    ContainerCredentialsProvider.builder().build()

  def getCloudWatchClient(): CloudWatchClient =
    if (mode == Mode.LOCAL)
      CloudWatchClient.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      CloudWatchClient.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()

  def getS3Client(): S3Client =
    if(mode==Mode.LOCAL)
      S3Client.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .forcePathStyle(true)
        .build()
    else
      S3Client.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()

  def getSesClient(): SesClient =
    if (mode == Mode.LOCAL)
      SesClient.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SesClient.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()

  def getSnsClient(): SnsClient =
    if (mode == Mode.LOCAL)
      SnsClient.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SnsClient.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()

  lazy val sqsClient = {
    if (mode == Mode.LOCAL)
      SqsClient.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SqsClient.builder()
        .credentialsProvider(getCredentialsProvider())
        .build()
  }

  def getSqsClient(): SqsClient =
    sqsClient

  def deleteMessages(messages: java.util.List[SQSEvent.SQSMessage], queueUrl: String): Unit =
    // deletoidaan viestit jonosta
    if(mode==Mode.LOCAL)
      // batch delete ei toimi LocalStackissa
      messages.forEach(message => {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(message.getReceiptHandle)
          .build())
      })
    else
      val entries: java.util.Collection[DeleteMessageBatchRequestEntry] = messages.stream().map(event => DeleteMessageBatchRequestEntry.builder()
        .id(event.getMessageId)
        .receiptHandle(event.getReceiptHandle)
        .build()).collect(Collectors.toList)
      sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
        .queueUrl(queueUrl)
        .entries(entries)
        .build())

}
