package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.integration.{ONRService, OmatTiedot}
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, RequestMapping, RestController}
import upickle.default.*

@RequestMapping(path = Array(""))
@RestController("RaportointiOmattiedot")
@Tag(
  name = "7. Käyttäjän tiedot",
  description = "Käyttöliittymälle tehty proxy-api organisaatiopalveluun ja oppijanumerorekisteriin")
class OmatTiedotResource {

  val LOG = LoggerFactory.getLogger(classOf[OmatTiedotResource])
  val OnrService = ONRService.apply()

  @GetMapping(path = Array(RaportointiAPIConstants.OMAT_TIEDOT_PATH), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  @Operation(
    summary = "Palauttaa asiointikielen ja nimen",
    description = "Palauttaa käyttäjän asiointikielen, kutsumanimen ja sukunimen",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa asiointikielen ja nimen"),
    ))
  def getOmatTiedot() = {
    LOG.info("Haetaan omat tiedot")
    val securityOperaatiot = new SecurityOperaatiot
    val result = OnrService.haeOmatTiedot(securityOperaatiot.getIdentiteetti())
    result match
      case Left(e) =>
        LOG.error("Omien tietojen haku epäonnistui", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(write(Map("message" -> e.getMessage)))
      case Right(o) =>
        ResponseEntity.status(HttpStatus.OK).body(write[OmatTiedot](o))
  }
}
