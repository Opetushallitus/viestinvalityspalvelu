package fi.oph.viestinvalitys.siivous

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

import java.time.Instant

class LambdaHandler extends RequestHandler[Object, Void] {

  val BUCKET_NAME = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Object, context: Context): Void = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      LOG.info("Siivotaan poistettavat viestit ja liitteet")
      val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
      kantaOperaatiot.poistaPoistettavatLahetykset()

      val luotuEnnen = Instant.now.minusSeconds(60 * 60 * 24 * 7)
      val liiteTunnisteet = kantaOperaatiot.poistaPoistettavatLiitteet(luotuEnnen)
      liiteTunnisteet.foreach(tunniste => {
        LogContext(liiteTunniste = tunniste.toString)(() => {
          LOG.info("Poistetaan liite S3:sta")
          try
            val deleteObjectResponse = AwsUtil.s3Client.deleteObject(DeleteObjectRequest
              .builder()
              .bucket(BUCKET_NAME)
              .key(tunniste.toString)
              .build())
          catch
            case e: Exception => LOG.error("liitteen poistaminen epäonnistui", e)
        })
      })

      val poistetutAvaimet = kantaOperaatiot.poistaIdempotencyKeys(Instant.now().minusSeconds(60*60*48))
      LOG.info("Poistettiin idempotency-avaimet (" + poistetutAvaimet + " kpl) yli 48h sitten luoduilta viesteiltä")

      null
    })
  }
}
