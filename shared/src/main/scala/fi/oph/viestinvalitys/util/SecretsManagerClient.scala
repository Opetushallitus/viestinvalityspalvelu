package fi.oph.viestinvalitys.util

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.slf4j.LoggerFactory
import scala.util.{Try, Success, Failure}

@JsonIgnoreProperties(ignoreUnknown = true)
case class DatabaseSecret(username: String, password: String, dbname: String, host: String, port: Int)
case class CasSecret(username: String, password: String)

object SecretsManagerClient {
  private val log = {
    LoggerFactory.getLogger(getClass)
  }
  private val secretsExtensionHttpPort = 2773
  private val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  private def getSecretId(secretIdKey: String): Option[String] = sys.env.get(secretIdKey)

  // https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_lambda.html
  private def secretsExtensionEndpoint(secretId: String): String =
    s"http://localhost:$secretsExtensionHttpPort/secretsmanager/get?secretId=$secretId&withDecryption=true"

  private def getSecret[T](secretIdKey: String, valueType: Class[T]): Option[T] = getSecretId(secretIdKey) match {
    case None =>
      log.info(s"$secretIdKey environment variable is not set, skipping secret lookup")
      None
    case Some(secretId) =>
      val client = HttpClient.newHttpClient()
      val request = HttpRequest.newBuilder()
        .uri(URI.create(secretsExtensionEndpoint(secretId)))
        .header("X-Aws-Parameters-Secrets-Token", sys.env.getOrElse("AWS_SESSION_TOKEN", ""))
        .GET()
        .build()

      Try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
          val jsonNode: JsonNode = objectMapper.readTree(response.body())
          val secretString = jsonNode.get("SecretString").asText()
          Some(objectMapper.readValue(secretString, valueType))
        } else {
          log.error(s"Received status code ${response.statusCode()}")
          throw new RuntimeException()
        }
      } match {
        case Success(secretOpt) => secretOpt
        case Failure(exception) =>
          log.error(s"Error retrieving secret: ${exception.getMessage}")
          throw new RuntimeException()
      }
  }

  def getDatabaseSecret: Option[DatabaseSecret] = getSecret("DB_SECRET_ID", classOf[DatabaseSecret])
  def getCasSecret: Option[CasSecret] = getSecret("CAS_SECRET_ID", classOf[CasSecret])
}