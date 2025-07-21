package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.util.LogContext
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RequestMapping(path = Array(LahetysAPIConstants.LOGIN_PATH))
@RestController
@Hidden
class LoginResource {

  val LOG = LoggerFactory.getLogger(classOf[LoginResource]);

  @GetMapping(path = Array(""))
  def redirect(response: HttpServletResponse): Unit = {
    LogContext(path = LahetysAPIConstants.LOGIN_PATH)(() =>
      LOG.info("uudelleenohjaus loginin jälkeen")
      response.sendRedirect(LahetysAPIConstants.HEALTHCHECK_PATH))
  }
}