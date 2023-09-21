package fi.oph.viestinvalitus

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, CreateQueueResponse, DeleteMessageRequest, ReceiveMessageRequest, ReceiveMessageResponse, SendMessageRequest, SendMessageResponse}

import java.net.URI

class SQSService(private var queueUrl: String, private var endpoint: String, private var region: String, private var accessKey: String, private var secretKey: String) {

  val sqsClient: SqsClient = SqsClient.builder()
    .endpointOverride(new URI(endpoint))
    .credentialsProvider(
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)
      )
    )
    .region(Region.of(region))
    .build();

  def sendMessage(message: String): Unit = {
    val sendMessageResponse: SendMessageResponse = sqsClient.sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody("test message")
      .build())
  }
}