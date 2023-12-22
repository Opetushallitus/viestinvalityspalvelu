package fi.oph.viestinvalitys.raportointi.resource

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
}

