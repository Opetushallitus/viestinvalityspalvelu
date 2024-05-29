package fi.oph.viestinvalitys.raportointi.resource

import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RequestMapping(path = Array(""))
@RestController("RaportointiLogin")
@Hidden
class LoginResource {

  val LOG = LoggerFactory.getLogger(classOf[LoginResource])
  @GetMapping(path = Array(RaportointiAPIConstants.LOGIN_PATH))
  def redirect(response: HttpServletResponse): Unit = {
    LOG.info("Tehdään uudelleenohjats raportointikäliin")
    response.sendRedirect("/raportointi")
  }

  // CloudFront ohjaa tämä polun nodelle, joten tätä uudelleenohjausta käytetään vain lokaalisti
  @GetMapping(path = Array("raportointi"))
  def redirectToNodeLocally(response: HttpServletResponse): Unit = {
    response.sendRedirect("http://localhost:3000")
  }

}