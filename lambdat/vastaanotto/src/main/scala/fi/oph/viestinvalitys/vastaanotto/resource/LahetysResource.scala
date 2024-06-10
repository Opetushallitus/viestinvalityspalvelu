package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.vm.sade.auditlog.Changes
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kontakti, Prioriteetti}
import fi.oph.viestinvalitys.util.{DbUtil, LogContext}
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{LahetysImpl, LahetysValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants.*
import fi.oph.viestinvalitys.vastaanotto.security.SecurityOperaatiot
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.{RequestContextHolder, ServletRequestAttributes}

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

@RequestMapping(path = Array(""))
@RestController
@Tag(
  name = "1. Lähetykset",
  description = "Lähetys on joukko viestejä joita voidaan tarkastella yhtenä kokonaisuutena raportoinnissa. Viestit " +
    "liitetään luomisen yhteydessä lähetykseen, joko erikseen tai automaattisesti luotuun.")
class LahetysResource {

  val LOG = LoggerFactory.getLogger(classOf[LahetysResource]);

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
    LogContext(path = LUO_LAHETYS_PATH, identiteetti = securityOperaatiot.getIdentiteetti())(() =>
      try
        Right(None)
          .flatMap(_ =>
            // tarkastetaan lähetysoikeus
            if (!securityOperaatiot.onOikeusLahettaa())
              LOG.warn("Lähetysoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // deserialisoidaan lähetys
            try
              Right(mapper.readValue(lahetysBytes, classOf[LahetysImpl]))
            catch
              case e: Exception =>
                LOG.warn("Lähetyksen deserialisointi epäonnistui", e)
                Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLahetysFailureResponseImpl(java.util.List.of(VIRHEELLINEN_LAHETYS_JSON_VIRHE)))))
          .flatMap(lahetys =>
            // validoidaan lähetys
            val validointiVirheet = LahetysValidator.validateLahetys(lahetys)
            if (!validointiVirheet.isEmpty)
              LOG.warn("Lähetyksessä on validointivirheitä: " + validointiVirheet.mkString(", "))
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLahetysFailureResponseImpl(validointiVirheet.toSeq.asJava)))
            else
              Right(lahetys))
          .map(lahetys =>
            // tallennetaan lähetys
            val lahetysEntiteetti = KantaOperaatiot(DbUtil.database).tallennaLahetys(
              otsikko = lahetys.otsikko.get,
              omistaja = securityOperaatiot.getIdentiteetti(),
              lahettavaPalvelu = lahetys.lahettavaPalvelu.get,
              lahettavanVirkailijanOID = lahetys.lahettavanVirkailijanOid.toScala,
              lahettaja = Kontakti(lahetys.lahettaja.get.getNimi.toScala, lahetys.lahettaja.get.getSahkopostiOsoite.get),
              replyTo = lahetys.replyTo.toScala,
              prioriteetti = Prioriteetti.valueOf(lahetys.prioriteetti.get.toUpperCase),
              sailytysAika = lahetys.sailytysaika.get
            )
            LogContext(lahetysTunniste = lahetysEntiteetti.tunniste.toString)(() => LOG.info("Luotiin uusi lähetys"))
            val user = AuditLog.getUser(RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
            AuditLog.logCreate(user, Map("lahetysTunniste" -> lahetysEntiteetti.tunniste.toString), AuditOperation.CreateLahetys, lahetysEntiteetti)
            lahetysEntiteetti)
          .map(lahetysEntiteetti =>
            ResponseEntity.status(HttpStatus.OK).body(LuoLahetysSuccessResponseImpl(lahetysEntiteetti.tunniste)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[LuoLahetysResponse]]
      catch
        case e: Exception =>
          LOG.error("Lähetyksen luonti epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(LuoLahetysFailureResponseImpl(Seq(LahetysAPIConstants.LAHETYKSEN_LUONTI_EPAONNISTUI).asJava))
  )

  @GetMapping(
    path = Array(GET_LAHETYS_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa lähetyksen",
    description = "Palauttaa lähetyksen ja yhteenvedon sen tilasta",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa lähetyksen", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueLahetys(@PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String): ResponseEntity[PalautaLahetysResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    LogContext(path = GET_LAHETYS_PATH, identiteetti = securityOperaatiot.getIdentiteetti(), lahetysTunniste = lahetysTunniste)(() =>
      try
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

        Right(None)
          .flatMap(_ =>
            if(!securityOperaatiot.onOikeusKatsella())
              LOG.warn("Katseluoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            val uuid = ParametriUtil.asUUID(lahetysTunniste)
            if (uuid.isEmpty)
              LOG.warn("Lähetystunniste ei ole validi tunniste")
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetysFailureResponse(LAHETYSTUNNISTE_INVALID)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            val lahetys = kantaOperaatiot.getLahetys(tunniste)
            if(lahetys.isEmpty)
              LOG.warn("Lähetystunnistetta ei ole kannassa")
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(lahetys.get))
          .flatMap(lahetys =>
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja))
              LOG.warn("Katseluoikeus lähetykseen puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .map(lahetys =>
            LOG.info("Palautetaan lähetys")
            ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(lahetys.tunniste, lahetys.otsikko)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[PalautaLahetysResponse]]
      catch
        case e: Exception =>
          LOG.error("Lähetyksen lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PalautaLahetysFailureResponse(LahetysAPIConstants.LAHETYKSEN_LUKEMINEN_EPAONNISTUI)))

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
      new ApiResponse(responseCode = "400", description = LahetysAPIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[VastaanottajatFailureResponse])))),
      new ApiResponse(responseCode = "403", description = LahetysAPIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = LahetysAPIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
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
    LogContext(lahetysTunniste = lahetysTunniste, identiteetti = securityOperaatiot.getIdentiteetti())(() =>
      try
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

        Right(None)
          .flatMap(_ =>
            // tarkistetaan katseluoikeus
            if (!securityOperaatiot.onOikeusKatsella())
              LOG.warn("Katseluoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // validoidaan parametrit
            val uuid = ParametriUtil.asUUID(lahetysTunniste)
            val alkaenUuid = ParametriUtil.asUUID(alkaen)
            val enintaanInt = ParametriUtil.asInt(enintaan)

            val virheet = Some(Seq.empty.asInstanceOf[Seq[String]])
              .map(virheet =>
                if (uuid.isEmpty) virheet.appended(LahetysAPIConstants.LAHETYSTUNNISTE_INVALID) else virheet)
              .map(virheet =>
                if (alkaen.isPresent && alkaenUuid.isEmpty) virheet.appended(LahetysAPIConstants.ALKAEN_TUNNISTE_INVALID) else virheet)
              .map(virheet =>
                if (enintaan.isPresent &&
                  (enintaanInt.isEmpty || enintaanInt.get < VASTAANOTTAJAT_ENINTAAN_MIN || enintaanInt.get > VASTAANOTTAJAT_ENINTAAN_MAX))
                    virheet.appended(ENINTAAN_INVALID) else virheet).get

            if (!virheet.isEmpty)
              LOG.warn("Vastaanottajien lukeminen epäonnistui, pyyntö on virheellinen: " + virheet.mkString(", "))
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(VastaanottajatFailureResponse(virheet.asJava)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            // haetaan lähetys
            val lahetys = kantaOperaatiot.getLahetys(tunniste)
            if (lahetys.isEmpty)
              LOG.warn("Lähetystunnistetta ei ole kannassa")
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(lahetys.get))
          .flatMap(lahetys =>
            // tarkistetaan lukuoikeus lähetykseen
            val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja)
            if (!onLukuOikeudet)
              LOG.warn("Katseluoikeus lähetykseen puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .map(lahetys =>
            // haetaan vastaanottajat
            val alkaenUuid = ParametriUtil.asUUID(alkaen)
            val enintaanInt = ParametriUtil.asInt(enintaan)
            val vastaanottajat = kantaOperaatiot.getLahetyksenVastaanottajat(lahetys.tunniste, alkaenUuid, Option.apply(enintaanInt.getOrElse(VASTAANOTTAJAT_ENINTAAN_DEFAULT)))
            val seuraavatLinkki = {
              if (vastaanottajat.isEmpty || kantaOperaatiot.getLahetyksenVastaanottajat(lahetys.tunniste, Option.apply(vastaanottajat.last.tunniste), Option.apply(1)).isEmpty)
                Optional.empty
              else
                val protocol = {
                  if ("localhost".equals(request.getServerName))
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

            LOG.info("Palautetaan vastaanottajat lähetykselle " + lahetysTunniste)
            ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(
              vastaanottajat.map(vastaanottaja => VastaanottajaResponseImpl(vastaanottaja.tunniste.toString,
                Optional.ofNullable(vastaanottaja.kontakti.nimi.getOrElse(null)), vastaanottaja.kontakti.sahkoposti,
                vastaanottaja.viestiTunniste, vastaanottaja.tila.toString)).asJava, seuraavatLinkki)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[VastaanottajatResponse]]
      catch
        case e: Exception =>
          LOG.error("Vastaanottajien lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(VastaanottajatFailureResponse(Seq(LahetysAPIConstants.VASTAANOTTAJIEN_LUKEMINEN_EPAONNISTUI).asJava)))
}