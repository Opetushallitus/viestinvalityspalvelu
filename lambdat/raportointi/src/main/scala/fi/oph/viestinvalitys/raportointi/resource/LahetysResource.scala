package fi.oph.viestinvalitys.raportointi.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import fi.oph.viestinvalitys.raportointi.security.{SecurityConstants, SecurityOperaatiot}
import fi.oph.viestinvalitys.util.DbUtil
import io.swagger.v3.oas.annotations.links.{Link, LinkParameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory
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

import java.time.Instant
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


class PalautaLahetyksetResponse() {}

case class VastaanottajatTilassa(
  @BeanProperty vastaanottotila: String,
  @BeanProperty vastaanottajaLkm: Int
)

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class PalautaLahetyksetSuccessResponse(
  @BeanProperty lahetykset: java.util.List[PalautaLahetysSuccessResponse],
  @BeanProperty seuraavat: Optional[String]
) extends PalautaLahetyksetResponse

case class PalautaLahetyksetFailureResponse(
  @BeanProperty virhe: util.List[String],
) extends PalautaLahetyksetResponse
class PalautaLahetysResponse() {}
@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class PalautaLahetysSuccessResponse(
  @BeanProperty lahetysTunniste: String,
  @BeanProperty otsikko: String,
  @BeanProperty omistaja: String,
  @BeanProperty lahettavaPalvelu: String,
  @BeanProperty lahettavanVirkailijanOID: String,
  @BeanProperty lahettajanNimi: String,
  @BeanProperty lahettajanSahkoposti: String,
  @BeanProperty replyTo: String,
  @BeanProperty luotu: String,
  @BeanProperty tilat: java.util.List[VastaanottajatTilassa]
) extends PalautaLahetysResponse

case class PalautaLahetysFailureResponse(
  @BeanProperty virhe: String,
) extends PalautaLahetysResponse

class PalautaViestitResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class ViestiResponse(
  @BeanProperty lahetysTunniste: String,
  @BeanProperty lahettavapalvelu: String,
  @BeanProperty otsikko: String,
  @BeanProperty tunniste: String,
  @BeanProperty sisalto: String,
  @BeanProperty sisallonTyyppi: String,
  @BeanProperty lahettavanVirkailijanOID: String,
  @BeanProperty replyTo: String,
  @BeanProperty omistaja: String
)

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class PalautaViestitSuccessResponse(
  @BeanProperty viestit: java.util.List[ViestiResponse],
) extends PalautaViestitResponse

case class PalautaViestitFailureResponse(
  @BeanProperty virhe: String,
) extends PalautaViestitResponse

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
@RestController("RaportointiLahetys")
@Tag(
  name = "4. Raportointi",
  description = "Lähetys on joukko viestejä joita voidaan tarkastella yhtenä kokonaisuutena raportoinnissa. Viestit " +
    "liitetään luomisen yhteydessä lähetykseen, joko erikseen tai automaattisesti luotuun.")
class LahetysResource {

  val LOG = LoggerFactory.getLogger(classOf[LahetysResource]);

  @GetMapping(
    path = Array(GET_VIESTIT_LAHETYSTUNNISTEELLA_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa listan viestejä lähetystunnisteella",
    description = "Palauttaa lähetyksen viestien tiedot raportointikäyttöliittymälle",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestit", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestitSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueLahetyksenViestit(@PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String): ResponseEntity[PalautaViestitResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if (!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = ParametriUtil.asUUID(lahetysTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaViestitFailureResponse(LAHETYSTUNNISTE_INVALID))

    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val lahetys = kantaOperaatiot.getLahetys(uuid.get)
    if (lahetys.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val lahetyksenOikeudet: Set[String] = Set.empty // ei vielä toteutettu
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val viestit = kantaOperaatiot.getLahetyksenViestit(uuid.get)
    ResponseEntity.status(HttpStatus.OK).body(PalautaViestitSuccessResponse(
      viestit.map(viesti => ViestiResponse(
        lahetys.get.tunniste.toString, lahetys.get.lahettavaPalvelu, lahetys.get.otsikko,
        viesti.tunniste.toString, viesti.sisalto, viesti.sisallonTyyppi.toString,
        viesti.lahettavanVirkailijanOID.getOrElse(""), viesti.replyTo.getOrElse(""), viesti.omistaja
      )).asJava
    ))

  @GetMapping(
    path = Array(GET_LAHETYKSET_LISTA_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa listan lähetysten raportointitietoja",
    description = "Palauttaa lähetyksien tiedot raportointikäyttöliittymälle listauksena",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa lähetykset", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetyksetSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetyksetFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueLahetykset(@RequestParam(name = ALKAEN_PARAM_NAME, required = false) alkaen: Optional[String],
                    @RequestParam(name = ENINTAAN_PARAM_NAME, required = false) enintaan: Optional[String],
                    request: HttpServletRequest): ResponseEntity[PalautaLahetyksetResponse] =
    // TODO tarkempi käyttöoikeusrajaus/suodatus
    LOG.info("HEADEREITA")
    LOG.info(request.getHeader("Caller-Id"))
    val securityOperaatiot = new SecurityOperaatiot
    if (!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    // validoidaan parametrit
    val alkaenAika = ParametriUtil.asInstant(alkaen)
    val enintaanInt = ParametriUtil.asInt(enintaan)

    var virheet: Seq[String] = Seq.empty
    if (alkaen.isPresent && alkaenAika.isEmpty) virheet = virheet.appended(APIConstants.ALKAEN_AIKA_TUNNISTE_INVALID)
    if (enintaan.isPresent &&
      (enintaanInt.isEmpty || enintaanInt.get < LAHETYKSET_ENINTAAN_MIN || enintaanInt.get > LAHETYKSET_ENINTAAN_MAX))
      virheet = virheet.appended(LAHETYKSET_ENINTAAN_INVALID)
    if (!virheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetyksetFailureResponse(virheet.asJava))


    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val lahetykset = kantaOperaatiot.getLahetykset(alkaenAika,enintaanInt)
    if (lahetykset.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val lahetysStatukset = kantaOperaatiot.getLahetystenVastaanottotilat(lahetykset.map(_.tunniste))

    val seuraavatLinkki = {
      if(lahetykset.isEmpty || kantaOperaatiot.getLahetykset(Option.apply(lahetykset.last.luotu), Option.apply(1)).isEmpty)
        Optional.empty
      else
        val host = s"https://${request.getServerName}"
        val port = s"${if (request.getServerPort != 443) ":" + request.getServerPort else ""}"
        val path = s"${APIConstants.GET_LAHETYKSET_LISTA_PATH}"
        val alkaenParam = s"?${ALKAEN_PARAM_NAME}=${lahetykset.last.luotu}"
        val enintaanParam = enintaan.map(v => s"&${ENINTAAN_PARAM_NAME}=${v}").orElse("")
        Optional.of(host + port + path + alkaenParam + enintaanParam)

    }
    // TODO sivutus edellisiin?
    ResponseEntity.status(HttpStatus.OK).body(PalautaLahetyksetSuccessResponse(
      lahetykset.map(lahetys => PalautaLahetysSuccessResponse(
        lahetys.tunniste.toString, lahetys.otsikko, lahetys.omistaja, lahetys.lahettavaPalvelu, lahetys.lahettavanVirkailijanOID.getOrElse(""),
        lahetys.lahettaja.nimi.getOrElse(""), lahetys.lahettaja.sahkoposti, lahetys.replyTo.getOrElse(""), lahetys.luotu.toString,
        lahetysStatukset.getOrElse(lahetys.tunniste, Seq.empty).map(status => VastaanottajatTilassa(status._1, status._2)).asJava)).asJava, seuraavatLinkki))

  @GetMapping(
    path = Array(GET_LAHETYS_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa yksittäisen lähetyksen raportointitiedot",
    description = "Palauttaa lähetyksen tiedot raportointikäyttöliittymälle",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa lähetyksen", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
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

    ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(
      lahetys.get.tunniste.toString, lahetys.get.otsikko, lahetys.get.omistaja, lahetys.get.lahettavaPalvelu, lahetys.get.lahettavanVirkailijanOID.getOrElse(""),
      lahetys.get.lahettaja.nimi.getOrElse(""), lahetys.get.lahettaja.sahkoposti, lahetys.get.replyTo.getOrElse(""), lahetys.get.luotu.toString, Seq.empty.asJava))


  @GetMapping(
    path = Array(GET_VASTAANOTTAJAT_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa yksittäisen lähetyksen vastaanottajatiedot",
    description = "Palauttaa lähetyksen vastaanottajatiedot, mahdollisuus rajata aikavälillä",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa lähetyksen vastaanottajat", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
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
    if (alkaen.isPresent && alkaenUuid.isEmpty) virheet = virheet.appended(APIConstants.ALKAEN_UUID_TUNNISTE_INVALID)
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