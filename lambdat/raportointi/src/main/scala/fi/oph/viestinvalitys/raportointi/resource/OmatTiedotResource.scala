package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.integration.ONRService
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
  @GetMapping(path = Array(RaportointiAPIConstants.OMAT_TIEDOT_PATH), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  @Operation(
    summary = "Palauttaa asiointikielen",
    description = "Palauttaa käyttäjän asiointikielen",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa asiointikielen"),
    ))
  def getAsiointikieli() = {
    val result = ONRService.apply().haeAsiointikieli("1.2.246.562.24.99774408952")
    result match
      case Left(e) =>
        LOG.error("Asiointikielen haku epäonnistui", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(write(Map("message" -> e.getMessage)))
      case Right(o) =>
        ResponseEntity.status(HttpStatus.OK).body(write[String](o))
  }
}
