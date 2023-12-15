package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.vastaanotto.model.ViestiValidator
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants

import java.util.UUID

import scala.jdk.CollectionConverters.*

object APIConstants {

  final val RESPONSE_400_DESCRIPTION = "Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä"
  final val LAHETYS_RESPONSE_403_DESCRIPTION = "Käyttäjällä ei ole " + SecurityConstants.SECURITY_ROOLI_LAHETYS + "-oikeutta"
  final val KATSELU_RESPONSE_403_DESCRIPTION = "Käyttäjällä ei ole oikeutta lukea entiteettiä. Oikeus on jos:\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "-oikeus, tai\n" +
    "- Käyttäjä luonut entiteetin, tai\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_KATSELU + "-oikeus, ja lisäksi jokin entiteetin luonnin yhteydessä" +
    " liitetyistä lukuoikeuksista"
  final val KATSELU_RESPONSE_410_DESCRIPTION = "Entiteettiä ei löytynyt, tunniste on virheellinen tai entiteetti on poistettu säilytysajan päätyttyä"

  final val ENTITEETTI_TUNNISTE_INVALID = "Tunniste ei ole muodoltaan validi uuid"

  final val EXAMPLE_OTSIKKO_VALIDOINTIVIRHE = "[ \"" + ViestiValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"

  final val VIESTI_RATELIMIT_VIRHE = "Liikaa korkean prioriteetin lähetyspyyntöjä"

  final val ESIMERKKI_LIITETUNNISTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6"

  final val PRIORITEETTI_KORKEA_RATELIMIT_VIESTIA_SEKUNNISSA = 1
  final val PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA = 5
  final val PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA =
    PRIORITEETTI_KORKEA_RATELIMIT_VIESTIA_SEKUNNISSA * PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA
}

object UUIDUtil {

  def asUUID(tunniste: String): Option[UUID] =
    try
      Option.apply(UUID.fromString(tunniste))
    catch
      case e: Exception => Option.empty

  def asUUID(tunniste: java.util.Optional[String]): Option[UUID] =
    try
      Option.apply(UUID.fromString(tunniste.get))
    catch
      case e: Exception => Option.empty

  def validUUIDs(tunnisteet: java.util.Optional[java.util.List[String]]): Seq[UUID] =
    tunnisteet.map(t => t.stream()
        .map(tunniste => asUUID(tunniste))
        .filter(tunniste => tunniste.isDefined)
        .map(tunniste => tunniste.get)
        .toList.asScala.toSeq)
      .orElse(Seq.empty)
}

