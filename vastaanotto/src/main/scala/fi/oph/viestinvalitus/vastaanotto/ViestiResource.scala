package fi.oph.viestinvalitus.vastaanotto

import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

@RequestMapping(path = Array("/v2/resource/viesti"))
@RestController
class ViestiResource {

  @PutMapping(path = Array(""))
  @Operation(
    summary = "Luo uuden lähetettävän viestin",
    description = "Kuvaus",
    responses = Array(
      new ApiResponse(responseCode = "OK", content = Array(new Content(schema = new Schema(implementation = classOf[String]))))
    ))
  def lisaaViesti(@RequestBody viesti: Viesti): ResponseEntity[Viesti] = {
    viesti.kielet.asScala

    ResponseEntity.status(HttpStatus.OK).body(viesti)
  }
}