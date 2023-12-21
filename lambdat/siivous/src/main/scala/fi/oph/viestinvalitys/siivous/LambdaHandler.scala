package fi.oph.viestinvalitys.siivous

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.business.LahetysOperaatiot
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil}
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import fi.oph.viestinvalitys.aws.AwsUtil
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

class LambdaHandler extends RequestHandler[Object, Void] {

  val BUCKET_NAME = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Object, context: Context): Void = {
    LOG.info("Siivotaan poistettavat viestit ja liitteet")
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.database)
    lahetysOperaatiot.poistaPoistettavatViestit()

    val luotuEnnen = Instant.now.minusSeconds(60*60*24*7)
    lahetysOperaatiot.poistaPoistettavatLahetykset(luotuEnnen)
    val liiteTunnisteet = lahetysOperaatiot.poistaPoistettavatLiitteet(luotuEnnen)
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
