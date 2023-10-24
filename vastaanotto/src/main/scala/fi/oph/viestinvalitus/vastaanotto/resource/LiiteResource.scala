package fi.oph.viestinvalitus.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object LiiteConstants {
}

case class LiiteResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty liiteTunniste: String) {
}

@RequestMapping(path = Array("/v2/resource/liite"))
@RestController
class LiiteResource {

  @PutMapping(
    path = Array(""),
    consumes = Array(MediaType.APPLICATION_OCTET_STREAM_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Lataa uuden liitetiedoston",
    description = "",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Liite vastaanotettu, palauttaa liitetunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LiiteResponse]))))
    ))
  def lisaaLiite(@RequestBody bytes: Array[Byte]): ResponseEntity[LiiteResponse] = {
    ResponseEntity.status(HttpStatus.OK).body(LiiteResponse(UUID.randomUUID().toString))
  }
}