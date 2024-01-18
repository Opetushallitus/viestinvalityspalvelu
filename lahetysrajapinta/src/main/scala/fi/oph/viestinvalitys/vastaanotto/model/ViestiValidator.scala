package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.{LAHETYS_PRIORITEETTI_KORKEA, LAHETYS_PRIORITEETTI_NORMAALI}
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.*
import fi.oph.viestinvalitys.vastaanotto.model.ViestiImpl.*
import org.apache.commons.validator.routines.EmailValidator

import java.util.stream.Collectors
import java.util.{List, Optional, UUID}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
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
 * Validoi järjestelmään syötetyn viestin kentät
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

  final val VALIDATION_METADATA_NULL                      = "metadata: Seuraavat avaimet sisältävät null-arvoja: "
  final val VALIDATION_METADATA_DUPLICATE                 = "metadata: Seuraavat avaimet sisältää duplikaattiarvoja: "
  final val VALIDATION_METADATA_AVAIMET_MAARA             = "metadata: Metadata voi sisältää maksimissaan " + VIESTI_METADATA_AVAIMET_MAX_MAARA + " avainta"
  final val VALIDATION_METADATA_AVAIN_PITUUS              = "avain on yli maksimipituuden " + VIESTI_METADATA_AVAIN_MAX_PITUUS + " merkkiä"
  final val VALIDATION_METADATA_ARVOT_MAARA               = "avain sisältää yli " + VIESTI_METADATA_ARVOT_MAX_MAARA + " arvoa"
  final val VALIDATION_METADATA_ARVO_PITUUS               = "arvo on yli maksimipituuden " + VIESTI_METADATA_ARVO_MAX_PITUUS + " merkkiä: "

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL          = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA        = "kayttooikeusRajoitukset: Viestillä voi maksimissaan olla " + VIESTI_KAYTTOOIKEUS_MAX_MAARA + " käyttöoikeusrajoitusta"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE     = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: "
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID       = "käyttöoikeusrajoitus ei ole organisaatiorajoitettu (ts. ei pääty _<oid>)"

  final val VALIDATION_LAHETTAVAPALVELU_EI_TYHJA          = "lahettavapalvelu: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_VIRKAILIJANOID_EI_TYHJA            = "lähettävän virkailijan oid: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_LAHETTAJA_EI_TYHJA                 = "lähettäjä: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_REPLYTO_EI_TYHJA                   = "replyTo: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_PRIORITEETTI_EI_TYHJA              = "prioriteetti: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"
  final val VALIDATION_SAILYTYSAIKA_EI_TYHJA              = "sailytysaika: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"

  final val VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT = "prioriteetti: Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja"

  final val VALIDATION_KOKO                               = "koko: viestin ja liitteiden koko on suurempi kuin " + VIESTI_MAX_SIZE_MB_STR + " megatavua"

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    var errors: Set[String] = Set.empty

    if(otsikko.isEmpty || otsikko.get.length==0)
      errors = errors.incl(VALIDATION_OTSIKKO_TYHJA)
    else if(otsikko.get.length > OTSIKKO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_OTSIKKO_LIIAN_PITKA)

    errors

  def validateSisalto(sisalto: Optional[String]): Set[String] =
    var errors: Set[String] = Set.empty

    if (sisalto.isEmpty || sisalto.get.length == 0)
      errors = errors.incl(VALIDATION_SISALTO_TYHJA)
    else if (sisalto.get.length > SISALTO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_SISALTO_LIIAN_PITKA)

    errors

  def validateSisallonTyyppi(sisallonTyyppi: Optional[String]): Set[String] =
    if(sisallonTyyppi.isEmpty || (!sisallonTyyppi.get.equals(VIESTI_SISALTOTYYPPI_TEXT) && !sisallonTyyppi.get.equals(VIESTI_SISALTOTYYPPI_HTML)))
      Set(VALIDATION_SISALLONTYYPPI)
    else
      Set.empty

  final val SALLITUT_KIELET = Set("fi", "sv", "en")
  def validateKielet(kielet: Optional[List[String]]): Set[String] =
    if(kielet.isEmpty || kielet.get.isEmpty)
      return Set.empty

    // validoidaan yksittäiset kielet
    var virheet: Set[String] = Set.empty
    kielet.get.asScala.map(kieli => {
      if(kieli!=null && !SALLITUT_KIELET.contains(kieli))
        virheet = virheet.incl(VALIDATION_KIELI_EI_SALLITTU + kieli)
    })

    // tutkitaan onko kielissä null-arvoja
    if (kielet.get.stream().filter(kieli => kieli == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KIELI_NULL)

    // tutkitaan onko kielissä duplikaatteja
    val duplikaattiKielet = kielet.get.asScala
      .filter(kieli => kieli != null)
      .groupBy(kieli => kieli)
      .filter(kielet => kielet._2.size > 1)
      .map(kielet => kielet._1)
    if (!duplikaattiKielet.isEmpty)
      virheet = virheet.incl(VALIDATION_KIELI_DUPLICATES +
        duplikaattiKielet.mkString(","))

    virheet

  def validateMaskit(maskit: Optional[List[Maski]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok että maskeja ei ole määritelty
    if(maskit.isEmpty)
      return Set.empty

    // maskeja ei voi olla määrättömästi
    if(maskit.get.size()>VIESTI_MASKIT_MAX_MAARA)
      virheet = virheet.incl(VALIDATION_MASKIT_LIIKAA)

    // tarkastetaan onko maskilistalla null-arvoja
    if (maskit.get.stream().filter(maski => maski == null).count() > 0)
      virheet = virheet.incl(VALIDATION_MASKIT_NULL)

    // validoidaan yksittäiset maskit
    maskit.get.asScala.toSet.map(maski => {
      if (maski != null) {
        var maskiVirheet: Set[String] = Set.empty

        if(maski.getSalaisuus.isEmpty || maski.getSalaisuus.get.length==0)
          maskiVirheet = maskiVirheet.incl(VALIDATION_MASKIT_EI_SALAISUUTTA)
        else if(maski.getSalaisuus.get.length < VIESTI_SALAISUUS_MIN_PITUUS || maski.getSalaisuus.get.length > VIESTI_SALAISUUS_MAX_PITUUS)
          maskiVirheet = maskiVirheet.incl(VALIDATION_MASKIT_SALAISUUS_PITUUS)

        if(maski.getMaski.isPresent && (maski.getMaski.get.length < VIESTI_MASKI_MIN_PITUUS || maski.getMaski.get.length > VIESTI_MASKI_MAX_PITUUS))
          maskiVirheet = maskiVirheet.incl(VALIDATION_MASKIT_MASKI_PITUUS)

        if (!maskiVirheet.isEmpty)
          virheet = virheet.incl("Maski (salaisuus: " + maski.getSalaisuus.map(s => "*".repeat(s.length)).orElse("") +
            ", maski: " + maski.getMaski.orElse("") + "): " + maskiVirheet.mkString(","))
      }
    })

    // tutkitaan onko maskeissa duplikaatteja
    val duplikaattiMaskit = maskit.get.asScala
      .filter(maski => maski != null && maski.getSalaisuus.isPresent)
      .groupBy(maski => maski.getSalaisuus)
      .filter(maskitBySalaisuus => maskitBySalaisuus._2.size > 1)
      .map(maskitBySalaisuus => maskitBySalaisuus._1.get())
    if (!duplikaattiMaskit.isEmpty)
      virheet = virheet.incl(VALIDATION_MASKIT_DUPLICATES +
        duplikaattiMaskit.map(s => "*".repeat(s.length)).mkString(","))

    virheet

  def validateVastaanottajat(vastaanottajat: Optional[List[Vastaanottaja]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // vastaanottajat kenttä pitää olla määritelty
    if(vastaanottajat.isEmpty || vastaanottajat.get().isEmpty)
      return Set(VALIDATION_VASTAANOTTAJAT_TYHJA)

    // vastaanottajien määrälle on yläraja
    if(vastaanottajat.get.size>VIESTI_VASTAANOTTAJAT_MAX_MAARA)
      virheet= virheet.incl(VALIDATION_VASTAANOTTAJAT_LIIKAA)

    // tarkastetaan onko vastaanottajalistalla null-arvoja
    if(vastaanottajat.get.stream().filter(vastaanottaja => vastaanottaja==null).count()>0)
      virheet = virheet.incl(VALIDATION_VASTAANOTTAJA_NULL)

    // validoidaan yksittäiset vastaanottajat
    vastaanottajat.get.asScala.toSet.map(vastaanottaja => {
      if(vastaanottaja!=null) {
        var vastaanottajaVirheet: Set[String] = Set.empty
        if(vastaanottaja.getNimi.isPresent && vastaanottaja.getNimi.get.length>VIESTI_NIMI_MAX_PITUUS)
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA)
        if (vastaanottaja.getSahkopostiOsoite.isEmpty || vastaanottaja.getSahkopostiOsoite.get.length == 0)
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA)
        else if (!EmailValidator.getInstance().isValid(vastaanottaja.getSahkopostiOsoite.get))
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID)

        if (!vastaanottajaVirheet.isEmpty)
          virheet = virheet.incl("Vastaanottaja (nimi: " + vastaanottaja.getNimi.orElse("") + ", sähköpostiosoite: " + vastaanottaja.getSahkopostiOsoite + "): " +
            vastaanottajaVirheet.asJava.stream().collect(Collectors.joining(",")))
      }
    })

    // tutkitaan onko osoitteissa duplikaatteja
    val duplikaattiOsoitteet = vastaanottajat.get.asScala
      .filter(vastaanottaja => vastaanottaja!=null && vastaanottaja.getSahkopostiOsoite.isPresent)
      .groupBy(vastaanottaja => vastaanottaja.getSahkopostiOsoite)
      .filter(vastaanottajatByOsoite => vastaanottajatByOsoite._2.size>1)
      .map(vastaanottajatByOsoite => vastaanottajatByOsoite._1.get())
    if(!duplikaattiOsoitteet.isEmpty)
      virheet = virheet.incl(VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + duplikaattiOsoitteet.mkString(","))

    virheet

  def validateLiitteidenTunnisteet(tunnisteet: Optional[List[String]], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok jos tunnisteitä ei määritelty
    if(tunnisteet.isEmpty)
      return virheet

    if(tunnisteet.get.size()>VIESTI_LIITTEET_MAX_MAARA)
      virheet = virheet.incl(VALIDATION_LIITETUNNISTE_LIIKAA)

    // tarkastetaan onko liitetunnistelistalla null-arvoja
    if(tunnisteet.get.stream().filter(tunniste => tunniste==null).count()>0)
      virheet = virheet.incl(VALIDATION_LIITETUNNISTE_NULL)

    // validoidaan yksittäiset liitetunnisteet
    tunnisteet.get.asScala.toSet.map(tunniste => {
      if (tunniste != null) {
        var tunnisteVirheet: Set[String] = Set.empty
        try
          UUID.fromString(tunniste)

          val liiteMetadata = liiteMetadatat.get(UUID.fromString(tunniste))
          if(liiteMetadata.isEmpty || !identiteetti.equals(liiteMetadata.get.omistaja))
            tunnisteVirheet = tunnisteVirheet.incl(VALIDATION_LIITETUNNISTE_EI_TARJOLLA)
        catch
          case e: Exception => tunnisteVirheet = tunnisteVirheet.incl(VALIDATION_LIITETUNNISTE_INVALID)

        if(!tunnisteVirheet.isEmpty)
          virheet = virheet.incl("Liitetunniste \"" + tunniste + "\": " +
            tunnisteVirheet.asJava.stream().collect(Collectors.joining(",")))
      }
    })

    // tutkitaan onko tunnistelistalla duplikaatteja
    val duplikaattiTunnisteet = tunnisteet.get.asScala
      .filter(tunniste => tunniste != null)
      .groupBy(tunniste => tunniste)
      .filter(tunnisteByTunniste => tunnisteByTunniste._2.size > 1)
      .map(tunnisteByTunniste => tunnisteByTunniste._1)
    if (!duplikaattiTunnisteet.isEmpty)
      virheet = virheet.incl(VALIDATION_LIITETUNNISTE_DUPLICATE + duplikaattiTunnisteet.asJavaCollection.stream().collect(Collectors.joining(",")))

    virheet

  def validateLahetysTunniste(tunniste: Optional[String], lahetysMetadata: Option[LahetysMetadata], identiteetti: String): Set[String] =
    if(tunniste.isEmpty || "".equals(tunniste.get)) return Set.empty

    try
      UUID.fromString(tunniste.get)

      if (lahetysMetadata.isEmpty || !lahetysMetadata.get.omistaja.equals(identiteetti))
        return Set(VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA)
    catch
      case e: Exception => return Set(VALIDATION_LAHETYSTUNNISTE_INVALID)

    Set.empty

  def validateMetadata(metadata: Optional[java.util.Map[String, List[String]]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // metadataa ei pakko määritellä
    if(metadata.isEmpty) return Set.empty

    // tutkitaan onko metadatassa null-arvoja
    val nullArvot = metadata.get.asScala
      .filter(entry => entry._2==null || !entry._2.stream().filter(arvo => arvo==null).toList().isEmpty)
      .map(entry => entry._1)
    if (!nullArvot.isEmpty)
      virheet = virheet.incl(VALIDATION_METADATA_NULL + nullArvot.asJavaCollection.stream().collect(Collectors.joining(",")))

    // tutkitaan onko metadatassa duplikaattiarvoja
    val duplikaattiArvot = metadata.get.asScala
      .filter(entry => entry._2 != null && entry._2.size>entry._2.asScala.toSet.size)
      .map(entry => entry._1)
    if (!duplikaattiArvot.isEmpty)
      virheet = virheet.incl(VALIDATION_METADATA_DUPLICATE + duplikaattiArvot.asJavaCollection.stream().collect(Collectors.joining(",")))

    // liian monta metadata-arvoa ei ole sallittu
    if(metadata.get.size>VIESTI_METADATA_ARVOT_MAX_MAARA)
      virheet = virheet.incl(VALIDATION_METADATA_ARVOT_MAARA)

    // validoidaan yksittäiset metadata-avaimet
    metadata.get.asScala.map((avain, arvot) => {
      var avainVirheet: Set[String] = Set.empty

      if(avain.length>VIESTI_METADATA_AVAIN_MAX_PITUUS)
        avainVirheet = avainVirheet.incl(VALIDATION_METADATA_AVAIN_PITUUS)

      if(arvot!=null)
        if(arvot.size>VIESTI_METADATA_ARVOT_MAX_MAARA)
          avainVirheet = avainVirheet.incl(VALIDATION_METADATA_ARVOT_MAARA)

        arvot.asScala.foreach(arvo => {
          if(arvo!=null && arvo.length>VIESTI_METADATA_ARVO_MAX_PITUUS)
            avainVirheet = avainVirheet.incl(VALIDATION_METADATA_ARVO_PITUUS + arvo)
        })

      if (!avainVirheet.isEmpty)
        virheet = virheet.incl("Metadata \"" + avain + "\": " +
          avainVirheet.asJava.stream().collect(Collectors.joining(",")))
    })

    virheet

  val kayttooikeusPattern: Regex = ("^.*_[0-9]+(\\.[0-9]+)+$").r
  def validateKayttooikeusRajoitukset(kayttooikeusRajoitukset: Optional[List[String]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok jos käyttöoikeusrajoituksia ei määritelty
    if (kayttooikeusRajoitukset.isEmpty)
      return virheet

    // käyttöoikeuksia ei voi olla määrättömästi
    if(kayttooikeusRajoitukset.get.size>VIESTI_KAYTTOOIKEUS_MAX_MAARA)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA)

    // tarkastetaan onko käyttöoikeusrajoituslistalla null-arvoja
    if (kayttooikeusRajoitukset.get.stream().filter(tunniste => tunniste == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)

    // validoidaan yksittäiset käyttöoikeudet
    kayttooikeusRajoitukset.get.asScala.toSet.map(rajoitus => {
      if (rajoitus != null) {
        var rajoitusVirheet: Set[String] = Set.empty

        if (!kayttooikeusPattern.matches(rajoitus))
          rajoitusVirheet = rajoitusVirheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID)

        if (!rajoitusVirheet.isEmpty)
          virheet = virheet.incl("Käyttöoikeusrajoitus \"" + rajoitus + "\": " +
            rajoitusVirheet.mkString(","))
      }
    })

    // tutkitaan onko käyttöoikeusrajoituslistalla duplikaatteja
    val duplikaattiRajoitukset = kayttooikeusRajoitukset.get.asScala
      .filter(rajoitus => rajoitus != null)
      .groupBy(rajoitus => rajoitus)
      .filter(rajoitusByRajoitus => rajoitusByRajoitus._2.size > 1)
      .map(rajoitusByRajoitus => rajoitusByRajoitus._1)
    if (!duplikaattiRajoitukset.isEmpty)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaattiRajoitukset.mkString(","))

    virheet

  def validateLahetysJaPeritytKentat(lahetysTunniste: Optional[String], lahettavaPalvelu: Optional[String],
                                     lahettavanVirkailijanOid: Optional[String], lahettaja: Optional[Lahettaja],
                                     replyTo: Optional[String], prioriteetti: Optional[String], sailytysaika: Optional[Integer]
                                    ): Set[String] =
    var virheet: Set[String] = Set.empty

    val lahetysMaaritelty = !(lahetysTunniste.isEmpty || "".equals(lahetysTunniste.get))
    if(lahetysMaaritelty)
      // lähetyksen kenttiä ei voi määritellä viestikohtaisesti jos se peritään lähetykseltä
      if (lahettavaPalvelu.isPresent)
        virheet = virheet.incl(VALIDATION_LAHETTAVAPALVELU_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validateLahettavaPalvelu(lahettavaPalvelu)).flatten
      if (lahettavanVirkailijanOid.isPresent)
        virheet = virheet.incl(VALIDATION_VIRKAILIJANOID_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validateLahettavanVirkailijanOID(lahettavanVirkailijanOid)).flatten
      if (lahettaja.isPresent)
        virheet = virheet.incl(VALIDATION_LAHETTAJA_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validateLahettaja(lahettaja)).flatten
      if (replyTo.isPresent)
        virheet = virheet.incl(VALIDATION_REPLYTO_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validateReplyTo(replyTo)).flatten
      if (prioriteetti.isPresent)
        virheet = virheet.incl(VALIDATION_PRIORITEETTI_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validatePrioriteetti(prioriteetti)).flatten
      if (sailytysaika.isPresent)
        virheet = virheet.incl(VALIDATION_SAILYTYSAIKA_EI_TYHJA)
        virheet = Set(virheet, LahetysValidator.validateSailytysAika(sailytysaika)).flatten
      virheet
    else
      LahetysValidator.validateLahetys(LahetysImpl(Optional.of("DUMMY OTSIKKO"), lahettavaPalvelu, lahettavanVirkailijanOid, lahettaja, replyTo, prioriteetti, sailytysaika))

  def validateKorkeaPrioriteetti(prioriteetti: Optional[String], vastaanottajat: Optional[List[Vastaanottaja]], lahetysMetadata: Option[LahetysMetadata]): Set[String] =
    val korkeaPrioriteetti = (lahetysMetadata.isDefined && lahetysMetadata.get.korkeaPrioriteetti) || (prioriteetti.isPresent && prioriteetti.get.equals(LAHETYS_PRIORITEETTI_KORKEA))
    if(!korkeaPrioriteetti)
      return Set.empty

    if(vastaanottajat.isPresent && vastaanottajat.get.size()>1)
      return Set(VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT)

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

