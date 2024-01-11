package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kontakti}
import fi.oph.viestinvalitys.util.DbUtil
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysImpl, LahetysMetadata, LahetysValidator, LuoLahetysSuccessResponse, ViestiImpl, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants.*
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
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

@RequestMapping(path = Array(""))
@RestController
@Tag(
  name = "1. Lähetykset",
  description = "Lähetys on joukko viestejä joita voidaan tarkastella yhtenä kokonaisuutena raportoinnissa. Viestit " +
    "liitetään luomisen yhteydessä lähetykseen, joko erikseen tai automaattisesti luotuun.")
class LahetysResource {

  @Autowired var mapper: ObjectMapper = null;

  @PostMapping(
    path = Array(LUO_LAHETYS_PATH),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden lähetyksen",
    description = "",
    requestBody = new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[LahetysImpl])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Pyyntö vastaanotettu, palauttaa lähetystunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoLahetysSuccessResponseImpl])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[LuoLahetysFailureResponseImpl])))),
      new ApiResponse(responseCode = "403", description = LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lisaaLahetys(@RequestBody lahetysBytes: Array[Byte]): ResponseEntity[LuoLahetysResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val lahetys =
      try
        mapper.readValue(lahetysBytes, classOf[LahetysImpl])
      catch
        case e: Exception => return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLahetysFailureResponseImpl(java.util.List.of(VIRHEELLINEN_LAHETYS_JSON_VIRHE)))

    val validointiVirheet = LahetysValidator.validateLahetys(lahetys)
    if(!validointiVirheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLahetysFailureResponseImpl(validointiVirheet.toSeq.asJava))

    val tunniste = KantaOperaatiot(DbUtil.database).tallennaLahetys(
      otsikko                   = lahetys.otsikko.get,
      kayttooikeusRajoitukset   = lahetys.kayttooikeusRajoitukset.toScala.map(r => r.asScala.toSet).getOrElse(Set.empty),
      omistaja                  = securityOperaatiot.getIdentiteetti(),
      lahettavaPalvelu          = lahetys.lahettavaPalvelu.get,
      lahettavanVirkailijanOID  = lahetys.lahettavanVirkailijanOid.toScala,
      lahettaja                 = Kontakti(lahetys.lahettaja.get.getNimi.toScala, lahetys.lahettaja.get.getSahkopostiOsoite.get)
    ).tunniste

    ResponseEntity.status(HttpStatus.OK).body(LuoLahetysSuccessResponseImpl(tunniste))

  @GetMapping(
    path = Array(GET_LAHETYS_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa lähetyksen",
    description = "Palauttaa lähetyksen ja yhteenvedon sen tilasta",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
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

    val lahetyksenOikeudet = kantaOperaatiot.getLahetyksenKayttooikeudet(lahetys.get.tunniste)
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(lahetys.get.tunniste, lahetys.get.otsikko))


  final val ENDPOINT_LUEVASTAANOTTAJAT_DESCRIPTION = "<pre>Vastaanottaja voi olla jossain seuraavista tiloista:\n" +
    "- SKANNAUS:\t\t\todottaa liitteen skannausta\n" +
    "- ODOTTAA:\t\t\tlähetysjonossa\n" +
    "- LAHETYKSESSA:\t\tlähetyksessä\n" +
    "- VIRHE:\t\t\tlähetyksessä tapahtui odottamaton virhe\n" +
    "- LAHETETTY:\t\tlähetetty AWS SES:iin\n" +
    "- DELIVERY:\t\t\ttoimitettu vastaanottajalle\n" +
    "- BOUNCE:\t\t\tesim. vastaanottaja tuntematon tai postilaatikko täynnä\n" +
    "- COMPLAINT:\t\tvastaanottaja on merkannut viestin roskapostiksi\n" +
    "- REJECT:\t\t\tSES kieltäytynyt lähettämästä (SES:in virusskannauksessa positiivinen tulos)\n" +
    "- DELIVERYDELAY:\tlähetys ei toistaiseksi onnistunut (esim. kohdepalvelimeen ei ole saatu yhteyttä)</pre>\n" +
    "\n" +
    "<pre>Sivutuksen toiminta:\n" +
    "- Palautetut vastaanottajat on järjestetty tunnisteen perusteella\n" +
    "- " + ALKAEN_PARAM_NAME + "-parametrin avulla voi määritellä että vastaanottajat palautetaan halutusta vastaanottajasta alkaen (ei-inklusiivinen)\n" +
    "- " + ENINTAAN_PARAM_NAME + "-parametrin avulla voi määritellä että palautetaan enintään n vastaanottajaa (default " + VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR + ")\n" +
    "- vastauksen seuraavat-kentässä on linkki seuraavaan sivulliseen vastaanottajia (jos vastaanottajia ei enää ole, kenttää ei ole määritelty)</pre>\n"

  @GetMapping(
    path = Array(GET_VASTAANOTTAJAT_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    operationId = "getVastaanottajat",
    summary = "Palauttaa viestin vastaanottajien tilat",
    description = ENDPOINT_LUEVASTAANOTTAJAT_DESCRIPTION,
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa vastaanottajien tilat", content = Array(new Content(schema = new Schema(implementation = classOf[VastaanottajatSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = APIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[VastaanottajatFailureResponse])))),
      new ApiResponse(responseCode = "403", description = APIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = APIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueVastaanottajat(
    @Schema(description = "Lähetys jonka vastaanottajat haetaan", example = ESIMERKKI_LAHETYSTUNNISTE)
    @PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String,
    @Schema(description = "Palautetaan vastaanottajia alkaen tästä vastaanottajasta (ei-inklusiivinen)", example = ESIMERKKI_LAHETYSTUNNISTE)
    @RequestParam(name = ALKAEN_PARAM_NAME, required = false) alkaen: Optional[String],
    @Schema(description = "Palautetaan enintään näin monta vastaanottajaa", example = "1", minimum = VASTAANOTTAJAT_ENINTAAN_MIN_STR, maximum = VASTAANOTTAJAT_ENINTAAN_MAX_STR)
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

    val lahetyksenOikeudet = kantaOperaatiot.getLahetyksenKayttooikeudet(lahetys.get.tunniste)
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val vastaanottajat = kantaOperaatiot.getLahetyksenVastaanottajat(uuid.get, alkaenUuid, Option.apply(enintaanInt.getOrElse(VASTAANOTTAJAT_ENINTAAN_DEFAULT)))
    val seuraavatLinkki = {
      if(vastaanottajat.isEmpty || kantaOperaatiot.getLahetyksenVastaanottajat(uuid.get, Option.apply(vastaanottajat.last.tunniste), Option.apply(1)).isEmpty)
        Optional.empty
      else
        val protocol = {
          if("localhost".equals(request.getServerName))
            "http"
          else
            "https"
        }
        val host = s"${protocol}://${request.getServerName}"
        val port = s"${if (request.getServerPort != 443) ":" + request.getServerPort else ""}"
        val path = s"${GET_VASTAANOTTAJAT_PATH.replace(LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste)}"
        val alkaenParam = s"?${ALKAEN_PARAM_NAME}=${vastaanottajat.last.tunniste}"
        val enintaanParam = enintaan.map(v => s"&${ENINTAAN_PARAM_NAME}=${v}").orElse("")
        Optional.of(host + port + path + alkaenParam + enintaanParam)
    }

    ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(
      vastaanottajat.map(vastaanottaja => VastaanottajaResponseImpl(vastaanottaja.tunniste.toString,
        Optional.ofNullable(vastaanottaja.kontakti.nimi.getOrElse(null)), vastaanottaja.kontakti.sahkoposti,
        vastaanottaja.viestiTunniste, vastaanottaja.tila.toString)).asJava, seuraavatLinkki))
}