package fi.oph.viestinvalitys.util

import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.UUID

enum Mode:
  case LOCAL, TEST, PRODUCTION

object ConfigurationUtil {

  final val AJASTUS_POLLING_INTERVAL_SECONDS  = 2
  final val AJASTUS_POLLS_PER_MINUTE          = 60 / AJASTUS_POLLING_INTERVAL_SECONDS
  final val AJASTUS_SENDING_QUOTA_PER_SECOND  = 10 // tämä on SES-quota - varmuusmarginaali (nyk. quota 14)

  final val AJASTUS_QUEUE_URL_KEY = "AJASTUS_QUEUE_URL"
  final val SKANNAUS_QUEUE_URL_KEY = "SKANNAUS_QUEUE_URL"
  final val SESMONITOROINTI_QUEUE_URL_KEY = "SES_MONITOROINTI_QUEUE_URL"

  final val ENVIRONMENT_NAME_KEY = "ENVIRONMENT_NAME"

  lazy val environment = ConfigurationUtil.getConfigurationItem(ENVIRONMENT_NAME_KEY).get;

  lazy val opintopolkuDomain = sys.env.get("OPINTOPOLKU_DOMAIN") match {
    case Some(domain) => domain
    case _ =>
      val environment = ConfigurationUtil.getConfigurationItem(ENVIRONMENT_NAME_KEY).get
      environment match
        case "localtest" => ConfigurationUtil.getConfigurationItem("LOCAL_OPINTOPOLKU_DOMAIN").get
        case "local" => ConfigurationUtil.getConfigurationItem("DEV_OPINTOPOLKU_DOMAIN").get
        case "pallero" => "testiopintopolku.fi"
        case "sade" => "opintopolku.fi"
        case _ => environment + "opintopolku.fi"
  }

  lazy val casPassword = {
    val localMode = ConfigurationUtil.getMode() == Mode.LOCAL
    if(localMode)
      "Testitunnus12345678!"
    else
      val environmentName = ConfigurationUtil.getConfigurationItem(ENVIRONMENT_NAME_KEY).get
      ConfigurationUtil.getParameter(s"/${environmentName}/viestinvalitys/palvelutunnus-password")
  }

  def getConfigurationItem(key: String): Option[String] =
    sys.env.get(key).orElse(sys.props.get(key))

  def getMode(): Mode =
    getConfigurationItem("MODE").map(value => Mode.valueOf(value)).getOrElse(Mode.PRODUCTION)

  def getParameter(name: String): String =
    val ssmClient = SsmClient.builder()
      .credentialsProvider(AwsUtil.credentialsProvider)
      .build();

    try {
      val parameterRequest = GetParameterRequest.builder
        .withDecryption(true)
        .name(name).build
      val parameterResponse = ssmClient.getParameter(parameterRequest)
      parameterResponse.parameter.value
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }
}