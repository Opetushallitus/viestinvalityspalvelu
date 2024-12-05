package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.integration.{Organisaatio, OrganisaatioHierarkia, OrganisaatioService}
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, RequestMapping, RestController}
import upickle.default.*

@RequestMapping(path = Array(""))
@RestController("RaportointiOrganisaatiot")
@Tag(
  name = "6. Organisaatiot",
  description = "Käyttöliittymälle tehty proxy-api organisaatiopalveluun")
class OrganisaatioResource {

  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioResource])

  @GetMapping(path = Array(RaportointiAPIConstants.ORGANISAATIOT_OIKEUDET_PATH), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  @Operation(
    summary = "Palauttaa käyttäjän organisaatiot",
    description = "Palauttaa käyttäjän käyttöoikeuksien organisaatiot",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa listan organisaatio-oideja"),
    ))
  def getOrganisaatiot(request: HttpServletRequest) = {
    LOG.debug("Haetaan käyttöoikeuksien organisaatiot")
    val securityOperaatiot = new SecurityOperaatiot(httpSession = request.getSession(false))
    try
      ResponseEntity.status(HttpStatus.OK).body(write[List[String]](securityOperaatiot.getCasOrganisaatiot().toList))
    catch
      case e: Exception =>
        LOG.error("Organisaatio-oikeuksien haku epäonnistui", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(write(Map("message" -> e.getMessage)))
  }
}