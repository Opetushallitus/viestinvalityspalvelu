package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.util.LogContext
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array(HEALTHCHECK_PATH))
@RestController
@Tag("5. Healthcheck")
class HealthcheckResource {

  val LOG = LoggerFactory.getLogger(classOf[HealthcheckResource]);

  @GetMapping(path = Array(""))
  def lisaaViesti(): ResponseEntity[String] = {
    LogContext(path = HEALTHCHECK_PATH)(() =>
      LOG.info("healthcheck")
      ResponseEntity.status(HttpStatus.OK).body("OK"))
  }
}