package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.*

@RequestMapping(path = Array(""))
@RestController("RaportointiHealthCheck")
class HealthcheckResource {

  @GetMapping(path = Array(RaportointiAPIConstants.HEALTHCHECK_PATH))
  def healthcheck(): ResponseEntity[String] = {
    ResponseEntity.status(HttpStatus.OK).body("OK")
  }
}