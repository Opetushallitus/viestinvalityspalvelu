package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.integration.{Organisaatio, OrganisaatioClient, OrganisaatioHierarkia}
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, RequestMapping, RestController}
import upickle.default.*


@RequestMapping(path = Array(""))
@RestController("RaportointiOrganisaatiot")
@Tag(
  name = "6. Organisaatiot",
  description = "Käyttöliittymälle tehty proxy-api organisaatiopalveluun")
class OrganisaatioResource {

  @GetMapping(path = Array(RaportointiAPIConstants.ORGANISAATIOT_PATH), produces = Array(MediaType.APPLICATION_JSON_VALUE))
  @Operation(
    summary = "Palauttaa organisaatiohierarkian",
    description = "Palauttaa käyttäjän oikeuksien mukaisen organisaatiohierarkian",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa organisaatiohierarkian"),
    ))
  def getOrganisaatioHierarkia() = {
    val orgs = OrganisaatioClient.getOrganisaatioHierarkia()
    ResponseEntity.status(HttpStatus.OK).body(write[List[Organisaatio]](orgs))
  }

}