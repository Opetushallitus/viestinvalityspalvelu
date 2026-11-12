package fi.oph.viestinvalitys.ajastus

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, LogContext}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants
import org.crac.Resource
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry}

import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.jdk.CollectionConverters.SeqHasAsJava

class LambdaHandler extends RequestHandler[Any, Void], Resource {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  private val MAX_LOGGED_RESPONSE_BYTES = 2048

  private def readResponseBody(stream: InputStream): String = {
    if (stream == null) return ""

    try {
      val bytes = stream.readNBytes(MAX_LOGGED_RESPONSE_BYTES + 1)
      val truncated = bytes.length > MAX_LOGGED_RESPONSE_BYTES
      val body = new String(bytes.take(MAX_LOGGED_RESPONSE_BYTES), StandardCharsets.UTF_8)
      if (truncated) body + "... [truncated]" else body
    } finally {
      stream.close()
    }
  }

  private def pingHealthcheck(service: String, url: String): Unit = {
    val startedAt = System.nanoTime()
    var connection: HttpURLConnection = null
    var responseCode: Option[Int] = None
    var httpFailureLogged = false

    try {
      connection = URL(url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")

      val status = connection.getResponseCode
      responseCode = Some(status)
      val responseStream = if (status >= 400) connection.getErrorStream else connection.getInputStream
      val responseBody = readResponseBody(responseStream)
      val durationMs = (System.nanoTime() - startedAt) / 1000000
      val cloudFrontRequestId = Option(connection.getHeaderField("x-amz-cf-id")).getOrElse("")
      val cache = Option(connection.getHeaderField("x-cache")).getOrElse("")
      val via = Option(connection.getHeaderField("via")).getOrElse("")
      val contentType = Option(connection.getHeaderField("content-type")).getOrElse("")

      if (status < 200 || status >= 300) {
        httpFailureLogged = true
        LOG.error(
          "Healthcheck returned HTTP error: service={} url={} status={} durationMs={} xAmzCfId={} xCache={} via={} contentType={} responseBody={}",
          service, url, status, durationMs, cloudFrontRequestId, cache, via, contentType, responseBody
        )
        throw new IOException(s"Healthcheck for $service returned HTTP $status")
      }

      LOG.info("Healthcheck succeeded: service={} url={} status={} durationMs={}", service, url, status, durationMs)
    } catch {
      case e: IOException =>
        if (!httpFailureLogged) {
          val durationMs = (System.nanoTime() - startedAt) / 1000000
          LOG.error(
            "Healthcheck request failed: service={} url={} status={} durationMs={}",
            service, url, responseCode.map(_.toString).getOrElse("no response"), durationMs, e
          )
        }
        throw e
    } finally {
      if (connection != null) connection.disconnect()
    }
  }

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
        pingHealthcheck("vastaanotto", "https://" + HOSTNAME + LahetysAPIConstants.HEALTHCHECK_PATH)
      }
      {
        LOG.info("Pingataan raportointilambdaa")
        pingHealthcheck("raportointi", "https://" + HOSTNAME + "/raportointi/v1/healthcheck")
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
