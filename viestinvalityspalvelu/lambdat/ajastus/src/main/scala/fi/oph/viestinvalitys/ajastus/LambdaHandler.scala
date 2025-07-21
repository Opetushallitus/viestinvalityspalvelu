package fi.oph.viestinvalitys.ajastus

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, LogContext}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants
import org.crac.Resource
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry}

import java.net.{HttpURLConnection, URL}
import java.time.Instant
import scala.jdk.CollectionConverters.SeqHasAsJava

class LambdaHandler extends RequestHandler[Any, Void], Resource {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Any, context: Context): Void = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY).get

      val sqsClient = AwsUtil.sqsClient
      LOG.info("Ajastetaan lähetyslambda")
      val timestamp = Instant.now()
      Range(0, ConfigurationUtil.AJASTUS_POLLS_PER_MINUTE)
        .map(v => v * ConfigurationUtil.AJASTUS_POLLING_INTERVAL_SECONDS)
        .map(delay => SendMessageBatchRequestEntry.builder()
          .id(java.util.UUID.randomUUID().toString)
          .messageBody(timestamp.plusSeconds(delay).toString)
          .delaySeconds(delay)
          .build())
          .grouped(10)
          .foreach(entries => sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(entries.asJava)
            .build())
          )

      val HOSTNAME = ConfigurationUtil.getConfigurationItem("PING_HOSTNAME").get
      {
        LOG.info("Pingataan vastaanottolambdaa")
        val con = URL("https://" + HOSTNAME + LahetysAPIConstants.HEALTHCHECK_PATH).openConnection().asInstanceOf[HttpURLConnection];
        con.setRequestMethod("GET")
        con.getHeaderFields()
        con.getInputStream.close()
      }
      {
        LOG.info("Pingataan raportointilambdaa")
        val con = URL("https://" + HOSTNAME + "/raportointi/v1/healthcheck").openConnection().asInstanceOf[HttpURLConnection];
        con.setRequestMethod("GET")
        con.getHeaderFields()
        con.getInputStream.close()
      }

      null
    })
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    AwsUtil.sqsClient
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
  }
}