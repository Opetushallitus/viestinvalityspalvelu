package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.business.{RaportointiTila, VastaanottajanTila, raportointiTilat}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.emailRegex

import java.time.Instant
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*

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

  def asValidRaportointitila(parametri: Optional[String]): Option[String] =
    // TODO tee fiksummin
    if (!parametri.isPresent || !parametri.equals(RaportointiTila.epaonnistui) || !parametri.equals(RaportointiTila.kesken) || !parametri.equals(RaportointiTila.valmis))
      Option.empty
    else
      Option.apply(parametri.get())

  def valmis(p: VastaanottajanTila): Boolean =
    raportointiTilat.valmiit.filter(tila => p.equals(tila)).size > 0

  def kesken(p: VastaanottajanTila): Boolean =
    raportointiTilat.kesken.filter(tila => p.equals(tila)).size > 0

  def epaonnistui(p: VastaanottajanTila): Boolean =
    raportointiTilat.epaonnistuneet.filter(tila => p.equals(tila)).size > 0
  def getRaportointiTila(tila: VastaanottajanTila): Option[String] =
    tila match
      case t if valmis(t)       => Option.apply("valmis")
      case t if kesken(t)       => Option.apply("kesken")
      case t if epaonnistui(t)  => Option.apply("epaonnistui")
      case _                    => Option.empty

}

