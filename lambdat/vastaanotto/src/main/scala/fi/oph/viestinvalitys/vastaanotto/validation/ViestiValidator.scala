package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.*
import fi.oph.viestinvalitys.vastaanotto.model.ViestiImpl.*
import fi.oph.viestinvalitys.vastaanotto.validation.LahetysValidator
import org.apache.commons.validator.routines.EmailValidator

import java.util.stream.Collectors
import java.util.{List, Optional, UUID}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/**
 * Sisältää liitteen validointiin tarvittavan metadatan
 */
case class LiiteMetadata(omistaja: String, koko: Int)

/**
 * Sisältää lähetyksen validointiin tarvittavan metadatan
 */
case class LahetysMetadata(omistaja: String, korkeaPrioriteetti: Boolean)

/**
 * Validoi järjestelmään syötetyn viestin kentät. Validaattorin virheilmoitukset eivät saa sisältää sensitiivistä tietoa
 * koska ne menevät mm. lokeille.
 */
object ViestiValidator:

  final val VALIDATION_OTSIKKO_TYHJA                      = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA                = "otsikko: Otsikko ei voi pidempi kuin " + OTSIKKO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALTO_TYHJA                      = "sisalto: Kenttä on pakollinen"
  final val VALIDATION_SISALTO_LIIAN_PITKA                = "sisalto: Sisältö ei voi pidempi kuin " + SISALTO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALLONTYYPPI                     = "sisallonTyyppi: Sisällön tyypin täytyy olla joko \"" + VIESTI_SISALTOTYYPPI_TEXT + "\" tai \"" + VIESTI_SISALTOTYYPPI_HTML + "\""

  final val VALIDATION_KIELI_EI_SALLITTU                  = "kielet: Kieli ei ole sallittu (\"fi\", \"sv\" ja \"en\"): "
  final val VALIDATION_KIELI_NULL                         = "kielet: Kenttä sisältää null-arvoja"
  final val VALIDATION_KIELI_DUPLICATES                   = "kielet: Kenttä sisältää duplikaatteja: "

  final val VALIDATION_MASKIT_NULL                        = "maskit: Kenttä sisältää null-arvoja"
  final val VALIDATION_MASKIT_LIIKAA                      = "maskit: Viestillä voi maksimissaan olla " + VIESTI_MASKIT_MAX_MAARA + " maskia"
  final val VALIDATION_MASKIT_EI_SALAISUUTTA              = "salaisuus-kenttä on pakollinen"
  final val VALIDATION_MASKIT_SALAISUUS_PITUUS            = "salaisuus-kentän sallittu pituus on " + VIESTI_SALAISUUS_MIN_PITUUS + "-" + VIESTI_SALAISUUS_MAX_PITUUS + " merkkiä"
  final val VALIDATION_MASKIT_MASKI_PITUUS                = "maski-kentän sallittu pituus on " + VIESTI_MASKI_MIN_PITUUS + "-" + VIESTI_MASKI_MAX_PITUUS + " merkkiä"
  final val VALIDATION_MASKIT_DUPLICATES                  = "maskit: salaisuus-kentissä on duplikaatteja: "

  final val VALIDATION_VASTAANOTTAJAT_TYHJA               = "vastaanottajat: Kenttä on pakollinen"
  final val VALIDATION_VASTAANOTTAJAT_LIIKAA              = "vastaanottajat: Viestillä voi maksimissaan olla " + VIESTI_VASTAANOTTAJAT_MAX_MAARA + " vastaanottajaa"
  final val VALIDATION_VASTAANOTTAJA_NULL                 = "vastaanottajat: Kenttä sisältää null-arvoja"
  final val VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE     = "vastaanottajat: Osoite-kentissä on duplikaatteja: "
  final val VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA    = "nimi-kenttä voi maksimissaan olla " + VIESTI_NIMI_MAX_PITUUS + " merkkiä pitkä"
  final val VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA        = "sähköpostiosoite-kenttä on pakollinen"
  final val VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID      = "sähköpostiosoite ei ole validi sähköpostiosoite"

  final val VALIDATION_LIITETUNNISTE_NULL                 = "liiteTunnisteet: Kenttä sisältää null-arvoja"
  final val VALIDATION_LIITETUNNISTE_LIIKAA               = "liiteTunnisteet: Viestillä voi maksimissaan olla " + VIESTI_LIITTEET_MAX_MAARA + " liitettä"
  final val VALIDATION_LIITETUNNISTE_DUPLICATE            = "liiteTunnisteet: Kentässä on duplikaatteja: "
  final val VALIDATION_LIITETUNNISTE_INVALID              = "liitetunniste ei ole muodoltaan validi liitetunniste"
  final val VALIDATION_LIITETUNNISTE_EI_TARJOLLA          = "liitetunnistetta vastaavaa liitettä ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia"

  final val VALIDATION_LAHETYSTUNNISTE_INVALID            = "lähetysTunniste: arvo ei ole muodoltaan validi lähetysTunniste"
  final val VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA        = "lähetysTunniste: tunnistetta ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia"

  final val VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA        = "idempotencyKey: Idempotency-avain ei voi pidempi kuin " + VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS + " merkkiä"
  final val VALIDATION_IDEMPOTENCY_KEY_INVALID            = "idempotencyKey: Sallitut merkit ovat " + VIESTI_IDEMPOTENCY_KEY_SALLITUT_MERKIT

  final val VALIDATION_METADATA_NULL                      = "metadata: Seuraavat avaimet sisältävät null-arvoja: "
  final val VALIDATION_METADATA_DUPLICATE                 = "metadata: Seuraavat avaimet sisältää duplikaattiarvoja: "
  final val VALIDATION_METADATA_AVAIMET_MAARA             = "metadata: Metadata voi sisältää maksimissaan " + VIESTI_METADATA_AVAIMET_MAX_MAARA + " avainta"
  final val VALIDATION_METADATA_AVAIN_INVALID             = "avaimessa sallitut merkit ovat " + VIESTI_METADATA_SALLITUT_MERKIT
  final val VALIDATION_METADATA_AVAIN_PITUUS              = "avain on yli maksimipituuden " + VIESTI_METADATA_AVAIN_MAX_PITUUS + " merkkiä"
  final val VALIDATION_METADATA_ARVOT_MAARA               = "avain sisältää yli " + VIESTI_METADATA_ARVOT_MAX_MAARA + " arvoa"
  final val VALIDATION_METADATA_ARVO_INVALID              = "arvossa sallitut merkit ovat " + VIESTI_METADATA_SALLITUT_MERKIT
  final val VALIDATION_METADATA_ARVO_PITUUS               = "arvo on yli maksimipituuden " + VIESTI_METADATA_ARVO_MAX_PITUUS + " merkkiä: "

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL          = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA        = "kayttooikeusRajoitukset: Viestillä voi maksimissaan olla " + VIESTI_KAYTTOOIKEUS_MAX_MAARA + " käyttöoikeusrajoitusta"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE     = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: "
  final val VALIDATION_ORGANISAATIO_INVALID               = "käyttöoikeusrajoituksen organisaation oidin tulee olla muotoa 1.2.246.562.(10|99).\\d"
  final val VALIDATION_ORGANISAATIO_PITUUS                = "käyttöoikeusrajoituksen organisaatio on yli maksimipituuden " + VIESTI_ORGANISAATIO_MAX_PITUUS + " merkkiä"
  final val VALIDATION_OIKEUS_TYHJA                       = "käyttöoikeusrajoituksen oikeus on tyhjä"
  final val VALIDATION_OIKEUS_PITUUS                      = "käyttöoikeusrajoituksen oikeus on yli maksimipituuden " + VIESTI_OIKEUS_MAX_PITUUS + " merkkiä"

  final val VALIDATION_LAHETTAVAPALVELU_EI_TYHJA          = "lahettavapalvelu: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_VIRKAILIJANOID_EI_TYHJA            = "lähettävän virkailijan oid: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_LAHETTAJA_EI_TYHJA                 = "lähettäjä: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_REPLYTO_EI_TYHJA                   = "replyTo: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_PRIORITEETTI_EI_TYHJA              = "prioriteetti: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_SAILYTYSAIKA_EI_TYHJA              = "sailytysaika: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"

  final val VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT = "prioriteetti: Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja"

  final val VALIDATION_KOKO                               = "koko: viestin ja liitteiden koko on suurempi kuin " + VIESTI_MAX_SIZE_MB_STR + " megatavua"

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if(otsikko.isEmpty || otsikko.get.length==0) Left(virheet.incl(VALIDATION_OTSIKKO_TYHJA)) else Right(virheet))
      .map(virheet =>
        if(otsikko.get.length > OTSIKKO_MAX_PITUUS) virheet.incl(VALIDATION_OTSIKKO_LIIAN_PITKA) else virheet)
      .fold(l => l, r => r)

  def validateSisalto(sisalto: Optional[String]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (sisalto.isEmpty || sisalto.get.length == 0) Left(virheet.incl(VALIDATION_SISALTO_TYHJA)) else Right(virheet))
      .map(virheet =>
        if (sisalto.get.length > SISALTO_MAX_PITUUS) virheet.incl(VALIDATION_SISALTO_LIIAN_PITKA) else virheet)
      .fold(l => l, r => r)

  def validateSisallonTyyppi(sisallonTyyppi: Optional[String]): Set[String] =
    if(sisallonTyyppi.isEmpty || (!sisallonTyyppi.get.equals(VIESTI_SISALTOTYYPPI_TEXT) && !sisallonTyyppi.get.equals(VIESTI_SISALTOTYYPPI_HTML)))
      Set(VALIDATION_SISALLONTYYPPI)
    else
      Set.empty

  final val SALLITUT_KIELET = Set("fi", "sv", "en")
  def validateKielet(kielet: Optional[List[String]]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // katsotaan tarvitseeko validoida
        if(kielet.isEmpty || kielet.get.isEmpty) Left(virheet) else Right(virheet))
      .map(virheet =>
        // validoidaan yksittäiset kielet
        kielet.get.asScala.foldLeft(virheet)((virheet, kieli) =>
          if (kieli != null && !SALLITUT_KIELET.contains(kieli)) virheet.incl(VALIDATION_KIELI_EI_SALLITTU + kieli) else virheet
        ))
      .map(virheet =>
        // tutkitaan onko kielissä null-arvoja
        if (kielet.get.stream().filter(kieli => kieli == null).count() > 0) virheet.incl(VALIDATION_KIELI_NULL) else virheet)
      .map(virheet =>
        // tutkitaan onko kielissä duplikaatteja
        val duplikaattiKielet = kielet.get.asScala
          .filter(kieli => kieli != null)
          .groupBy(kieli => kieli)
          .filter(kielet => kielet._2.size > 1)
          .map(kielet => kielet._1)
        if (!duplikaattiKielet.isEmpty)
          virheet.incl(VALIDATION_KIELI_DUPLICATES + duplikaattiKielet.mkString(","))
        else virheet)
      .fold(l => l, r => r)

  def validateMaskit(maskit: Optional[List[Maski]]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // on ok että maskeja ei ole määritelty
        if(maskit.isEmpty) Left(virheet) else Right(virheet))
      .map(virheet =>
        // maskeja ei voi olla määrättömästi
        if(maskit.get.size()>VIESTI_MASKIT_MAX_MAARA) virheet.incl(VALIDATION_MASKIT_LIIKAA) else virheet)
      .map(virheet =>
        // tarkastetaan onko maskilistalla null-arvoja
        if (maskit.get.stream().filter(maski => maski == null).count() > 0) virheet.incl(VALIDATION_MASKIT_NULL) else virheet)
      .map(virheet =>
        // validoidaan yksittäiset maskit
        maskit.get.asScala.toSet.filter(m => m != null).foldLeft(virheet)((virheet, maski) =>
          val maskiVirheet = Some(Set.empty.asInstanceOf[Set[String]])
            .map(maskiVirheet =>
              // validoidaan salaisuus
              if (maski.getSalaisuus.isEmpty || maski.getSalaisuus.get.length == 0)
                maskiVirheet.incl(VALIDATION_MASKIT_EI_SALAISUUTTA)
              else if (maski.getSalaisuus.get.length < VIESTI_SALAISUUS_MIN_PITUUS || maski.getSalaisuus.get.length > VIESTI_SALAISUUS_MAX_PITUUS)
                maskiVirheet.incl(VALIDATION_MASKIT_SALAISUUS_PITUUS)
              else maskiVirheet)
            .map(maskiVirheet =>
              // validoidaan maski
              if (maski.getMaski.isPresent && (maski.getMaski.get.length < VIESTI_MASKI_MIN_PITUUS || maski.getMaski.get.length > VIESTI_MASKI_MAX_PITUUS))
                maskiVirheet.incl(VALIDATION_MASKIT_MASKI_PITUUS)
              else maskiVirheet).get

          if (!maskiVirheet.isEmpty)
            virheet.incl("Maski (salaisuus: " + maski.getSalaisuus.map(s => "*".repeat(s.length)).orElse("") +
              ", maski: " + maski.getMaski.orElse("") + "): " + maskiVirheet.mkString(","))
          else virheet))
      .map(virheet =>
        // tutkitaan onko maskeissa duplikaatteja
        val duplikaattiMaskit = maskit.get.asScala
          .filter(maski => maski != null && maski.getSalaisuus.isPresent)
          .groupBy(maski => maski.getSalaisuus)
          .filter(maskitBySalaisuus => maskitBySalaisuus._2.size > 1)
          .map(maskitBySalaisuus => maskitBySalaisuus._1.get())
        if (!duplikaattiMaskit.isEmpty)
          virheet.incl(VALIDATION_MASKIT_DUPLICATES + duplikaattiMaskit.map(s => "*".repeat(s.length)).mkString(","))
        else virheet)
      .fold(l => l, r => r)

  def validateVastaanottajat(vastaanottajat: Optional[List[Vastaanottaja]]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // vastaanottajat kenttä pitää olla määritelty
        if(vastaanottajat.isEmpty || vastaanottajat.get().isEmpty) Left(virheet.incl(VALIDATION_VASTAANOTTAJAT_TYHJA))
        else Right(virheet))
      .map(virheet =>
        // vastaanottajien määrälle on yläraja
        if(vastaanottajat.get.size>VIESTI_VASTAANOTTAJAT_MAX_MAARA) virheet.incl(VALIDATION_VASTAANOTTAJAT_LIIKAA)
        else virheet)
      .map(virheet =>
        // tarkastetaan onko vastaanottajalistalla null-arvoja
        if(vastaanottajat.get.stream().filter(vastaanottaja => vastaanottaja==null).count()>0)
          virheet.incl(VALIDATION_VASTAANOTTAJA_NULL)
        else virheet)
      .map(virheet =>
        // validoidaan yksittäiset vastaanottajat
        vastaanottajat.get.asScala.toSet.filter(v => v != null).foldLeft(virheet)((virheet, vastaanottaja) =>
          val vastaanottajaVirheet = Some(Set.empty.asInstanceOf[Set[String]])
            .map(vastaanottajaVirheet =>
              // validoidaan nimi
              if(vastaanottaja.getNimi.isPresent && vastaanottaja.getNimi.get.length>VIESTI_NIMI_MAX_PITUUS)
                vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA)
              else vastaanottajaVirheet)
            .map(vastaanottajaVirheet =>
              // validoidaan sahkopostiosoite
              if (vastaanottaja.getSahkopostiOsoite.isEmpty || vastaanottaja.getSahkopostiOsoite.get.length == 0)
                vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA)
              else if (!EmailValidator.getInstance().isValid(vastaanottaja.getSahkopostiOsoite.get))
                vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID)
              else vastaanottajaVirheet).get

          if (!vastaanottajaVirheet.isEmpty)
            virheet.incl("Vastaanottaja (nimi: " + vastaanottaja.getNimi.orElse("") + ", sähköpostiosoite: " + vastaanottaja.getSahkopostiOsoite + "): " +
              vastaanottajaVirheet.asJava.stream().collect(Collectors.joining(",")))
          else virheet))
      .map(virheet =>
        // tutkitaan onko osoitteissa duplikaatteja
        val duplikaattiOsoitteet = vastaanottajat.get.asScala
          .filter(vastaanottaja => vastaanottaja!=null && vastaanottaja.getSahkopostiOsoite.isPresent)
          .groupBy(vastaanottaja => vastaanottaja.getSahkopostiOsoite)
          .filter(vastaanottajatByOsoite => vastaanottajatByOsoite._2.size>1)
          .map(vastaanottajatByOsoite => vastaanottajatByOsoite._1.get())
        if(!duplikaattiOsoitteet.isEmpty)
          virheet.incl(VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + duplikaattiOsoitteet.mkString(","))
        else virheet)
      .fold(l => l, r => r)

  def validateLiitteidenTunnisteet(tunnisteet: Optional[List[String]], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // on ok jos tunnisteitä ei määritelty
        if(tunnisteet.isEmpty) Left(virheet) else Right(virheet))
      .map(virheet =>
        // validoidaan liitteiden määrä
        if(tunnisteet.get.size()>VIESTI_LIITTEET_MAX_MAARA) virheet.incl(VALIDATION_LIITETUNNISTE_LIIKAA) else virheet)
      .map(virheet =>
        // tarkastetaan onko liitetunnistelistalla null-arvoja
        if(tunnisteet.get.stream().filter(tunniste => tunniste==null).count()>0) virheet.incl(VALIDATION_LIITETUNNISTE_NULL)
        else virheet)
      .map(virheet =>
        // validoidaan yksittäiset liitetunnisteet
        tunnisteet.get.asScala.toSet.filter(t => t != null).foldLeft(virheet)((virheet, tunniste) =>
          val tunnisteVirheet = Right(Set.empty.asInstanceOf[Set[String]])
            .flatMap(tunnisteVirheet =>
              try
                Right((tunnisteVirheet, UUID.fromString(tunniste)))
              catch
                case e: Exception => Left(tunnisteVirheet.incl(VALIDATION_LIITETUNNISTE_INVALID)))
            .map((tunnisteVirheet, tunniste) =>
              val liiteMetadata = liiteMetadatat.get(tunniste)
              if (liiteMetadata.isEmpty || !identiteetti.equals(liiteMetadata.get.omistaja))
                tunnisteVirheet.incl(VALIDATION_LIITETUNNISTE_EI_TARJOLLA)
              else tunnisteVirheet)
            .fold(l => l, r => r)

          if(!tunnisteVirheet.isEmpty)
            virheet.incl("Liitetunniste \"" + tunniste + "\": " + tunnisteVirheet.mkString(","))
          else virheet))
      .map(virheet =>
        // tutkitaan onko tunnistelistalla duplikaatteja
        val duplikaattiTunnisteet = tunnisteet.get.asScala
          .filter(tunniste => tunniste != null)
          .groupBy(tunniste => tunniste)
          .filter(tunnisteByTunniste => tunnisteByTunniste._2.size > 1)
          .map(tunnisteByTunniste => tunnisteByTunniste._1)
        if (!duplikaattiTunnisteet.isEmpty)
          virheet.incl(VALIDATION_LIITETUNNISTE_DUPLICATE + duplikaattiTunnisteet.mkString(","))
        else virheet)
      .fold(l => l, r => r)

  def validateLahetysTunniste(tunniste: Optional[String], lahetysMetadata: Option[LahetysMetadata], identiteetti: String): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // on ok että lähetystunniste ei ole määritelty
        if(tunniste.isEmpty || "".equals(tunniste.get)) Left(virheet) else Right(virheet))
      .flatMap(virheet =>
        // validoidaan tunnisteen muoto
        try
          Right((virheet, UUID.fromString(tunniste.get)))
        catch
          case e: Exception => Left(virheet.incl(VALIDATION_LAHETYSTUNNISTE_INVALID)))
      .map((virheet, tunniste) =>
        // validoidaan lähetyksen olemassaolo ja oikeudet
        if (lahetysMetadata.isEmpty || !lahetysMetadata.get.omistaja.equals(identiteetti))
            virheet.incl(VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA)
        else virheet)
      .fold(l => l, r => r)

  val metadataAvainPattern: Regex = ("[a-zA-Z0-9\\-\\._]+").r
  val metadataArvoPattern: Regex = ("[a-zA-Z0-9\\-\\._]+").r
  def validateMetadata(metadata: Optional[java.util.Map[String, List[String]]]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // metadataa ei pakko määritellä
        if(metadata.isEmpty) Left(virheet) else Right(virheet))
      .map(virheet =>
        // tutkitaan onko metadatassa null-arvoja
        val nullArvot = metadata.get.asScala
          .filter(entry => entry._2==null || !entry._2.stream().filter(arvo => arvo==null).toList().isEmpty)
          .map(entry => entry._1)
        if (!nullArvot.isEmpty)
          virheet.incl(VALIDATION_METADATA_NULL + nullArvot.mkString(","))
        else virheet)
      .map(virheet =>
        // tutkitaan onko metadatassa duplikaattiarvoja
        val duplikaattiArvot = metadata.get.asScala
          .filter(entry => entry._2 != null && entry._2.size>entry._2.asScala.toSet.size)
          .map(entry => entry._1)
        if (!duplikaattiArvot.isEmpty)
          virheet.incl(VALIDATION_METADATA_DUPLICATE + duplikaattiArvot.mkString(","))
        else virheet)
      .map(virheet =>
        // liian monta metadata-arvoa ei ole sallittu
        if(metadata.get.size>VIESTI_METADATA_ARVOT_MAX_MAARA) virheet.incl(VALIDATION_METADATA_ARVOT_MAARA) else virheet)
      .map(virheet =>
        // validoidaan yksittäiset metadata-avaimet
        metadata.get.asScala.filter((avain, arvot) => arvot!=null).foldLeft(virheet)((virheet, metadataElementti) =>
          val (avain, arvot) = metadataElementti
          val avainVirheet = Some(Set.empty.asInstanceOf[Set[String]])
            .map(avainVirheet =>
              if(!metadataAvainPattern.matches(avain)) avainVirheet.incl(VALIDATION_METADATA_AVAIN_INVALID) else avainVirheet)
            .map(avainVirheet =>
              if(avain.length>VIESTI_METADATA_AVAIN_MAX_PITUUS) avainVirheet.incl(VALIDATION_METADATA_AVAIN_PITUUS) else avainVirheet)
            .map(avainVirheet =>
              if(arvot.size>VIESTI_METADATA_ARVOT_MAX_MAARA)
                avainVirheet.incl(VALIDATION_METADATA_ARVOT_MAARA) else avainVirheet)
            .map(avainVirheet =>
              val arvoVirheet = arvot.asScala.foldLeft(Set.empty.asInstanceOf[Set[String]])((arvoVirheet, arvo) =>
                Some(arvoVirheet).map(arvoVirheet =>
                  if(arvo!=null && arvo.length>VIESTI_METADATA_ARVO_MAX_PITUUS)
                    arvoVirheet.incl(VALIDATION_METADATA_ARVO_PITUUS + arvo)
                  else arvoVirheet
                ).map(arvoVirheet =>
                  if(arvo!=null && !metadataArvoPattern.matches(arvo))
                    arvoVirheet.incl(VALIDATION_METADATA_ARVO_INVALID + arvo)
                  else arvoVirheet
                ).get
              )
              avainVirheet.concat(arvoVirheet))
            .get
          if(!avainVirheet.isEmpty)
            virheet.incl("Metadata \"" + avain + "\": " + avainVirheet.mkString(",")) else virheet))
      .fold(l => l, r => r)

  val organisaatioOidPattern: Regex = "^1\\.2\\.246\\.562\\.(10|99)\\.\\d+$".r
  def validateKayttooikeusRajoitukset(kayttooikeusRajoitukset: Optional[List[Kayttooikeus]]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        // on ok jos käyttöoikeusrajoituksia ei määritelty
        if (kayttooikeusRajoitukset.isEmpty) Left(virheet) else Right(virheet))
      .map(virheet =>
        // käyttöoikeuksia ei voi olla määrättömästi
        if(kayttooikeusRajoitukset.get.size>VIESTI_KAYTTOOIKEUS_MAX_MAARA)
          virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA)
        else virheet)
      .map(virheet =>
        // tarkastetaan onko käyttöoikeusrajoituslistalla null-arvoja
        if (kayttooikeusRajoitukset.get.stream().filter(tunniste => tunniste == null).count() > 0)
          virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)
        else virheet)
      .map(virheet =>
        // validoidaan yksittäiset käyttöoikeudet
        val rajoitusVirheet = kayttooikeusRajoitukset.get.asScala.toSet.filter(rajoitus => rajoitus!=null)
          .foldLeft(virheet)((virheet, rajoitus) =>
            val rajoitusVirheet = Some(Set.empty.asInstanceOf[Set[String]])
              .map(rajoitusVirheet =>
                if (rajoitus.getOrganisaatio.isEmpty || !organisaatioOidPattern.matches(rajoitus.getOrganisaatio.get))
                  rajoitusVirheet.incl(VALIDATION_ORGANISAATIO_INVALID) else rajoitusVirheet)
              .map(rajoitusVirheet =>
                if(rajoitus.getOrganisaatio.isPresent && rajoitus.getOrganisaatio.get.length>VIESTI_ORGANISAATIO_MAX_PITUUS)
                  rajoitusVirheet.incl(VALIDATION_ORGANISAATIO_PITUUS) else rajoitusVirheet)
              .map(rajoitusVirheet =>
                if (rajoitus.getOikeus.isEmpty || rajoitus.getOikeus.get.length==0)
                  rajoitusVirheet.incl(VALIDATION_OIKEUS_TYHJA) else rajoitusVirheet)
              .map(rajoitusVirheet =>
                if (rajoitus.getOikeus.isPresent && rajoitus.getOikeus.get.length > VIESTI_OIKEUS_MAX_PITUUS)
                  rajoitusVirheet.incl(VALIDATION_OIKEUS_PITUUS) else rajoitusVirheet).get
            if (!rajoitusVirheet.isEmpty)
              virheet.incl("Käyttöoikeusrajoitus (" + rajoitus.getOrganisaatio.orElse("\"\"") + "," + rajoitus.getOikeus.orElse("\"\"") + "): " + rajoitusVirheet.mkString(","))
            else virheet)
        virheet.concat(rajoitusVirheet))
      .map(virheet =>
        // tutkitaan onko käyttöoikeusrajoituslistalla duplikaatteja
        val duplikaattiRajoitukset = kayttooikeusRajoitukset.get.asScala
          .filter(rajoitus => rajoitus != null)
          .groupBy(rajoitus => rajoitus)
          .filter(rajoitusByRajoitus => rajoitusByRajoitus._2.size > 1)
          .map(rajoitusByRajoitus => rajoitusByRajoitus._1)
        if (!duplikaattiRajoitukset.isEmpty)
          virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaattiRajoitukset.mkString(","))
        else virheet)
      .fold(l => l, r => r)

  val idempotencyPattern: Regex = "[A-Za-z0-9\\-\\._]+".r
  def validateIdempotencyKey(idempotencyKey: Optional[String]): Set[String] =
    if(idempotencyKey.isEmpty)
      Set.empty
    else
      Right(Set.empty.asInstanceOf[Set[String]])
        .map(virheet =>
          if(idempotencyKey.get.length > VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS) virheet.incl(VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA) else virheet)
        .map(virheet =>
          if (!idempotencyPattern.matches(idempotencyKey.get()))
            virheet.incl(VALIDATION_IDEMPOTENCY_KEY_INVALID) else virheet)
        .fold(l => l, r => r)

  def validateLahetysJaPeritytKentat(lahetysTunniste: Optional[String], lahettavaPalvelu: Optional[String],
                                     lahettavanVirkailijanOid: Optional[String], lahettaja: Optional[Lahettaja],
                                     replyTo: Optional[String], prioriteetti: Optional[String], sailytysaika: Optional[Integer]
                                    ): Set[String] =
    val lahetysMaaritelty = !(lahetysTunniste.isEmpty || "".equals(lahetysTunniste.get))
    if(lahetysMaaritelty)
      // lähetyksen kenttiä ei voi määritellä viestikohtaisesti jos se peritään lähetykseltä
      Some(Set.empty.asInstanceOf[Set[String]])
        .map(virheet =>
          if(lahettavaPalvelu.isPresent)
            Set(virheet.incl(VALIDATION_LAHETTAVAPALVELU_EI_TYHJA), LahetysValidator.validateLahettavaPalvelu(lahettavaPalvelu)).flatten
          else virheet)
        .map(virheet =>
          if(lahettavanVirkailijanOid.isPresent)
            Set(virheet.incl(VALIDATION_VIRKAILIJANOID_EI_TYHJA), LahetysValidator.validateLahettavanVirkailijanOID(lahettavanVirkailijanOid)).flatten
          else virheet)
        .map(virheet =>
          if(lahettaja.isPresent)
            Set(virheet.incl(VALIDATION_LAHETTAJA_EI_TYHJA), LahetysValidator.validateLahettaja(lahettaja)).flatten
          else virheet)
        .map(virheet =>
          if (replyTo.isPresent)
            Set(virheet.incl(VALIDATION_REPLYTO_EI_TYHJA), LahetysValidator.validateReplyTo(replyTo)).flatten
          else virheet)
        .map(virheet =>
          if (prioriteetti.isPresent)
            Set(virheet.incl(VALIDATION_PRIORITEETTI_EI_TYHJA), LahetysValidator.validatePrioriteetti(prioriteetti)).flatten
          else virheet)
        .map(virheet =>
          if (sailytysaika.isPresent)
            Set(virheet.incl(VALIDATION_SAILYTYSAIKA_EI_TYHJA), LahetysValidator.validateSailytysAika(sailytysaika)).flatten
          else virheet)
        .get
    else
      LahetysValidator.validateLahetys(LahetysImpl(Optional.of("DUMMY OTSIKKO"), lahettavaPalvelu, lahettavanVirkailijanOid, lahettaja, replyTo, prioriteetti, sailytysaika))

  def validateKorkeaPrioriteetti(prioriteetti: Optional[String], vastaanottajat: Optional[List[Vastaanottaja]], lahetysMetadata: Option[LahetysMetadata]): Set[String] =
    val korkeaPrioriteetti = (lahetysMetadata.isDefined && lahetysMetadata.get.korkeaPrioriteetti) || (prioriteetti.isPresent && prioriteetti.get.equals(LAHETYS_PRIORITEETTI_KORKEA))
    if(!korkeaPrioriteetti)
      Set.empty
    else if(vastaanottajat.isPresent && vastaanottajat.get.size()>1)
      Set(VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT)
    else
      Set.empty

  def validateKoko(sisalto: String, liiteTunnisteet: List[String], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Set[String] =
    val liitteidenKoko = liiteTunnisteet.asScala.toSet
      .map(tunniste => liiteMetadatat.get(UUID.fromString(tunniste)))
      .filter(metadata => metadata.isDefined && metadata.get.omistaja.equals(identiteetti))
      .map(metadata => metadata.get.koko)
      .sum

    if(sisalto.length + liitteidenKoko > ViestiImpl.VIESTI_MAX_SIZE)
      Set(VALIDATION_KOKO)
    else
      Set.empty

  def validateViesti(viesti: Viesti, lahetysMetadata: Option[LahetysMetadata], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Seq[String] =
    Seq(
      // validoidaan yksittäiset kentät
      validateOtsikko(viesti.getOtsikko),
      validateSisalto(viesti.getSisalto),
      validateSisallonTyyppi(viesti.getSisallonTyyppi),
      validateKielet(viesti.getKielet),
      validateMaskit(viesti.getMaskit),
      validateVastaanottajat(viesti.getVastaanottajat),
      validateLiitteidenTunnisteet(viesti.getLiitteidenTunnisteet, liiteMetadatat, identiteetti),
      validateLahetysTunniste(viesti.getLahetysTunniste, lahetysMetadata, identiteetti),
      validateMetadata(viesti.getMetadata),
      validateKayttooikeusRajoitukset(viesti.getKayttooikeusRajoitukset),

      // validoidaan kenttien väliset suhteet
      validateKorkeaPrioriteetti(viesti.getPrioriteetti, viesti.getVastaanottajat, lahetysMetadata),
      validateLahetysJaPeritytKentat(viesti.getLahetysTunniste, viesti.getLahettavaPalvelu, viesti.getLahettavanVirkailijanOid,
        viesti.getLahettaja, viesti.getReplyTo, viesti.getPrioriteetti, viesti.getSailytysaika)
    ).flatten


end ViestiValidator

