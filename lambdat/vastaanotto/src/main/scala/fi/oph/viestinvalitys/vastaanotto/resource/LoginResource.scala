package fi.oph.viestinvalitys.vastaanotto.resource

import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Hidden, Operation}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array("/login"))
@RestController
@Hidden
class LoginResource {

  @GetMapping(path = Array(""))
  def redirect(response: HttpServletResponse): Unit = {
    response.sendRedirect(APIConstants.HEALTHCHECK_PATH)
  }
}