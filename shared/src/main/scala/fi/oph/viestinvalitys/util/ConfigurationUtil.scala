package fi.oph.viestinvalitys.util

import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

enum Mode:
  case LOCAL, TEST, PRODUCTION

object ConfigurationUtil {

  final val AJASTUS_QUEUE_URL_KEY = "AJASTUS_QUEUE_URL"
  final val SKANNAUS_QUEUE_URL_KEY = "SKANNAUS_QUEUE_URL"
  final val SESMONITOROINTI_QUEUE_URL_KEY = "SES_MONITOROINTI_QUEUE_URL"

  def getConfigurationItem(key: String): Option[String] =
    sys.env.get(key).orElse(sys.props.get(key))

  def getMode(): Mode =
    getConfigurationItem("MODE").map(value => Mode.valueOf(value)).getOrElse(Mode.PRODUCTION)

  def getParameter(name: String): String =
    val ssmClient = SsmClient.builder()
      .credentialsProvider(ContainerCredentialsProvider.builder().build()) // tämä on SnapStartin takia
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