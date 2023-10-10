package fi.oph.viestinvalitus.vastaanotto

import fi.oph.viestinvalitus.model.Viesti
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array("/v2/resource/viesti"))
@RestController
class ViestiResource {

  @PutMapping(path = Array(""))
  def lisaaViesti(@RequestBody viesti: Viesti): ResponseEntity[Viesti] = {
    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(viesti)
  }

  @GetMapping(path = Array("/authorized"))
  def authorizedTestEndpoint(): ResponseEntity[String] = {
    ResponseEntity.status(HttpStatus.OK).body("OK")
  }

  @GetMapping(path = Array("/notauthorized"))
  def notAuthorizedTestEndpoint(): ResponseEntity[String] = {
    ResponseEntity.status(HttpStatus.OK).body("OK")
  }
}