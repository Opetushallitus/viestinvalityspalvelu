package fi.oph.viestinvalitys.tilapaivitys

import com.fasterxml.jackson.annotation.{JsonSetter, Nulls}
import tools.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.business.VastaanottajanTila

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

val MESSAGE_ID_HEADER_NAME = "Message-ID"

trait AsVastaanottajanSiirtyma {

  def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])]
}

case class Mail(@BeanProperty messageId: String) {
  def this() = {
    this(null)
  }
}

case class BouncedRecipient(@BeanProperty diagnosticCode: String) {
  def this() = {
    this(null)
  }
}

case class Bounce(@BeanProperty bounceType: String, @BeanProperty bouncedRecipients: java.util.List[BouncedRecipient])
  extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null, null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.BOUNCE, this.bouncedRecipients.asScala.find(r => true).map(r => r.diagnosticCode)))
}

case class Complaint(@BeanProperty complaintFeedbackType: String) extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.COMPLAINT, Option.apply(this.complaintFeedbackType)))
}

case class Delivery(@BeanProperty timestamp: String) extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.DELIVERY, Option.empty))

}

case class Send() extends AsVastaanottajanSiirtyma {

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.SEND, Option.empty))
}

case class Reject(@BeanProperty reason: String) extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.REJECT, Option.apply(this.reason)))
}

case class DelayedRecipient(@BeanProperty diagnosticCode: String) {
  def this() = {
    this(null)
  }
}

case class DeliveryDelay(@BeanProperty delayType: String, @BeanProperty delayedRecipients: java.util.List[DelayedRecipient])
  extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null, null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    Option.apply((VastaanottajanTila.DELIVERYDELAY, this.delayedRecipients.asScala.find(r => true).map(r => r.diagnosticCode)))
}

/**
 * AWS SES:n lähettämä eventti. Jsonista parsitaan vain ne kentät jotka ovat olennaisia tilasiirtymän tunnistamiseksi
 * ja halutaan antaa lisätietona
 */
case class SesMonitoringMessage(
                                 @BeanProperty eventType: String,
                                 @BeanProperty mail: Mail,
                                 @BeanProperty bounce: Bounce,
                                 @BeanProperty complaint: Complaint,
                                 @BeanProperty delivery: Delivery,
                                 @BeanProperty send: Send,
                                 @BeanProperty reject: Reject,
                                 @BeanProperty deliveryDelay: DeliveryDelay
                               ) extends AsVastaanottajanSiirtyma {
  def this() = {
    this(null, null, null, null, null, null, null, null)
  }

  override def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])] =
    this.eventType match
      case "Bounce"         => this.bounce.asVastaanottajanSiirtyma()
      case "Complaint"      => this.complaint.asVastaanottajanSiirtyma()
      // send ei sisällä lisätietoja, ja tulee usein vasta Delivery tai Bounce -eventtien jälkeen
      case "Send"           => Option.empty
      case "Delivery"       => this.delivery.asVastaanottajanSiirtyma()
      case "Reject"         => this.reject.asVastaanottajanSiirtyma()
      case "DeliveryDelay"  => this.deliveryDelay.asVastaanottajanSiirtyma()
      case _                => Option.empty
}

case class SqsViesti(@BeanProperty Message: String) {
  def this() = {
    this(null)
  }
}

object Deserialisoija {

  val mapper: ObjectMapper = JsonMapper.builder()
    .addModule(DefaultScalaModule)
    .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .changeDefaultNullHandling(_ => JsonSetter.Value.construct(Nulls.AS_EMPTY, Nulls.SET))
    .build()

  def deserialisoiSesNotifikaatio(json: String): Option[SesMonitoringMessage] =
    try
      Option.apply(mapper.readValue(json, classOf[SesMonitoringMessage]))
    catch
      case e: Exception => Option.empty

  def deserialisoiSqsViesti(json: String): Option[SesMonitoringMessage] =
    val sqsViesti = mapper.readValue(json, classOf[SqsViesti])
    Option.apply(sqsViesti.Message).map(message => deserialisoiSesNotifikaatio(message)).getOrElse(Option.empty)
}

