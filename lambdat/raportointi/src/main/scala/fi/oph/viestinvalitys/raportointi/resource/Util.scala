package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.business.{RaportointiTila, VastaanottajanTila, raportointiTilat}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.emailRegex

import java.time.Instant
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

object ParametriUtil {

  def asUUID(tunniste: String): Option[UUID] =
    try
      Option.apply(UUID.fromString(tunniste))
    catch
      case e: Exception => Option.empty

  def asUUID(tunniste: Optional[String]): Option[UUID] =
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

  def asInt(parametri: Optional[String]): Option[Int] =
    try
      Option.apply(parametri.get.toInt)
    catch
      case e: Exception => Option.empty

  def asInstant(parametri: Optional[String]): Option[Instant] =
    try
      Option.apply(Instant.parse(parametri.get))
    catch
      case e: Exception => Option.empty

  def asValidEmail(parametri: Optional[String]): Option[String] =
    if (!parametri.isPresent || !RaportointiAPIConstants.emailRegex.matches(parametri.get()))
      Option.empty
    else
      Option.apply(parametri.get())

  def asValidRaportointitila(tila: Optional[String]): Option[String] =
    if(tila.isPresent)
      tila.get match
      case t if RaportointiTila.values.exists(_.toString.equals(t)) => Option.apply(t)
      case _ => Option.empty
    else
      Option.empty

  def valmis(p: VastaanottajanTila): Boolean =
    raportointiTilat.valmiit.contains(p)

  def kesken(p: VastaanottajanTila): Boolean =
    raportointiTilat.kesken.contains(p)

  def epaonnistui(p: VastaanottajanTila): Boolean =
    raportointiTilat.epaonnistuneet.contains(p)

  def getRaportointiTila(tila: VastaanottajanTila): Option[String] =
    tila match
      case t if valmis(t)       => Option.apply("valmis")
      case t if kesken(t)       => Option.apply("kesken")
      case t if epaonnistui(t)  => Option.apply("epaonnistui")
      case _                    => Option.empty

}

object MaskiUtil {
  
  def maskaaSalaisuudet(input: String, maskit: Map[String, Option[String]]): String =
    val regexPattern = maskit.keys.map(escapeRegex).mkString("|")
    val regex = new Regex(regexPattern)
    regex.replaceAllIn(input, m =>
      maskit.getOrElse(m.matched, Some("xxxxx")).getOrElse("xxxxx")
    )

  def escapeRegex(s: String): String =
    "\\Q" + s.replace("\\E", "\\E\\\\E\\Q") + "\\E"
}

