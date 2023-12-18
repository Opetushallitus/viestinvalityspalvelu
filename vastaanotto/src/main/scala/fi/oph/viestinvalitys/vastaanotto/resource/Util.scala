package fi.oph.viestinvalitys.vastaanotto.resource

import java.util.UUID
import scala.jdk.CollectionConverters.*

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

