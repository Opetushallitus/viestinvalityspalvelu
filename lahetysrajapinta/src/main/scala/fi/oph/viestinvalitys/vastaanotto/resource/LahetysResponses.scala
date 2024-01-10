package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.annotation.JsonInclude
import fi.oph.viestinvalitys.vastaanotto.model.{LuoLahetysSuccessResponse, VastaanottajaResponse}
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants.{ESIMERKKI_LAHETYSTUNNISTE, EXAMPLE_LAHETYSTUNNISTE_VALIDOINTIVIRHE, EXAMPLE_OTSIKKO_VALIDOINTIVIRHE, LAHETYSTUNNISTE_INVALID}
import io.swagger.v3.oas.annotations.media.Schema

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty

class LuoLahetysResponse() {
}

@Schema(name = "LuoLahetysSuccessResponse")
case class LuoLahetysSuccessResponseImpl(
                                          @(Schema@field)(example = ESIMERKKI_LAHETYSTUNNISTE)
                                          @BeanProperty lahetysTunniste: UUID) extends LuoLahetysResponse, LuoLahetysSuccessResponse {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null)
  }
}

@Schema(name = "LuoLahetysFailureResponse")
case class LuoLahetysFailureResponseImpl(
                                          @(Schema @field)(example = EXAMPLE_OTSIKKO_VALIDOINTIVIRHE)
                                          @BeanProperty validointiVirheet: java.util.List[String]) extends LuoLahetysResponse {

  def this() =
    this(null)
}

class PalautaLahetysResponse() {}

case class PalautaLahetysSuccessResponse(
                                          @(Schema@field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                          @BeanProperty lahetysTunniste: UUID,
                                          @(Schema@field)(example = "Onnistunut otsikko")
                                          @BeanProperty otsikko: String
                                        ) extends PalautaLahetysResponse

case class PalautaLahetysFailureResponse(
                                          @(Schema@field)(example = LAHETYSTUNNISTE_INVALID)
                                          @BeanProperty virhe: String,
                                        ) extends PalautaLahetysResponse

class VastaanottajatResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajaResponseImpl(
                                  @(Schema@field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                  @BeanProperty tunniste: String,
                                  @(Schema@field)(example = "Vallu Vastaanottaja")
                                  @BeanProperty nimi: Optional[String],
                                  @(Schema@field)(example = "vallu.vastaanottaja@example.com")
                                  @BeanProperty sahkoposti: String,
                                  @(Schema@field)(example = "b4662fcb-a4a0-4747-b4b9-f3e165d9e626")
                                  @BeanProperty viestiTunniste: UUID,
                                  @(Schema@field)(example = "BOUNCE")
                                  @BeanProperty tila: String
                                ) extends VastaanottajaResponse {

  def this() = this(null, null, null, null, null)
}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajatSuccessResponse(
                                          @BeanProperty vastaanottajat: java.util.List[VastaanottajaResponse],
                                          @(Schema@field)(example = "<linkki seuraavaan sivulliseen vastaanottajia>")
                                          @BeanProperty seuraavat: Optional[String],
                                        ) extends VastaanottajatResponse {

  def this() = this(null, null)
}

case class VastaanottajatFailureResponse(
                                          @(Schema@field)(example = EXAMPLE_LAHETYSTUNNISTE_VALIDOINTIVIRHE)
                                          @BeanProperty virheet: util.List[String],
                                        ) extends VastaanottajatResponse
