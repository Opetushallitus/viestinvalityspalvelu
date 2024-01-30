package fi.oph.viestinvalitys.raportointi.resource

import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Hidden, Operation}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty

@RequestMapping(path = Array(""))
@RestController("RaportointiLogin")
@Hidden
class LoginResource {

  @GetMapping(path = Array(RaportointiAPIConstants.LOGIN_PATH))
  def redirect(response: HttpServletResponse): Unit = {
    response.sendRedirect("/raportointi")
  }

  // CloudFront ohjaa tämä polun nodelle, joten tätä uudelleenohjausta käytetään vain lokaalisti
  @GetMapping(path = Array("raportointi"))
  def redirectToNodeLocally(response: HttpServletResponse): Unit = {
    response.sendRedirect("http://localhost:3000")
  }

}