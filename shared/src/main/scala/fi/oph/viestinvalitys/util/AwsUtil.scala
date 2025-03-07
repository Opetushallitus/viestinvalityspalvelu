package fi.oph.viestinvalitys.util

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, ContainerCredentialsProvider, DefaultCredentialsProvider, SystemPropertyCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, DeleteMessageRequest}

import java.net.URI
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

object AwsUtil {

  final val MAX_AUDIT_LOG_ENTRY_SIZE = 240 * 1024

  final val LOCALSTACK_HOST_KEY = "LOCALSTACK_PORT"

  val mode = ConfigurationUtil.getMode()
  val credentialsProvider = AwsCredentialsProviderChain.of(DefaultCredentialsProvider.builder().build(), ContainerCredentialsProvider.builder().build())

  lazy val cloudWatchClient = {
    if (mode == Mode.LOCAL)
      CloudWatchClient.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      CloudWatchClient.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

  lazy val cloudWatchLogsClient = {
    if (mode == Mode.LOCAL)
      CloudWatchLogsClient.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      CloudWatchLogsClient.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

  lazy val s3Client = {
    if(mode==Mode.LOCAL)
      S3Client.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .forcePathStyle(true)
        .build()
    else
      S3Client.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

  lazy val sesClient = {
    if (mode == Mode.LOCAL)
      SesClient.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SesClient.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

  lazy val snsClient = {
    if (mode == Mode.LOCAL)
      SnsClient.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SnsClient.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

  lazy val sqsClient = {
    if (mode == Mode.LOCAL)
      SqsClient.builder()
        .endpointOverride(new URI(ConfigurationUtil.getConfigurationItem(LOCALSTACK_HOST_KEY).getOrElse("http://localhost:4566")))
        .region(Region.US_EAST_1)
        .credentialsProvider(SystemPropertyCredentialsProvider.create())
        .build()
    else
      SqsClient.builder()
        .credentialsProvider(credentialsProvider)
        .build()
  }

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
