package fi.oph.viestinvalitys.vastaanotto.resource

import java.util.UUID

object UUIDUtil {

  def asUUID(tunniste: String): Option[UUID] =
    try
      Option.apply(UUID.fromString(tunniste))
    catch
      case e: Exception => Option.empty

  def validUUIDs(tunnisteet: Seq[String]): Seq[UUID] =
    tunnisteet
      .map(asUUID)
      .filter(tunniste => tunniste.isDefined)
      .map(tunniste => tunniste.get)
}

