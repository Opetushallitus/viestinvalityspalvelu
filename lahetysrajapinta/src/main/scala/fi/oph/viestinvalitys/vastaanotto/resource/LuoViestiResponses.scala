package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.vastaanotto.model.LuoViestiSuccessResponse
import io.swagger.v3.oas.annotations.media.Schema

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty

class LuoViestiResponse() {}

@Schema(name = "LuoViestiSuccessResponse")
case class LuoViestiSuccessResponseImpl(
                                         @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                         @BeanProperty viestiTunniste: UUID,
                                         @(Schema@field)(example = "5b4501ec-3298-4064-8868-262b55fdce9a")
                                         @BeanProperty lahetysTunniste: UUID
                                       ) extends LuoViestiResponse, LuoViestiSuccessResponse {

  def this() = {
    this(null, null)
  }
}

@Schema(name = "LuoViestiFailureResponse")
case class LuoViestiFailureResponseImpl(
                                         @(Schema @field)(example = APIConstants.EXAMPLE_OTSIKKO_VALIDOINTIVIRHE)
                                         @BeanProperty validointiVirheet: java.util.List[String]
                                       ) extends LuoViestiResponse

@Schema(name = "LuoViestiRateLimitResponse")
case class LuoViestiRateLimitResponseImpl(
                                           @(Schema@field)(example = APIConstants.VIESTI_RATELIMIT_VIRHE)
                                           @BeanProperty virhe: java.util.List[String]
                                         ) extends LuoViestiResponse

class PalautaViestiResponse() {}

case class PalautaViestiSuccessResponse(
                                         @(Schema@field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                         @BeanProperty viestiTunniste: UUID,
                                         @(Schema@field)(example = "Onnistunut otsikko")
                                         @BeanProperty otsikko: String
                                       ) extends PalautaViestiResponse

case class PalautaViestiFailureResponse(
                                         @(Schema@field)(example = APIConstants.VIESTITUNNISTE_INVALID)
                                         @BeanProperty virhe: String,
                                       ) extends PalautaViestiResponse
