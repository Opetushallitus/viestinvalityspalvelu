package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.*
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.*
import fi.oph.viestinvalitys.util.AwsUtil.MAX_AUDIT_LOG_ENTRY_SIZE
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{LahetysMetadata, LiiteMetadata, ViestiImpl, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants.*
import fi.oph.viestinvalitys.vastaanotto.security.SecurityOperaatiot
import fi.oph.viestinvalitys.vastaanotto.util.LanguageDetection
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Hidden, Operation}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.{RequestContextHolder, ServletRequestAttributes}
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}

import java.time.Instant
import java.util.{Collections, UUID}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

@RequestMapping(path = Array(""))
@RestController
@Tag(
  name = "3. Viestit",
  description = "Lähetettävät viestit. Viestit lähetetään yksi vastaanottaja kerrallaan.")
class ViestiResource {

  val LOG = LoggerFactory.getLogger(classOf[ViestiResource])

  @Autowired var mapper: ObjectMapper = null
  val mode = ConfigurationUtil.getMode()

  private def validoiViesti(viesti: ViestiImpl, kantaOperaatiot: KantaOperaatiot): Seq[String] =
    val securityOperaatiot = new SecurityOperaatiot
    val identiteetti = securityOperaatiot.getIdentiteetti()

    val liiteMetadatat = kantaOperaatiot.getLiitteet(ParametriUtil.validUUIDs(viesti.liitteidenTunnisteet))
      .map(liite => liite.tunniste -> LiiteMetadata(liite.omistaja, liite.koko))
      // hyväksytään esimerkkitunniste kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
      .appended(UUID.fromString(LahetysAPIConstants.ESIMERKKI_LIITETUNNISTE) -> LiiteMetadata(identiteetti, 0))
      .toMap

    val lahetysMetadata =
      ParametriUtil.asUUID(viesti.lahetysTunniste)
        .map(lahetysTunniste => kantaOperaatiot.getLahetys(lahetysTunniste)
          .map(lahetys => LahetysMetadata(lahetys.omistaja, lahetys.prioriteetti.equals(Prioriteetti.KORKEA))))
        .getOrElse(Option.empty)

    ViestiValidator.validateViesti(viesti, lahetysMetadata, liiteMetadatat, identiteetti)

  private def tallennaMetriikat(vastaanottajienMaara: Int, prioriteetti: Prioriteetti): Unit =
    AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
      .namespace(s"${ConfigurationUtil.environment}-viestinvalitys")
      .metricData(MetricDatum.builder()
        .metricName("VastaanottojenMaara")
        .value(vastaanottajienMaara.toDouble)
        .storageResolution(1)
        .dimensions(Seq(Dimension.builder()
          .name("Prioriteetti")
          .value(prioriteetti.toString)
          .build()).asJava)
        .timestamp(Instant.now())
        .unit(StandardUnit.COUNT)
        .build())
      .build())

  private def tallennaAuditLoki(viestiEntiteetti: Viesti, vastaanottajaEntiteetit: Seq[Vastaanottaja], liitteidenTunnisteet: Seq[UUID]): Unit =
    // Cloudwatching lokientry maksimikoko an 256kB, käytännössä viestin sisältö on ainoa kenttä joka voi viedä valtavasti tilaa
    // joten splitataan se useampaan osaan tarvittaessa
    val metadataSize = AuditLog.mapper.writeValueAsBytes(Map(
      "viestiTunniste" -> viestiEntiteetti.tunniste.toString,
      "vastaanottajaTunnisteet" -> vastaanottajaEntiteetit.map(v => v.tunniste.toString).mkString(","))).length
    val viestiSizeWithoutSisalto = AuditLog.mapper.writeValueAsBytes(java.util.Map.of(
      "viesti", viestiEntiteetti.copy(sisalto = ""), "liitteet", liitteidenTunnisteet, "vastaanottajat", vastaanottajaEntiteetit)).length
    val sisaltoSize = AuditLog.mapper.writeValueAsBytes(viestiEntiteetti.sisalto).length
    val splitSisalto = metadataSize + viestiSizeWithoutSisalto + sisaltoSize > MAX_AUDIT_LOG_ENTRY_SIZE

    // Tallennetaan viestin metatiedot ja itse viesti ensimmäiseen lokientryyn. Sisältää myös viestin sisällön jos mahtuu
    AuditLog.logCreate(
      AuditLog.getUser(RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest),
      Map(
        "viestiTunniste" -> viestiEntiteetti.tunniste.toString,
        "vastaanottajaTunnisteet" -> vastaanottajaEntiteetit.map(v => v.tunniste.toString).mkString(",")),
      AuditOperation.CreateViesti,
      java.util.Map.of(
        "viesti", if(splitSisalto) viestiEntiteetti.copy(sisalto=null) else viestiEntiteetti,
        "liitteet", liitteidenTunnisteet,
        "vastaanottajat", vastaanottajaEntiteetit))

    // jos viestin sisältö liian iso ensimmäiseen lokientryyn, tallennetaan viestin sisältö erillisissä entryissä
    if(splitSisalto)
      val osioKoko = MAX_AUDIT_LOG_ENTRY_SIZE-metadataSize
      val osiot = Math.ceil(sisaltoSize.asInstanceOf[Double] / osioKoko.asInstanceOf[Double]).asInstanceOf[Int]
      viestiEntiteetti.sisalto.grouped(osioKoko).zipWithIndex.foreach((sisalto, index) => {
        AuditLog.logCreate(
          AuditLog.getUser(RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest),
          Map(
            "viestiTunniste" -> viestiEntiteetti.tunniste.toString,
            "vastaanottajaTunnisteet" -> vastaanottajaEntiteetit.map(v => v.tunniste.toString).mkString(",")),
          AuditOperation.CreateViesti,
          java.util.Map.of(
            "osio", s"${index+1}/${osiot}",
            "sisalto", sisalto))
      })

  final val ENDPOINT_LISAAVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- Mikäli lähetystunnusta ei ole määritelty, se luodaan automaattisesti ja tunnuksen otsikkona on viestin otsikko\n" +
    "- Käyttöoikeusrajoitukset rajaavat ketkä voivat nähdä viestejä lähetys tai raportointirajapinnan kautta, niiden " +
    "täytyy olla organisaatiorajoitettuja, ts. seka organisaatio että oikeus -kentät ovat pakollisia (ks. esimerkki)\n" +
    "- Viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiImpl.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
    "suurempi koko johtaa 400-virheeseen\n" +
    "- Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja\n" +
    "- Yksittäinen järjestelmä voi lähettää vain yhden korkean prioriteetin pyynnön sekunnissa, " +
    "nopeampi lähetystahti voi johtaa 429-vastaukseen\n" +
    "- Viestin mukana on mahdollista antaa enintään " + ViestiImpl.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS_STR + " merkin pituinen " +
    " merkin pituinen idempotency-avain, jonka avulla voidaan varmistaa ettei samaa viestiä lähetetä kahdesti. Mikäli annetulla avaimella " +
    "löytyy jo viesti palautetaan tämän viestin tiedot. HUOMAA tämä myös käyttäessäsi rajapintaa swaggerin kautta!\n" +
    "- <b>HUOMAA ERITYISESTI</b> jos käytetään idempotency-avainta ja viesti on palasteltu useammaksi viestiksi koska vastaanottajia on enemmän " +
    "kuin yhden viestin maksimivastaanottajamäärä. Eri viesteillä täytyy olla eri idempotency-avain tai viesti lähetetään vain ensimmäisenä vuorossa oleville " +
    "vastaanottajille ja seuraavat kutsut palauttavat tämän ensimmäisen viestin tiedot!"
  @PostMapping(
    path = Array(LUO_VIESTI_PATH),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden viestin",
    description = ENDPOINT_LISAAVIESTI_DESCRIPTION,
    requestBody =
      new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[ViestiImpl])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description="Pyyntö vastaanotettu, palauttaa lähetettävän viestin tunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiSuccessResponseImpl])))),
      new ApiResponse(responseCode = "400", description=LahetysAPIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiFailureResponseImpl])))),
      new ApiResponse(responseCode = "403", description=LahetysAPIConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "429", description=LahetysAPIConstants.VIESTI_RATELIMIT_VIRHE, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiRateLimitResponseImpl])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte], @Hidden @RequestParam(name = "disableRateLimiter", defaultValue = "false") disableRateLimiter: Boolean): ResponseEntity[LuoViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    LogContext(path = LUO_VIESTI_PATH, identiteetti = securityOperaatiot.getIdentiteetti())(() =>
      try
        val kantaOperaatiot = KantaOperaatiot(DbUtil.database)

        Right(None)
          .flatMap(_ =>
            // tarkastetaan lähetysoikeus
            if (!securityOperaatiot.onOikeusLahettaa())
              LOG.warn("lähetysoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // deserialisoidaan
            try
              Right(mapper.readValue(viestiBytes, classOf[ViestiImpl]))
            catch
              case e: Exception =>
                LOG.warn("viestin deserialisointi epäonnistui")
                Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponseImpl(java.util.List.of(LahetysAPIConstants.VIRHEELLINEN_VIESTI_JSON_VIRHE)))))
          .flatMap(viesti =>
            // tarkastetaan rate limit
            if ((mode == Mode.PRODUCTION || !disableRateLimiter) &&
              (viesti.prioriteetti.isPresent && Prioriteetti.KORKEA.toString.equals(viesti.prioriteetti.get.toUpperCase)) &&
              kantaOperaatiot.getKorkeanPrioriteetinViestienMaaraSince(securityOperaatiot.getIdentiteetti(),
                LahetysAPIConstants.PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA) + 1 >
                LahetysAPIConstants.PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA)
              LOG.warn("liikaa korkean prioriteetin viestejä")
              Left(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(LuoViestiRateLimitResponseImpl(java.util.List.of(LahetysAPIConstants.VIESTI_RATELIMIT_VIRHE))))
            else
              Right(viesti))
          .flatMap(viesti =>
            // validoidaan viesti
            val validointiVirheet = validoiViesti(viesti, kantaOperaatiot)
            if (!validointiVirheet.isEmpty)
              LOG.warn("Viestissä on validointivirheitä: " + validointiVirheet.mkString(", "))
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponseImpl(validointiVirheet.asJava)))
            else
              Right(viesti))
          .map(viesti =>
            val omistaja = securityOperaatiot.getIdentiteetti()

            val existingViestiEntiteetti = viesti.idempotencyKey.toScala.map(key => kantaOperaatiot.getExistingViesti(omistaja, key)).getOrElse(Option.empty)
            if(existingViestiEntiteetti.isDefined)
              // Viesti on jo tallennettu käyttävän järjestelmän toimesta, palautetaan tallennettu viesti eikä lähetetä uudestaan
              LogContext(viestiTunniste = existingViestiEntiteetti.get.tunniste.toString)(() => LOG.info("Löydettiin tallennettu viesti avaimella "
                + viesti.idempotencyKey))
              existingViestiEntiteetti.get
            else
              // tallennetaan viesti
              val (viestiEntiteetti, vastaanottajaEntiteetit) = kantaOperaatiot.tallennaViesti(
                otsikko = viesti.otsikko.get,
                sisalto = viesti.sisalto.get,
                sisallonTyyppi = SisallonTyyppi.valueOf(viesti.sisallonTyyppi.get.toUpperCase),
                kielet = viesti.kielet.map(kielet => kielet.asScala.map(kieli => Kieli.valueOf(kieli.toUpperCase)).toSet).orElse(LanguageDetection.tunnistaKieli(viesti.sisalto.get)),
                maskit = viesti.maskit.map(maskit => maskit.asScala.map(maski => maski.getSalaisuus.get -> maski.getMaski.toScala).toMap).orElse(Map.empty),
                lahettavanVirkailijanOID = viesti.lahettavanVirkailijanOid.toScala,
                lahettaja = viesti.lahettaja.map(l => Kontakti(l.getNimi.toScala, l.getSahkopostiOsoite.get)).toScala,
                replyTo = viesti.replyTo.toScala,
                vastaanottajat = viesti.vastaanottajat.get.asScala.map(vastaanottaja => Kontakti(vastaanottaja.getNimi.toScala, vastaanottaja.getSahkopostiOsoite.get)).toSeq,
                liiteTunnisteet = viesti.liitteidenTunnisteet.orElse(Collections.emptyList()).asScala.map(tunniste => UUID.fromString(tunniste)).toSeq,
                lahettavaPalvelu = viesti.lahettavaPalvelu.toScala,
                lahetysTunniste = ParametriUtil.asUUID(viesti.lahetysTunniste),
                prioriteetti = viesti.prioriteetti.map(p => Prioriteetti.valueOf(p.toUpperCase)).toScala,
                sailytysAika = viesti.sailytysaika.map(s => s.asInstanceOf[Int]).toScala,
                kayttooikeusRajoitukset = viesti.kayttooikeusRajoitukset.toScala.map(r => r.asScala.toSet)
                  .map(kayttooikeudet => kayttooikeudet.map(kayttooikeus => Kayttooikeus(kayttooikeus.getOikeus.get, kayttooikeus.getOrganisaatio.toScala))).getOrElse(Set.empty),
                metadata = viesti.metadata.toScala.map(m => m.asScala.map(entry => entry._1 -> entry._2.asScala.toSeq).toMap).getOrElse(Map.empty),
                omistaja = omistaja,
                idempotencyKey = viesti.idempotencyKey.toScala
              )

              // yritetään tallentaa lokit ja metriikat (best effort)
              try
                LogContext(viestiTunniste = viestiEntiteetti.tunniste.toString)(() => LOG.info("tallennettiin viesti"))
                tallennaAuditLoki(viestiEntiteetti, vastaanottajaEntiteetit, viesti.liitteidenTunnisteet.orElse(Collections.emptyList()).asScala.map(tunniste => UUID.fromString(tunniste)).toSeq)
                tallennaMetriikat(vastaanottajaEntiteetit.size, viestiEntiteetti.prioriteetti)
              catch
                case e: Exception => LogContext(viestiTunniste = viestiEntiteetti.tunniste.toString)
                  (() => LOG.error("Lokien ja/tai metriikoiden tallennus epäonnistui!", e))

              viestiEntiteetti
          )
          .map(viestiEntiteetti =>
            ResponseEntity.status(HttpStatus.OK).body(LuoViestiSuccessResponseImpl(viestiEntiteetti.tunniste, viestiEntiteetti.lahetysTunniste)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[LuoViestiResponse]]
        catch
        case e: Exception =>
          LOG.error("Viestin luonti epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(LuoViestiFailureResponseImpl(Seq(LahetysAPIConstants.VIESTIN_LUONTI_EPAONNISTUI).asJava)))

  final val ENDPOINT_LUEVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- Palauttaa viestin ja yhteenvedon lähetyksen tilasta\n"
  @GetMapping(
    path = Array(GET_VIESTI_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa viestin",
    description = ENDPOINT_LUEVIESTI_DESCRIPTION,
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = LahetysAPIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description = LahetysAPIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = LahetysAPIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueViesti(@PathVariable(VIESTITUNNISTE_PARAM_NAME) viestiTunniste: String): ResponseEntity[PalautaViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    LogContext(path = GET_VIESTI_PATH, identiteetti = securityOperaatiot.getIdentiteetti(), viestiTunniste = viestiTunniste)(() =>
      try
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

        Right(None)
          .flatMap(_ =>
            // tarkastetaan katseluoikeus
            if (!securityOperaatiot.onOikeusKatsella())
              LOG.warn("katseluoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            // validoidaan tunniste
            val uuid = ParametriUtil.asUUID(viestiTunniste)
            if (uuid.isEmpty)
              LOG.warn("viestitunniste ei ole validi")
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaViestiFailureResponse(LahetysAPIConstants.VIESTITUNNISTE_INVALID)))
            else
              Right(uuid.get))
          .flatMap(tunniste =>
            // yritetään lukea viesti
            val viesti = kantaOperaatiot.getViestit(Seq(tunniste)).find(v => true)
            if (viesti.isEmpty)
              LOG.warn("viestitunnistetta ei ole kannassa")
              Left(ResponseEntity.status(HttpStatus.GONE).build())
            else
              Right(viesti.get))
          .flatMap(viesti =>
            // tarkastetaan käyttäjän oikeudet tähän viestiin
            if (!securityOperaatiot.onOikeusKatsellaEntiteetti(viesti.omistaja))
              LOG.warn("oikeus viestiin puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(viesti))
          .map(viesti =>
            LOG.info("palautetaan viesti")
            ResponseEntity.status(HttpStatus.OK).body(PalautaViestiSuccessResponse(viesti.tunniste, viesti.otsikko)))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[PalautaViestiResponse]]
      catch
        case e: Exception =>
          LOG.error("Viestin lukeminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PalautaViestiFailureResponse(LahetysAPIConstants.VIESTIN_LUKEMINEN_EPAONNISTUI)))

}