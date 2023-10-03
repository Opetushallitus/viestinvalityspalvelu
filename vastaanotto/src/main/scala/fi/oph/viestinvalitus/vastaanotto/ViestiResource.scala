package fi.oph.viestinvalitus.vastaanotto

import fi.oph.viestinvalitus.vastaanotto.{SQSService, Viesti}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array("/viestinvalituspalvelu/v2/resource/viesti"))
@RestController
class ViestiResource {

  //@Autowired
  //var sqsService: SQSService = null

  @PutMapping(path = Array(""))
  def lisaaViesti(@RequestBody viesti: Viesti): ResponseEntity[Viesti] = {
    //sqsService.sendMessage("test message")
    ResponseEntity.status(400).body(viesti)
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

class ResponseContainer[A](@BeanProperty statusCode: String, @BeanProperty body: A)

class Viesti(@BeanProperty heading: String, @BeanProperty content: String) {

  def this() = {
    this(null, null)
  }
}
