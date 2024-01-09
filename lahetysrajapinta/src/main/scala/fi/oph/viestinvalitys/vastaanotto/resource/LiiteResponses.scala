package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.vastaanotto.model.LuoLiiteSuccessResponse
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants.ESIMERKKI_LIITETUNNISTE
import io.swagger.v3.oas.annotations.media.Schema

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty

class LuoLiiteResponse() {}

@Schema(name = "LuoLiiteSuccessResponse")
case class LuoLiiteSuccessResponseImpl(
                                        @(Schema @field)(example = ESIMERKKI_LIITETUNNISTE)
                                        @BeanProperty liiteTunniste: UUID) extends LuoLiiteResponse, LuoLiiteSuccessResponse {

  def this() = {
    this(null)
  }
}

@Schema(name = "LuoLiiteFailureResponse")
case class LuoLiiteFailureResponseImpl(
                                        @(Schema@field)(example = "{ virheet: [ \"Liitteen koko on liian suuri\" ] }")
                                        @BeanProperty virheet: java.util.List[String]) extends LuoLiiteResponse {}
