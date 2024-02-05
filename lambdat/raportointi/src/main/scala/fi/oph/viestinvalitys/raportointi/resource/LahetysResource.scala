package fi.oph.viestinvalitys.raportointi.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import fi.oph.viestinvalitys.raportointi.security.{SecurityConstants, SecurityOperaatiot}
import fi.oph.viestinvalitys.util.{DbUtil, LogContext}
import io.swagger.v3.oas.annotations.links.{Link, LinkParameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
  @BeanProperty seuraavatAlkaen: Optional[String]
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
                    @RequestParam(name = VASTAANOTTAJA_PARAM_NAME, required = false) vastaanottajanEmail: Optional[String],
                    request: HttpServletRequest): ResponseEntity[PalautaLahetyksetResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val emailRegex = "^[^\\s,@]+@(([a-zA-Z\\-0-9])+\\.)+([a-zA-Z\\-0-9]){2,}$".r
    try
      Right(None)
        .flatMap(_ =>
          // tarkistetaan lukuoikeus
          if (!securityOperaatiot.onOikeusKatsella())
            Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
          else
            Right(None))
        .flatMap(_ =>
          // validoidaan parametrit
          val alkaenAika = ParametriUtil.asInstant(alkaen)
          val enintaanInt = ParametriUtil.asInt(enintaan)

          val virheet = Some(Seq.empty.asInstanceOf[Seq[String]])
            .map(virheet =>
                if (alkaen.isPresent && alkaenAika.isEmpty) virheet.appended(RaportointiAPIConstants.ALKAEN_AIKA_TUNNISTE_INVALID) else virheet)
            .map(virheet =>
              if (enintaan.isPresent && (enintaanInt.isEmpty || enintaanInt.get < LAHETYKSET_ENINTAAN_MIN || enintaanInt.get > LAHETYKSET_ENINTAAN_MAX))
                virheet.appended(LAHETYKSET_ENINTAAN_INVALID) else virheet)
            .map(virheet =>
              if (vastaanottajanEmail.isPresent && !emailRegex.matches(vastaanottajanEmail.get()))
                virheet.appended(VASTAANOTTAJA_INVALID) else virheet)
            .get
          if (!virheet.isEmpty)
            Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetyksetFailureResponse(virheet.asJava)))
          else
            Right((alkaenAika, enintaanInt)))
        .flatMap((alkaenAika, enintaanInt) =>
          val lahetykset = kantaOperaatiot.getLahetykset(alkaenAika, enintaanInt, securityOperaatiot.getKayttajanOikeudet(), vastaanottajanEmail.orElse(""))
          if (lahetykset.isEmpty)
            // on ok tilanne että haku ei palauta tuloksia
            Left(ResponseEntity.status(HttpStatus.OK).body(PalautaLahetyksetSuccessResponse(Seq.empty.asJava,Optional.empty)))
          else
            val lahetysStatukset = kantaOperaatiot.getLahetystenVastaanottotilat(lahetykset.map(_.tunniste), securityOperaatiot.getKayttajanOikeudet())

            val seuraavatAlkaen = {
              if (lahetykset.isEmpty || kantaOperaatiot.getLahetykset(Option.apply(lahetykset.last.luotu), Option.apply(1), securityOperaatiot.getKayttajanOikeudet(), vastaanottajanEmail.orElse("")).isEmpty)
                Optional.empty
              else
                Optional.of(lahetykset.last.luotu.toString)
            }
            // TODO sivutus edellisiin?
            Right(ResponseEntity.status(HttpStatus.OK).body(PalautaLahetyksetSuccessResponse(
              lahetykset.map(lahetys => PalautaLahetysSuccessResponse(
                lahetys.tunniste.toString, lahetys.otsikko, lahetys.omistaja, lahetys.lahettavaPalvelu, lahetys.lahettavanVirkailijanOID.getOrElse(""),
                lahetys.lahettaja.nimi.getOrElse(""), lahetys.lahettaja.sahkoposti, lahetys.replyTo.getOrElse(""), lahetys.luotu.toString,
                lahetysStatukset.getOrElse(lahetys.tunniste, Seq.empty).map(status => VastaanottajatTilassa(status._1, status._2)).asJava)).asJava, seuraavatAlkaen))))
        .fold(e => e, r => r).asInstanceOf[ResponseEntity[PalautaLahetyksetResponse]]
    catch
      case e: Exception =>
        LOG.error("Lähetysten lukeminen epäonnistui", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PalautaLahetyksetFailureResponse(Seq(RaportointiAPIConstants.LAHETYKSET_LUKEMINEN_EPAONNISTUI).asJava))

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
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    LogContext(lahetysTunniste = lahetysTunniste)(() =>
      try
        Right(None)
          .flatMap(_ =>
            // tarkistetaan lukuoikeus
            if (!securityOperaatiot.onOikeusKatsella())
              LOG.info(s"Käyttäjällä ${securityOperaatiot.identiteetti} ei ole katseluooikeuksia raportointinäkymään")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // validoidaan tunniste
            val uuid = ParametriUtil.asUUID(lahetysTunniste)
            if (uuid.isEmpty)
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetysFailureResponse(LAHETYSTUNNISTE_INVALID)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            // haetaan lähetykset
            val lahetys = kantaOperaatiot.getLahetysKayttooikeusrajauksilla(tunniste, securityOperaatiot.getKayttajanOikeudet())
            if (lahetys.isEmpty)
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(lahetys.get))
          .flatMap(lahetys =>
            // validoidaan lukuoikeudet lähetykseen, vähän turha tuplatsekkaus
            val lahetyksenOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys.tunniste))(lahetys.tunniste)
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja, lahetyksenOikeudet))
              LOG.info(s"Käyttäjällä ei ole katseluooikeuksia lähetykseen ${lahetysTunniste}")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .map(lahetys =>
            val lahetysStatukset: Seq[VastaanottajatTilassa] = kantaOperaatiot.getLahetystenVastaanottotilat(Seq.apply(lahetys.tunniste), securityOperaatiot.getKayttajanOikeudet())
              .getOrElse(lahetys.tunniste, Seq.empty)
              .map(status => VastaanottajatTilassa(status._1, status._2))

            ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(
              lahetys.tunniste.toString, lahetys.otsikko, lahetys.omistaja, lahetys.lahettavaPalvelu, lahetys.lahettavanVirkailijanOID.getOrElse(""),
              lahetys.lahettaja.nimi.getOrElse(""), lahetys.lahettaja.sahkoposti, lahetys.replyTo.getOrElse(""), lahetys.luotu.toString, lahetysStatukset.asJava)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[PalautaLahetysResponse]]
      catch
        case e: Exception =>
          LOG.error("Lähetyksen lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PalautaLahetysFailureResponse(RaportointiAPIConstants.LAHETYS_LUKEMINEN_EPAONNISTUI)))

  @GetMapping(
    path = Array(GET_VASTAANOTTAJAT_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa yksittäisen lähetyksen vastaanottajatiedot tiloittain",
    description = "Palauttaa lähetyksen vastaanottajatiedot, mahdollisuus rajata aikavälillä",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa lähetyksen vastaanottajat", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  /**
   * Hakutuloksiin ryhmitellään ensin kaikki virhetilassa olevat, sitten kaikki keskeneräiset ja lopuksi lähetetyt.
   * Sivutuksessa täytyy huomioida, että tilat päivittyvät
   * @param lahetysTunniste
   * @param alkaen
   * @param enintaan
   * @param request
   * @return
   */
  def lueVastaanottajat(
    @PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String,
    @RequestParam(name = ALKAEN_PARAM_NAME, required = false) alkaen: Optional[String],
    @RequestParam(name = ENINTAAN_PARAM_NAME, required = false) enintaan: Optional[String],
                         request: HttpServletRequest
  ): ResponseEntity[VastaanottajatResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

    // TODO kursorisivutus,
    // niputa epäonnistuneet ja kesken tilat
    // träkkää viimeisen tila kesken/valmis
    LogContext(lahetysTunniste = lahetysTunniste)(() =>
      try
        Right(None)
          .flatMap(_ =>
            if (!securityOperaatiot.onOikeusKatsella())
              LOG.info(s"Käyttäjällä ${securityOperaatiot.identiteetti} ei ole katseluooikeuksia raportointinäkymään")
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
                if (uuid.isEmpty) virheet.appended(RaportointiAPIConstants.LAHETYSTUNNISTE_INVALID) else virheet)
              .map(virheet =>
                if (alkaen.isPresent && alkaenUuid.isEmpty) virheet.appended(RaportointiAPIConstants.ALKAEN_UUID_TUNNISTE_INVALID)
                else virheet)
              .map(virheet =>
                if (enintaan.isPresent &&
                  (enintaanInt.isEmpty || enintaanInt.get < VASTAANOTTAJAT_ENINTAAN_MIN || enintaanInt.get > VASTAANOTTAJAT_ENINTAAN_MAX))
                  virheet.appended(ENINTAAN_INVALID)
                else virheet).get

            if (!virheet.isEmpty)
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(VastaanottajatFailureResponse(virheet.asJava)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            val lahetys = kantaOperaatiot.getLahetys(tunniste)
            if (lahetys.isEmpty)
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(lahetys.get))
          .flatMap(lahetys =>
            val lahetyksenOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys.tunniste))(lahetys.tunniste)
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja, lahetyksenOikeudet))
              LOG.info(s"Ei katseluooikeuksia lähetykseen ${lahetysTunniste}")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .map(lahetys =>
            val alkaenUuid = ParametriUtil.asUUID(alkaen)
            val enintaanInt = ParametriUtil.asInt(enintaan)
            // TODO vaihda uuid->sposti
            val epaonnistuneet = kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Option.apply("epaonnistui"), securityOperaatiot.getKayttajanOikeudet())
            val vastaanottajat = kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.apply(enintaanInt.getOrElse(VASTAANOTTAJAT_ENINTAAN_DEFAULT)), Option.empty, securityOperaatiot.getKayttajanOikeudet())
            val seuraavatLinkki = {
              if (vastaanottajat.isEmpty || kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.apply(vastaanottajat.last.kontakti.sahkoposti), Option.apply(1), Option.empty,securityOperaatiot.getKayttajanOikeudet()).isEmpty)
                Optional.empty
              else
                val host = s"https://${request.getServerName}"
                val port = s"${if (request.getServerPort != 443) ":" + request.getServerPort else ""}"
                val path = s"${RaportointiAPIConstants.GET_VASTAANOTTAJAT_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste)}"
                val alkaenParam = s"?${ALKAEN_PARAM_NAME}=${vastaanottajat.last.tunniste}"
                val enintaanParam = enintaan.map(v => s"&${ENINTAAN_PARAM_NAME}=${v}").orElse("")
                Optional.of(host + port + path + alkaenParam + enintaanParam)
            }

            ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(
              (epaonnistuneet++vastaanottajat).map(vastaanottaja => VastaanottajaResponse(vastaanottaja.tunniste.toString,
                Optional.ofNullable(vastaanottaja.kontakti.nimi.getOrElse(null)), vastaanottaja.kontakti.sahkoposti,
                vastaanottaja.viestiTunniste.toString, vastaanottaja.tila.toString)).asJava, seuraavatLinkki)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[VastaanottajatResponse]]
      catch
        case e: Exception =>
          LOG.error("Vastaanottajien lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(VastaanottajatFailureResponse(Seq(RaportointiAPIConstants.VASTAANOTTAJAT_LUKEMINEN_EPAONNISTUI).asJava)))
}