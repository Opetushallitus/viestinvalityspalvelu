package fi.oph.viestinvalitys.sesmonitorointi

import com.fasterxml.jackson.annotation.{JsonSetter, Nulls}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.business.VastaanottajanTila

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

val MESSAGE_ID_HEADER_NAME = "Message-ID"

trait AsVastaanottajanSiirtyma {

  def asVastaanottajanSiirtyma(): Option[(VastaanottajanTila, Option[String])]
}

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

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module()) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.setDefaultSetterInfo(JsonSetter.Value.construct(Nulls.AS_EMPTY, Nulls.SET))
    mapper
  }

  def deserialisoiSesNotifikaatio(json: String): SesMonitoringMessage =
    mapper.readValue(json, classOf[SesMonitoringMessage])

  def deserialisoiSqsViesti(json: String): SesMonitoringMessage =
    val sqsViesti = mapper.readValue(json, classOf[SqsViesti])
    deserialisoiSesNotifikaatio(sqsViesti.Message)

}

