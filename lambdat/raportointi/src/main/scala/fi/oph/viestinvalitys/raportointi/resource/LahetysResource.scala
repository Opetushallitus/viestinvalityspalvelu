package fi.oph.viestinvalitys.raportointi.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.raportointi.resource.APIConstants.*
import fi.oph.viestinvalitys.raportointi.security.{SecurityConstants, SecurityOperaatiot}
import fi.oph.viestinvalitys.util.DbUtil
import io.swagger.v3.oas.annotations.links.{Link, LinkParameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ScanCursor
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize

import java.util.{Optional, UUID}
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.Database
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api.*

import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class PalautaLahetysResponse() {}

case class PalautaLahetysSuccessResponse(
  @BeanProperty lahetysTunniste: String,
  @BeanProperty otsikko: String
) extends PalautaLahetysResponse

case class PalautaLahetysFailureResponse(
  @BeanProperty virhe: String,
) extends PalautaLahetysResponse

class VastaanottajatResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajaResponse(
  @BeanProperty tunniste: String,
  @BeanProperty nimi: Optional[String],
  @BeanProperty sahkoposti: String,
  @BeanProperty viestiTunniste: String,
  @BeanProperty tila: String
)

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajatSuccessResponse(
  @BeanProperty vastaanottajat: java.util.List[VastaanottajaResponse],
  @BeanProperty seuraavat: Optional[String],
) extends VastaanottajatResponse

case class VastaanottajatFailureResponse(
  @BeanProperty virheet: util.List[String],
) extends VastaanottajatResponse

@RequestMapping(path = Array(""))
@RestController("raportointi/lahetys")
class LahetysResource {

  @GetMapping(
    path = Array(GET_LAHETYS_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  def lueLahetys(@PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String): ResponseEntity[PalautaLahetysResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if (!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = ParametriUtil.asUUID(lahetysTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetysFailureResponse(LAHETYSTUNNISTE_INVALID))

    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val lahetys = kantaOperaatiot.getLahetys(uuid.get)
    if (lahetys.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val lahetyksenOikeudet: Set[String] = Set.empty // ei vielä toteutettu
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(lahetys.get.tunniste.toString, lahetys.get.otsikko))


  @GetMapping(
    path = Array(GET_VASTAANOTTAJAT_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  def lueVastaanottajat(
    @PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String,
    @RequestParam(name = ALKAEN_PARAM_NAME, required = false) alkaen: Optional[String],
    @RequestParam(name = ENINTAAN_PARAM_NAME, required = false) enintaan: Optional[String],
                         request: HttpServletRequest
  ): ResponseEntity[VastaanottajatResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if (!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    // validoidaan parametrit
    val uuid = ParametriUtil.asUUID(lahetysTunniste)
    val alkaenUuid = ParametriUtil.asUUID(alkaen)
    val enintaanInt = ParametriUtil.asInt(enintaan)

    var virheet: Seq[String] = Seq.empty
    if (uuid.isEmpty) virheet = virheet.appended(APIConstants.LAHETYSTUNNISTE_INVALID)
    if (alkaen.isPresent && alkaenUuid.isEmpty) virheet = virheet.appended(APIConstants.ALKAEN_TUNNISTE_INVALID)
    if (enintaan.isPresent &&
      (enintaanInt.isEmpty || enintaanInt.get < VASTAANOTTAJAT_ENINTAAN_MIN || enintaanInt.get > VASTAANOTTAJAT_ENINTAAN_MAX))
      virheet = virheet.appended(ENINTAAN_INVALID)
    if(!virheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(VastaanottajatFailureResponse(virheet.asJava))

    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

    val lahetys = kantaOperaatiot.getLahetys(uuid.get)
    if (lahetys.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val lahetyksenOikeudet: Set[String] = Set.empty // ei vielä toteutettu
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val vastaanottajat = kantaOperaatiot.getLahetyksenVastaanottajat(uuid.get, alkaenUuid, Option.apply(enintaanInt.getOrElse(VASTAANOTTAJAT_ENINTAAN_DEFAULT)))
    val seuraavatLinkki = {
      if(vastaanottajat.isEmpty || kantaOperaatiot.getLahetyksenVastaanottajat(uuid.get, Option.apply(vastaanottajat.last.tunniste), Option.apply(1)).isEmpty)
        Optional.empty
      else
        val host = s"https://${request.getServerName}"
        val port = s"${if (request.getServerPort != 443) ":" + request.getServerPort else ""}"
        val path = s"${APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste)}"
        val alkaenParam = s"?${ALKAEN_PARAM_NAME}=${vastaanottajat.last.tunniste}"
        val enintaanParam = enintaan.map(v => s"&${ENINTAAN_PARAM_NAME}=${v}").orElse("")
        Optional.of(host + port + path + alkaenParam + enintaanParam)
    }

    ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(
      vastaanottajat.map(vastaanottaja => VastaanottajaResponse(vastaanottaja.tunniste.toString,
        Optional.ofNullable(vastaanottaja.kontakti.nimi.getOrElse(null)), vastaanottaja.kontakti.sahkoposti,
        vastaanottaja.viestiTunniste.toString, vastaanottaja.tila.toString)).asJava, seuraavatLinkki))
}