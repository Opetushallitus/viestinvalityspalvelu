package fi.oph.viestinvalitys.skannaus

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.{SQSBatchResponse, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.business.{KantaOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import fi.vm.sade.auditlog.Changes
import org.crac.Resource
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

case class GuardDutyS3ObjectDetails(
                                     @BeanProperty bucketName: String,
                                     @BeanProperty objectKey: String
                                   ) {
  def this() = {
    this(null, null)
  }
}

case class GuardDutyScanResultDetails(
                                       @BeanProperty scanResultStatus: String
                                     ) {
  def this() = {
    this(null)
  }
}

case class GuardDutyDetail(
                            @BeanProperty s3ObjectDetails: GuardDutyS3ObjectDetails,
                            @BeanProperty scanResultDetails: GuardDutyScanResultDetails
                          ) {
  def this() = {
    this(null, null)
  }
}

case class GuardDutyScanEvent(
                               @BeanProperty detail: GuardDutyDetail
                             ) {
  def this() = {
    this(null)
  }
}

class LambdaHandler
  extends RequestHandler[SQSEvent, SQSBatchResponse],
    Resource {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler])

  val queueUrl =
    ConfigurationUtil
      .getConfigurationItem(ConfigurationUtil.SKANNAUS_QUEUE_URL_KEY)
      .get

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(
      new Jdk8Module()
    ) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(
      DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
      false
    )
    mapper.configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
      false
    )
    mapper
  }

  def deserialisoiGuardDutyViesti(
                                   viesti: String
                                 ): GuardDutyScanEvent =
    mapper.readValue(
      viesti,
      classOf[GuardDutyScanEvent]
    )

  override def handleRequest(
                              event: SQSEvent,
                              context: Context
                            ): SQSBatchResponse = {
    LogContext(
      requestId = context.getAwsRequestId,
      functionName = context.getFunctionName
    )(() => {
      LOG.info("Prosessoidaan GuardDuty-haittaohjelmaskannauksen tulokset")

      val failures =
        event.getRecords.asScala.flatMap(processSingleMessage)

      SQSBatchResponse
        .builder()
        .withBatchItemFailures(failures.asJava)
        .build()
    })
  }

  def processSingleMessage(
                            sqsMessage: SQSMessage
                          ): Option[SQSBatchResponse.BatchItemFailure] = {
    try
      val message =
        deserialisoiGuardDutyViesti(sqsMessage.getBody)

      val objectKey =
        message.detail.s3ObjectDetails.objectKey

      val scanResultStatus =
        message.detail.scanResultDetails.scanResultStatus

      val tunniste = {
        try
          Option(UUID.fromString(objectKey))
        catch
          case _: Exception =>
            LOG.info(
              s"Tiedostonimi ei UUID-muotoinen: $objectKey"
            )
            Option.empty
      }

      tunniste.foreach(tunniste => {
        LogContext(
          liiteTunniste = tunniste.toString
        )(() => {

          val uusiTila =
            scanResultStatus match
              case "NO_THREATS_FOUND" =>
                LiitteenTila.PUHDAS

              case "THREATS_FOUND" =>
                LiitteenTila.SAASTUNUT

              case "UNSUPPORTED" =>
                LiitteenTila.VIRHE

              case "ACCESS_DENIED" =>
                LiitteenTila.VIRHE

              case "FAILED" =>
                LiitteenTila.VIRHE

              case tuntematonTila =>
                LOG.warn(
                  s"Tuntematon GuardDuty-skannauksen tulos: $tuntematonTila"
                )
                LiitteenTila.VIRHE

          LOG.info(
            s"GuardDuty-skannauksen tulos: $scanResultStatus, " +
              s"päivitetään liitteen tila tilaan: $uusiTila"
          )

          val changes: Changes =
            new Changes.Builder()
              .updated(
                "liitteenTila",
                LiitteenTila.SKANNAUS.toString,
                uusiTila.toString
              )
              .build()

          AuditLog.logChanges(
            AuditLog.getAuditUserForLambda(),
            Map("liite" -> tunniste.toString),
            AuditOperation.UpdateLiitteenTila,
            changes
          )

          KantaOperaatiot(DbUtil.database)
            .paivitaLiitteenTila(
              tunniste,
              uusiTila
            )
        })
      })

      None

    catch
      case e: Exception =>
        LOG.error(
          "Virhe prosessoitaessa GuardDuty-haittaohjelmaskannauksen tulosta",
          e
        )

        Some(
          SQSBatchResponse.BatchItemFailure
            .builder()
            .withItemIdentifier(sqsMessage.getMessageId)
            .build()
        )
  }

  @throws[Exception]
  def beforeCheckpoint(
                        context: org.crac.Context[_ <: Resource]
                      ): Unit = {
    AwsUtil.sqsClient
  }

  @throws[Exception]
  def afterRestore(
                    context: org.crac.Context[_ <: Resource]
                  ): Unit = {}
}