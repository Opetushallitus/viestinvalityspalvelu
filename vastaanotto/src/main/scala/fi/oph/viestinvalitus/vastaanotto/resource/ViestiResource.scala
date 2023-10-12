package fi.oph.viestinvalitus.vastaanotto.resource

import fi.oph.viestinvalitus.vastaanotto.model.Viesti
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
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
    description = "Rajoitteita: \n" +
      "- korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja",
    responses = Array(
      new ApiResponse(responseCode = "200", description="Palauttaa vastaanotetun viestin tunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[String], example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")))),
      new ApiResponse(responseCode = "400", description="Palauttaa listan lähetyspyynnössä olevista virheistä", content = Array(new Content(schema = new Schema(implementation = classOf[String], example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")))),
    ))
  def lisaaViesti(@RequestBody viesti: Viesti): ResponseEntity[Viesti] = {
    viesti.kielet.asScala

    ResponseEntity.status(HttpStatus.OK).body(viesti)
  }
}