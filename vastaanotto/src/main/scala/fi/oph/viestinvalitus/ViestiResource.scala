package fi.oph.viestinvalitus

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, PutMapping, RequestBody, RequestMapping, ResponseBody, RestController}

import scala.beans.BeanProperty

@RequestMapping(path = Array("/v2/resource/viesti"))
@RestController
class ViestiResource {

  @PutMapping(path = Array(""))
  def lisaaViesti(@RequestBody viesti: Viesti): Viesti = {
    viesti
  }
}

class Viesti(@BeanProperty heading: String, @BeanProperty content: String) {

}
