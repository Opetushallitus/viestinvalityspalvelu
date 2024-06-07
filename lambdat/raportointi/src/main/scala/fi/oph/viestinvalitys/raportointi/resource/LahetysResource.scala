package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus, Vastaanottaja}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioClient
import fi.oph.viestinvalitys.raportointi.model.*
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.{DbUtil, LogContext}
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.{RequestContextHolder, ServletRequestAttributes}
import slick.jdbc.PostgresProfile.api.*

import java.util
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*


@RequestMapping(path = Array(""))
@RestController("RaportointiLahetys")
@Tag(
  name = "4. Raportointi",
  description = "Lähetys on joukko viestejä joita voidaan tarkastella yhtenä kokonaisuutena raportoinnissa. Viestit " +
    "liitetään luomisen yhteydessä lähetykseen, joko erikseen tai automaattisesti luotuun.")
class LahetysResource {

  val LOG = LoggerFactory.getLogger(classOf[LahetysResource])

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
                    @RequestParam(name = ORGANISAATIO_PARAM_NAME, required = false) organisaatio: Optional[String],
                    request: HttpServletRequest): ResponseEntity[PalautaLahetyksetResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    try
      Right(None)
        .flatMap(_ =>
          // tarkistetaan lukuoikeus
          if (!securityOperaatiot.onOikeusKatsella())
            Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
          else
            Right(None))
        .flatMap(_ =>
          val virheet = LahetyksetParamValidator.validateLahetyksetParams(LahetyksetParams(alkaen, enintaan, vastaanottajanEmail, organisaatio))
          if (!virheet.isEmpty)
            Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetyksetFailureResponse(virheet.asJava)))
          else
            Right(None))
        .flatMap(_ =>
          val alkaenAika = ParametriUtil.asInstant(alkaen)
          val enintaanInt = ParametriUtil.asInt(enintaan)
          val kayttooikeudetRajauksella = organisaatiorajaus(organisaatio, securityOperaatiot.getKayttajanOikeudet(), OrganisaatioClient)
          val lahetykset = kantaOperaatiot.getLahetykset(alkaenAika, enintaanInt, kayttooikeudetRajauksella, vastaanottajanEmail.orElse(""))
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
            val maskit = kantaOperaatiot.getLahetystenMaskit(lahetykset.map(_.tunniste), securityOperaatiot.getKayttajanOikeudet())
            AuditLog.logRead("lahetys", lahetykset.map(lahetys => lahetys.tunniste.toString).toList.toString(), AuditOperation.ReadLahetys,
              RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
            // TODO sivutus edellisiin?
            Right(ResponseEntity.status(HttpStatus.OK).body(PalautaLahetyksetSuccessResponse(
              lahetykset.map(lahetys => PalautaLahetysSuccessResponse(
                lahetys.tunniste.toString, lahetysotsikonMaskaus(lahetys.otsikko, lahetys.tunniste, maskit), lahetys.omistaja, lahetys.lahettavaPalvelu, lahetys.lahettavanVirkailijanOID.getOrElse(""),
                lahetys.lahettaja.nimi.getOrElse(""), lahetys.lahettaja.sahkoposti, lahetys.replyTo.getOrElse(""), lahetys.luotu.toString,
                lahetysStatukset.getOrElse(lahetys.tunniste, Seq.empty).map(status => VastaanottajatTilassa(status._1, status._2)).asJava, 0)).asJava, seuraavatAlkaen))))
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
              LOG.warn(s"Käyttäjällä ${securityOperaatiot.identiteetti} ei ole katseluooikeuksia raportointinäkymään")
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
            val lahetyksenOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys.tunniste)).getOrElse(lahetys.tunniste, Set.empty)
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja, lahetyksenOikeudet))
              LOG.warn(s"Käyttäjällä ei ole katseluooikeuksia lähetykseen ${lahetysTunniste}")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .map(lahetys =>
             val lahetysStatukset: Seq[VastaanottajatTilassa] = kantaOperaatiot.getLahetystenVastaanottotilat(Seq.apply(lahetys.tunniste), securityOperaatiot.getKayttajanOikeudet())
              .getOrElse(lahetys.tunniste, Seq.empty)
              .map(status => VastaanottajatTilassa(status._1, status._2))
             val viestiLkm: Int = kantaOperaatiot.getLahetyksenViestiLkm(lahetys.tunniste)
             val maskit = kantaOperaatiot.getLahetystenMaskit(Seq.apply(lahetys.tunniste), securityOperaatiot.getKayttajanOikeudet())
             AuditLog.logRead("lahetys", lahetys.tunniste.toString, AuditOperation.ReadLahetys,
              RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
             ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(
              lahetys.tunniste.toString, lahetysotsikonMaskaus(lahetys.otsikko, lahetys.tunniste, maskit), lahetys.omistaja, lahetys.lahettavaPalvelu, lahetys.lahettavanVirkailijanOID.getOrElse(""),
              lahetys.lahettaja.nimi.getOrElse(""), lahetys.lahettaja.sahkoposti, lahetys.replyTo.getOrElse(""), lahetys.luotu.toString, lahetysStatukset.asJava, viestiLkm)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[PalautaLahetysResponse]]
      catch
        case e: Exception =>
          LOG.error("Lähetyksen lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PalautaLahetysFailureResponse(RaportointiAPIConstants.LAHETYS_LUKEMINEN_EPAONNISTUI)))

  @GetMapping(
    path = Array(GET_VIESTI_LAHETYSTUNNISTEELLA_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa massaviestin lähetystunnisteella",
    description = "Palauttaa massaviestin tiedot raportointikäyttöliittymälle maskatulla sisällöllä",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[ViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueMassaviesti(@PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String): ResponseEntity[ViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

    LogContext(lahetysTunniste = lahetysTunniste)(() =>
      try
        Right(None)
          .flatMap(_ =>
            // tarkistetaan katseluoikeus
            if (!securityOperaatiot.onOikeusKatsella())
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // validoidaan tunniste
            val uuid = ParametriUtil.asUUID(lahetysTunniste)
            if (uuid.isEmpty)
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ViestiFailureResponse(LAHETYSTUNNISTE_INVALID)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            // haetaan viesti
            val viesti = kantaOperaatiot.getMassaViestiLahetystunnisteella(tunniste, securityOperaatiot.getKayttajanOikeudet())
            if (viesti.isEmpty)
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(viesti.get))
          .flatMap(viesti =>
            // tarkistetaan oikeudet viestiin
            val viestinOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getViestinKayttooikeudet(Seq(viesti.tunniste)).getOrElse(viesti.tunniste, Set.empty)
            val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(viesti.omistaja, viestinOikeudet)
            if (!onLukuOikeudet)
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(viesti))
          .map(viesti =>
            val maskattuOtsikko = if (!viesti.maskit.isEmpty) MaskiUtil.maskaaSalaisuudet(viesti.otsikko, viesti.maskit) else viesti.otsikko
            val maskattuSisalto = if (!viesti.maskit.isEmpty) MaskiUtil.maskaaSalaisuudet(viesti.sisalto, viesti.maskit) else viesti.sisalto
            AuditLog.logRead("viest", viesti.tunniste.toString, AuditOperation.ReadViesti,
              RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
            ResponseEntity.status(HttpStatus.OK).body(ViestiSuccessResponse(
                viesti.lahetysTunniste.toString, viesti.tunniste.toString, maskattuOtsikko,
              maskattuSisalto, viesti.sisallonTyyppi.toString, viesti.kielet.map(kieli => kieli.toString).toSeq.asJava
              )
            ))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[ViestiResponse]]
      catch
        case e: Exception =>
          LOG.error("Viestin lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ViestiFailureResponse(RaportointiAPIConstants.VIESTI_LUKEMINEN_EPAONNISTUI)))

  @GetMapping(
    path = Array(GET_VIESTI_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa viestin tunnisteella",
    description = "Palauttaa viestin tiedot raportointikäyttöliittymälle maskatulla sisällöllä",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[ViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description = KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueViesti(@PathVariable(VIESTITUNNISTE_PARAM_NAME) viestiTunniste: String): ResponseEntity[ViestiResponse] =

    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

    LogContext(lahetysTunniste = viestiTunniste)(() =>
      try
        Right(None)
          .flatMap(_ =>
            // tarkistetaan katseluoikeus
            if (!securityOperaatiot.onOikeusKatsella())
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // validoidaan tunniste
            val uuid = ParametriUtil.asUUID(viestiTunniste)
            if (uuid.isEmpty)
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ViestiFailureResponse(LAHETYSTUNNISTE_INVALID)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            // haetaan viesti
            val viesti = kantaOperaatiot.getRaportointiViestiTunnisteella(tunniste, securityOperaatiot.getKayttajanOikeudet())
            if (viesti.isEmpty)
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(viesti.get))
          .flatMap(viesti =>
            // tarkistetaan oikeudet viestiin
            val viestinOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getViestinKayttooikeudet(Seq(viesti.tunniste)).getOrElse(viesti.tunniste, Set.empty)
            val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(viesti.omistaja, viestinOikeudet)
            if (!onLukuOikeudet)
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(viesti))
          .map(viesti =>
            val maskattuOtsikko = if (!viesti.maskit.isEmpty) MaskiUtil.maskaaSalaisuudet(viesti.otsikko, viesti.maskit) else viesti.otsikko
            val maskattuSisalto = if (!viesti.maskit.isEmpty) MaskiUtil.maskaaSalaisuudet(viesti.sisalto, viesti.maskit) else viesti.sisalto
            AuditLog.logRead("viest", viesti.tunniste.toString, AuditOperation.ReadViesti,
              RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
            ResponseEntity.status(HttpStatus.OK).body(ViestiSuccessResponse(
              viesti.lahetysTunniste.toString, viesti.tunniste.toString, maskattuOtsikko,
              maskattuSisalto, viesti.sisallonTyyppi.toString, viesti.kielet.map(kieli => kieli.toString).toSeq.asJava
            )
            ))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[ViestiResponse]]
      catch
        case e: Exception =>
          LOG.error("Viestin lukeminen epäonnistui", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ViestiFailureResponse(RaportointiAPIConstants.VIESTI_LUKEMINEN_EPAONNISTUI)))

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
   * @param alkaen sähköposti sivutusta varten
   * @param sivutusTila missä tilassa olevia sivutetaan
   * @param enintaan sivutuksen koko
   * @param tila epaonnistui / kesken / valmis
   * @param vastaanottaja
   * @param organisaatio
   * @return
   */
  def lueVastaanottajat(
    @PathVariable(LAHETYSTUNNISTE_PARAM_NAME) lahetysTunniste: String,
    @RequestParam(name = ALKAEN_PARAM_NAME, required = false) alkaen: Optional[String],
    @RequestParam(name = SIVUTUS_TILA_PARAM_NAME, required = false) sivutustila: Optional[String],
    @RequestParam(name = ENINTAAN_PARAM_NAME, required = false) enintaan: Optional[String],
    @RequestParam(name = TILA_PARAM_NAME, required = false) tila: Optional[String],
    @RequestParam(name = VASTAANOTTAJA_PARAM_NAME, required = false) vastaanottajanEmail: Optional[String],
    @RequestParam(name = ORGANISAATIO_PARAM_NAME, required = false) organisaatio: Optional[String],
                         request: HttpServletRequest
  ): ResponseEntity[VastaanottajatResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    LogContext(lahetysTunniste = lahetysTunniste)(() =>
      try
        Right(None)
          .flatMap(_ =>
            if (!securityOperaatiot.onOikeusKatsella())
              LOG.warn(s"Käyttäjällä ${securityOperaatiot.identiteetti} ei ole katseluooikeuksia raportointinäkymään")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            val virheet = LahetyksetParamValidator.validateVastaanottajatParams(VastaanottajatParams(lahetysTunniste, alkaen, enintaan, sivutustila, tila, vastaanottajanEmail, organisaatio))
            if (!virheet.isEmpty)
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(VastaanottajatFailureResponse(virheet.asJava)))
            else
              Right(ParametriUtil.asUUID(lahetysTunniste).get))
          .flatMap(tunniste =>
            val lahetys = kantaOperaatiot.getLahetys(tunniste)
            if (lahetys.isEmpty)
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(lahetys.get))
          .flatMap(lahetys =>
            val lahetyksenOikeudet: Set[Kayttooikeus] = kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys.tunniste)).getOrElse(lahetys.tunniste, Set.empty)
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.omistaja, lahetyksenOikeudet))
              LOG.warn(s"Ei katseluooikeuksia lähetykseen $lahetysTunniste")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(lahetys))
          .flatMap(lahetys =>
            val enintaanInt = ParametriUtil.asInt(enintaan).getOrElse(VASTAANOTTAJAT_ENINTAAN_DEFAULT)
            val kayttooikeudetRajauksella = organisaatiorajaus(organisaatio, securityOperaatiot.getKayttajanOikeudet(), OrganisaatioClient)
            // sivutusta varten haetaan myös jonon seuraava
            val vastaanottajatJaSeuraava = vastaanottajaLista(kantaOperaatiot, kayttooikeudetRajauksella, lahetys.tunniste,
              ParametriUtil.asValidEmail(alkaen), enintaanInt+1, ParametriUtil.asValidRaportointitila(sivutustila), vastaanottajanEmail, ParametriUtil.asValidRaportointitila(tila))
            if (vastaanottajatJaSeuraava.isEmpty)
              Left(ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(Seq.empty.asJava, Optional.empty, Optional.empty)))
            else
              val vastaanottajat = {
                if (vastaanottajatJaSeuraava.length <= enintaanInt)
                  vastaanottajatJaSeuraava
                else
                  vastaanottajatJaSeuraava.dropRight(1)
              }
              val viimeisenTila = ParametriUtil.getRaportointiTila(vastaanottajat.last.tila)
              val seuraavatAlkaen = {
                vastaanottajatJaSeuraava match
                  case v if v.length <= enintaanInt => Optional.empty // lista jatkuu seuraavalle sivulle
                  case _ => Optional.of(vastaanottajat.last.kontakti.sahkoposti)
              }
              AuditLog.logRead("vastaanottajat", vastaanottajat.map(v => v.tunniste.toString).toList.toString(), AuditOperation.ReadVastaanottajat,
                RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
              Right(ResponseEntity.status(HttpStatus.OK).body(VastaanottajatSuccessResponse(
              vastaanottajat.map(vastaanottaja => VastaanottajaResponse(vastaanottaja.tunniste.toString,
                Optional.ofNullable(vastaanottaja.kontakti.nimi.getOrElse(null)), vastaanottaja.kontakti.sahkoposti,
                vastaanottaja.viestiTunniste.toString, vastaanottaja.tila.toString)).asJava, seuraavatAlkaen, Optional.of(viimeisenTila.get)))))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[VastaanottajatResponse]]
      catch
        case e: Exception =>
          LOG.error("Vastaanottajien lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(VastaanottajatFailureResponse(Seq(RaportointiAPIConstants.VASTAANOTTAJAT_LUKEMINEN_EPAONNISTUI).asJava)))

  def organisaatiorajaus(organisaatio: Optional[String], kayttajanOikeudet: Set[Kayttooikeus], organisaatioClient: OrganisaatioClient): Set[Kayttooikeus] =
    if (organisaatio.isEmpty)
      kayttajanOikeudet
    else
      val childOids: Set[String] = organisaatioClient.getAllChildOidsFlat(organisaatio.get)
      kayttajanOikeudet.filter(ko => ko.organisaatio.exists(o => childOids.contains(o) || o.equals(organisaatio.get())))

  def lahetysotsikonMaskaus(otsikko: String, lahetysTunnus: UUID, lahetystenMaskit: Map[UUID, Map[String, Option[String]]]): String =
    if (lahetystenMaskit.get(lahetysTunnus).nonEmpty)
      MaskiUtil.maskaaSalaisuudet(otsikko, lahetystenMaskit.get(lahetysTunnus).get)
    else
      otsikko

  def vastaanottajaLista(kantaOperaatiot: KantaOperaatiot,
                         kayttajanOikeudet: Set[Kayttooikeus],
                         lahetystunniste: UUID,
                         alkaenValidEmail: Option[String],
                         enintaanInt: Int,
                         sivutustila: Option[String],
                         vastaanottajanEmail: Optional[String],
                         tila: Option[String]): Seq[Vastaanottaja] =
    tila match
      case Some(tila) =>
        kantaOperaatiot.haeLahetyksenVastaanottajia(
          lahetysTunniste = lahetystunniste,
          alkaen = alkaenValidEmail,
          enintaan = Some(enintaanInt),
          raportointiTila = Some(tila),
          kayttooikeudet = kayttajanOikeudet,
          vastaanottajanEmail = vastaanottajanEmail.orElse("")
        )
      case _ =>
        val epaonnistuneet: Seq[Vastaanottaja] = {
          if (alkaenValidEmail.isEmpty)
            // kaikki epäonnistuneet sivutuksen alkuun jos ei olla selattu eteenpäin
            kantaOperaatiot.haeLahetyksenVastaanottajia(
              lahetysTunniste = lahetystunniste,
              alkaen = None,
              enintaan = None,
              raportointiTila = Some("epaonnistui"),
              kayttooikeudet = kayttajanOikeudet,
              vastaanottajanEmail = vastaanottajanEmail.orElse("")
            )
          else Seq.empty
        }
        sivutustila match
          case Some(sivutustila) if sivutustila.equals("valmis") =>
            // sivutuksessa seuraavana on valmiit lähetykset, ei haeta keskeneräisiä
            Seq(epaonnistuneet,
              kantaOperaatiot.haeLahetyksenVastaanottajia(
                lahetysTunniste = lahetystunniste,
                alkaen = alkaenValidEmail,
                enintaan = Some(enintaanInt),
                raportointiTila = Some("valmis"),
                kayttooikeudet = kayttajanOikeudet,
                vastaanottajanEmail = vastaanottajanEmail.orElse("")
              )
            ).flatten
          case _ =>
            val kesken: Seq[Vastaanottaja] = kantaOperaatiot.haeLahetyksenVastaanottajia(
              lahetysTunniste = lahetystunniste,
              alkaen = alkaenValidEmail,
              enintaan = Some(enintaanInt),
              raportointiTila = Some("kesken"),
              kayttooikeudet = kayttajanOikeudet,
              vastaanottajanEmail = vastaanottajanEmail.orElse("")
            )
            if (kesken.size < enintaanInt)
              // jos sivu ei ole täynnä keskeneräisistä, haetaan valmiita
              val loputCount = enintaanInt - kesken.size
              val loputSivulla = kantaOperaatiot.haeLahetyksenVastaanottajia(
                lahetysTunniste = lahetystunniste,
                alkaen = None,
                enintaan = Some(loputCount),
                raportointiTila = Some("valmis"),
                kayttooikeudet = kayttajanOikeudet,
                vastaanottajanEmail = vastaanottajanEmail.orElse("")
              )
              Seq(epaonnistuneet, kesken, loputSivulla).flatten
            else
              Seq(epaonnistuneet,kesken).flatten
}