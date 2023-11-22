package fi.oph.viestinvalitys.sesmonitorointi

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}

import scala.beans.BeanProperty

val MESSAGE_ID_HEADER_NAME = "Message-ID"

case class Header(@BeanProperty name: String, @BeanProperty value: String) {
  def this() = {
    this(null, null)
  }
}

case class Mail(@BeanProperty headers: Array[Header]) {
  def this() = {
    this(null)
  }
}

case class Bounce(@BeanProperty bounceType: String) {
  def this() = {
    this(null)
  }
}

case class SesMonitoringMessage(
                                 @BeanProperty eventType: String,
                                 @BeanProperty mail: Mail,
                                 @BeanProperty bounce: Bounce) {
  def this() = {
    this(null, null, null)
  }
}

class Deserialisoija {

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  def deserialisoi(json: String): SesMonitoringMessage =
    mapper.readValue(json, classOf[SesMonitoringMessage])

}

