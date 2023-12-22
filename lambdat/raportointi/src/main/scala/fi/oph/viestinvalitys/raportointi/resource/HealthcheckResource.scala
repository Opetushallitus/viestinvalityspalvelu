package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.resource.APIConstants.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array(HEALTHCHECK_PATH))
@RestController("raportointi/healthcheck")
class HealthcheckResource {

  @GetMapping(path = Array(""))
  def lisaaViesti(): ResponseEntity[String] = {
    ResponseEntity.status(HttpStatus.OK).body("OK")
  }
}