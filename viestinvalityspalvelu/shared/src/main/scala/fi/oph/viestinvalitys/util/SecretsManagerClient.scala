package fi.oph.viestinvalitys.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient as AwsSecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

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

  private def getSecret[T](secretIdKey: String, valueType: Class[T]): Option[T] = getSecretId(secretIdKey) match {
    case None =>
      log.info(s"$secretIdKey  environment variable is not set, skipping secret lookup")
      None
    case Some(secretId) =>
      val secretsClient = AwsSecretsManagerClient
        .builder()
        .credentialsProvider(AwsUtil.credentialsProvider)
        .build()

      Try {
        val getSecretValueRequest = GetSecretValueRequest.builder()
          .secretId(secretId)
          .build()

        val getSecretValueResponse = secretsClient.getSecretValue(getSecretValueRequest)
        val secretString = getSecretValueResponse.secretString()
        Some(objectMapper.readValue(secretString, valueType))
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