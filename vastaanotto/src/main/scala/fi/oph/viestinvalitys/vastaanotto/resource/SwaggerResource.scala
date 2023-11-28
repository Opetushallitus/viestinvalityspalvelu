package fi.oph.viestinvalitys.vastaanotto.resource

import io.swagger.v3.oas.annotations.{Hidden, Operation}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array("/swagger"))
@RestController
@Hidden
class SwaggerResource {

  @GetMapping(path = Array(""))
  def redirect(response: HttpServletResponse): Unit = {
    response.sendRedirect("/static/swagger-ui/index.html")
  }
}