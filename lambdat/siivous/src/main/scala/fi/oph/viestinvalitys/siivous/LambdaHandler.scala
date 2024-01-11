package fi.oph.viestinvalitys.siivous

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.business.KantaOperaatiot
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil}
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

class LambdaHandler extends RequestHandler[Object, Void] {

  val BUCKET_NAME = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Object, context: Context): Void = {
    LOG.info("Siivotaan poistettavat viestit ja liitteet")
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    kantaOperaatiot.poistaPoistettavatLahetykset()

    val luotuEnnen = Instant.now.minusSeconds(60*60*24*7)
    val liiteTunnisteet = kantaOperaatiot.poistaPoistettavatLiitteet(luotuEnnen)
    liiteTunnisteet.foreach(tunniste => {
      LOG.info("Poistetaan liite: " + tunniste.toString)
      val deleteObjectResponse = AwsUtil.s3Client.deleteObject(DeleteObjectRequest
        .builder()
        .bucket(BUCKET_NAME)
        .key(tunniste.toString)
        .build())
    })
    null
  }
}
