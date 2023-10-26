package fi.oph.viestinvalitus.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitus.vastaanotto.model
import fi.oph.viestinvalitus.vastaanotto.model.{Lahetys, LahetysTunnisteIdentityProvider, LiiteTunnisteIdentityProvider, Viesti, ViestiValidator}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object ViestiConstants {
  final val VIESTI_MAX_SIZE = VIESTI_MAX_SIZE_MB_STR.toInt * 1024 * 1024
  final val VIESTI_MAX_SIZE_MB_STR = "8"

  final val VIESTI_RATELIMIT_VIRHE = "virhe: Liikaa korkean prioriteetin lähetyspyyntöjä"

  final val EXAMPLE_VIESTI_VALIDOINTIVIRHE = "[ \"" + ViestiValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"
}

class ViestiResponse() {}

case class ViestiSuccessResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty lahetysTunniste: String,

  @(Schema @field)(example = "{ \"vallu.vastaanottaja@esimerkki.domain\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\" }")
  @BeanProperty viestiTunnisteet: java.util.Map[String, String]
) extends ViestiResponse {

}

case class ViestiFailureResponse(
  @(Schema @field)(example = ViestiConstants.EXAMPLE_VIESTI_VALIDOINTIVIRHE)
  @BeanProperty validointiVirheet: java.util.List[String]
) extends ViestiResponse {

}

case class ViestiRateLimitResponse(
  @(Schema@field)(example = ViestiConstants.VIESTI_RATELIMIT_VIRHE)
  @BeanProperty virhe: java.util.List[String]
) extends ViestiResponse

@RequestMapping(path = Array("/v2/resource"))
@RestController
@Tag("3. Viesti")
class ViestiResource {

  @Autowired var mapper: ObjectMapper = null

  final val ENDPOINT_VIESTI_DESCRIPTION = "Rajoitteita:\n" +
    "- viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiConstants.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
    "suurempi koko johtaa 400-virheeseen\n" +
    "- korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja\n" +
    "- yksittäinen järjestelmä voi lähettää vain yhden korkean prioriteetin pyynnön joka viides sekunti, " +
    "nopeampi lähetystahti voi johtaa 429-vastaukseen"

  @PutMapping(
    path = Array("/viesti"),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden lähetettävän viestin per vastaanottaja",
    description = ENDPOINT_VIESTI_DESCRIPTION,
    requestBody =
      new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[Viesti])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description="Pyyntö vastaanotettu, palauttaa lähetettävien viestien tunnisteet", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description="Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiFailureResponse])))),
      new ApiResponse(responseCode = "429", description="Liikaa korkean prioriteetin lähetyspyyntöjä", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiRateLimitResponse])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte]): ResponseEntity[ViestiResponse] = {
    val viesti: Viesti = mapper.readValue(viestiBytes, classOf[Viesti])

    val DUMMY_IDENTITY = "järjestelmä1"
    val DUMMY_LIITE_IDENTITY_PROVIDER: LiiteTunnisteIdentityProvider = liiteTunniste => Option.apply(DUMMY_IDENTITY)
    val DUMMY_LAHETYS_IDENTITY_PROVIDER: LahetysTunnisteIdentityProvider = lahetysTunniste => Option.apply(DUMMY_IDENTITY)

    val validointiVirheet = Seq(
      // validoidaan yksittäiset kentät
      ViestiValidator.validateOtsikko(viesti.otsikko),
      ViestiValidator.validateSisalto(viesti.sisalto),
      ViestiValidator.validateSisallonTyyppi(viesti.sisallonTyyppi),
      ViestiValidator.validateKielet(viesti.kielet),
      ViestiValidator.validateLahettavanVirkailijanOID(viesti.lahettavanVirkailijanOid),
      ViestiValidator.validateLahettaja(viesti.lahettaja),
      ViestiValidator.validateVastaanottajat(viesti.vastaanottajat),
      ViestiValidator.validateLiitteidenTunnisteet(viesti.liitteidenTunnisteet, DUMMY_LIITE_IDENTITY_PROVIDER, DUMMY_IDENTITY),
      ViestiValidator.validateLahettavaPalvelu(viesti.lahettavaPalvelu),
      ViestiValidator.validateLahetysTunniste(viesti.lahetysTunniste, DUMMY_LAHETYS_IDENTITY_PROVIDER, DUMMY_IDENTITY),
      ViestiValidator.validatePrioriteetti(viesti.prioriteetti),
      ViestiValidator.validateSailytysAika(viesti.sailytysAika),
      ViestiValidator.validateKayttooikeusRajoitukset(viesti.kayttooikeusRajoitukset),
      ViestiValidator.validateMetadata(viesti.metadata),

      // validoidaan kenttien väliset suhteet
      ViestiValidator.validateKorkeaPrioriteetti(viesti.prioriteetti, viesti.vastaanottajat)
    ).flatten

    if(!validointiVirheet.isEmpty) {
      ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ViestiFailureResponse(validointiVirheet.asJava))
    } else {
      ResponseEntity.status(HttpStatus.OK).body(ViestiSuccessResponse(UUID.randomUUID().toString, java.util.Map.of(viesti.vastaanottajat.get(0).sahkopostiOsoite, UUID.randomUUID().toString)))
    }
  }
}