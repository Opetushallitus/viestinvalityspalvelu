package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.ViestiValidator.validateLahetysJaKayttooikeusRajoitukset
import org.apache.commons.validator.routines.EmailValidator

import java.util.{List, Optional, UUID}
import java.util.stream.Collectors
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
case class LahetysMetadata(omistaja: String)

/**
 * Validoi järjestelmään syötetyn viestin kentät
 */
object ViestiValidator:

  final val VIESTI_MAX_SIZE = VIESTI_MAX_SIZE_MB_STR.toInt * 1024 * 1024
  final val VIESTI_MAX_SIZE_MB_STR = "8"

  final val VALIDATION_OTSIKKO_TYHJA                      = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA                = "otsikko: Otsikko ei voi pidempi kuin " + Viesti.OTSIKKO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALTO_TYHJA                      = "sisalto: Kenttä on pakollinen"
  final val VALIDATION_SISALTO_LIIAN_PITKA                = "sisalto: Sisältö ei voi pidempi kuin " + Viesti.SISALTO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALLONTYYPPI                     = "sisallonTyyppi: Sisällön tyypin täytyy olla joko \"" + Viesti.VIESTI_SISALTOTYYPPI_TEXT + "\" tai \"" + Viesti.VIESTI_SISALTOTYYPPI_HTML + "\""

  final val VALIDATION_KIELET_TYHJA                       = "kielet: Kenttä on pakollinen"
  final val VALIDATION_KIELI_EI_SALLITTU                  = "kielet: Kieli ei ole sallittu (\"fi\", \"sv\" ja \"en\"): "
  final val VALIDATION_KIELI_NULL                         = "kielet: Kenttä sisältää null-arvoja"

  final val VALIDATION_MASKIT_NULL                        = "maskit: Kenttä sisältää null-arvoja"
  final val VALIDATION_MASKIT_EI_SALAISUUTTA              = "salaisuus-kenttä on pakollinen"
  final val VALIDATION_MASKIT_DUPLICATES                  = "maskit: Maskit-kentissä on duplikaatteja: "

  final val VALIDATION_LAHETTAJAN_OID                     = "lähettäjänOid: Oid ei ole validi (1.2.246.562-alkuinen) oph-oid"

  final val VALIDATION_OPH_OID_PREFIX                     = "1.2.246.562"

  final val VALIDATION_LAHETTAJA_TYHJA                    = "lähettäjä: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_NIMI_TYHJA              = "lähettäjä: Lähettäjän nimi -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_TYHJA            = "lähettäjä: Lähettäjän sähköpostiosoite -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_INVALID          = "lähettäjä: Lähettäjän sähköpostiosoite ei ole validi sähköpostiosoite"
  final val VALIDATION_LAHETTAJAN_OSOITE_DOMAIN           = "lähettäjä: Lähettäjän sähköpostiosoite ei ole opintopolku.fi -domainissa"

  final val VALIDATION_REPLYTO_INVALID                    = "replyTo: arvo ei ole validi sähköpostiosoite"

  final val VALIDATION_VASTAANOTTAJAT_TYHJA               = "vastaanottajat: Kenttä on pakollinen"
  final val VALIDATION_VASTAANOTTAJA_NULL                 = "vastaanottajat: Kenttä sisältää null-arvoja"
  final val VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE     = "vastaanottajat: Osoite-kentissä on duplikaatteja: "
  final val VALIDATION_VASTAANOTTAJAN_NIMI_TYHJA          = "nimi-kenttä on pakollinen"
  final val VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA        = "sähköpostiosoite-kenttä on pakollinen"
  final val VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID      = "sähköpostiosoite ei ole validi sähköpostiosoite"

  final val VALIDATION_LIITETUNNISTE_NULL                 = "liiteTunnisteet: Kenttä sisältää null-arvoja"
  final val VALIDATION_LIITETUNNISTE_DUPLICATE            = "liiteTunnisteet: Kentässä on duplikaatteja: "
  final val VALIDATION_LIITETUNNISTE_INVALID              = "liitetunniste ei ole muodoltaan validi liitetunniste"
  final val VALIDATION_LIITETUNNISTE_EI_TARJOLLA          = "liitetunnistetta vastaavaa liitettä ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia"

  final val VALIDATION_LAHETTAVA_PALVELU_TYHJA            = "lahettavaPalvelu: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA      = "lahettavaPalvelu: Kentän pituus voi olla korkeintaan " + Viesti.LAHETTAVAPALVELU_MAX_PITUUS + " merkkiä"
  final val VALIDATION_LAHETTAVA_PALVELU_INVALID          = "lahettavaPalvelu: Arvo ei ole validi käännösavain"

  final val VALIDATION_LAHETYSTUNNISTE_INVALID            = "lähetysTunniste: arvo ei ole muodoltaan validi lähetysTunniste"
  final val VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA        = "lähetysTunniste: tunnistetta ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia"

  final val VALIDATION_PRIORITEETTI                       = "prioriteetti: Prioriteetti täytyy olla joko \"" + Viesti.VIESTI_PRIORITEETTI_NORMAALI+ "\" tai \"" + Viesti.VIESTI_PRIORITEETTI_KORKEA + "\""

  final val VALIDATION_SAILYTYSAIKA_TYHJA                 = "sailytysAika: Kenttä on pakollinen"
  final val VALIDATION_SAILYTYSAIKA                       = "sailytysAika: Säilytysajan tulee olla " + Viesti.SAILYTYSAIKA_MIN_PITUUS + "-" + Viesti.SAILYTYSAIKA_MAX_PITUUS + " päivää"

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL          = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE     = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: "
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID       = "käyttöoikeusrajoitus ei ole organisaatiorajoitettu (ts. ei pääty _<oid>)"

  final val VALIDATION_METADATA_NULL                      = "metadata: Seuraavat avaimet sisältävät null-arvoja: "
  final val VALIDATION_METADATA_DUPLICATE                 = "metadata: Seuraavat avaimet sisältää duplikaattiarvoja: "

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_EI_TYHJA      = "kayttooikeusRajoitukset: Kentän pitää olla tyhjä jos lähetystunniste on määritelty"

  final val VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT = "prioriteetti: Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja"

  final val VALIDATION_KOKO                               = "koko: viestin ja liitteiden koko on suurempi kuin " + ViestiValidator.VIESTI_MAX_SIZE_MB_STR + " megatavua"

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    var errors: Set[String] = Set.empty

    if(otsikko.isEmpty || otsikko.get.length==0)
      errors = errors.incl(VALIDATION_OTSIKKO_TYHJA)
    else if(otsikko.get.length > Viesti.OTSIKKO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_OTSIKKO_LIIAN_PITKA)

    errors

  def validateSisalto(sisalto: Optional[String]): Set[String] =
    var errors: Set[String] = Set.empty

    if (sisalto.isEmpty || sisalto.get.length == 0)
      errors = errors.incl(VALIDATION_SISALTO_TYHJA)
    else if (sisalto.get.length > Viesti.SISALTO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_SISALTO_LIIAN_PITKA)

    errors

  def validateSisallonTyyppi(sisallonTyyppi: Optional[String]): Set[String] =
    if(sisallonTyyppi.isEmpty || (!sisallonTyyppi.get.equals(Viesti.VIESTI_SISALTOTYYPPI_TEXT) && !sisallonTyyppi.get.equals(Viesti.VIESTI_SISALTOTYYPPI_HTML)))
      Set(VALIDATION_SISALLONTYYPPI)
    else
      Set.empty

  final val SALLITUT_KIELET = Set("fi", "sv", "en")
  def validateKielet(kielet: Optional[List[String]]): Set[String] =
    if(kielet.isEmpty || kielet.get.isEmpty) return Set(VALIDATION_KIELET_TYHJA)

    // validoidaan yksittäiset kielet
    var virheet: Set[String] = Set.empty
    kielet.get.asScala.map(kieli => {
      if(kieli!=null && !SALLITUT_KIELET.contains(kieli))
        virheet = virheet.incl(VALIDATION_KIELI_EI_SALLITTU + kieli)
    })

    // tutkitaan onko kielissä null-arvoja
    if (kielet.get.stream().filter(kieli => kieli == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KIELI_NULL)

    virheet

  def validateMaskit(maskit: Optional[List[Maski]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok että maskeja ei ole määritelty
    if(maskit.isEmpty)
      return Set.empty

    // tarkastetaan onko maskilistalla null-arvoja
    if (maskit.get.stream().filter(maski => maski == null).count() > 0)
      virheet = virheet.incl(VALIDATION_MASKIT_NULL)

    // validoidaan yksittäiset maskit
    maskit.get.asScala.toSet.map(maski => {
      if (maski != null) {
        var maskiVirheet: Set[String] = Set.empty

        if(maski.salaisuus.isEmpty || maski.salaisuus.get.length==0)
          maskiVirheet = maskiVirheet.incl(VALIDATION_MASKIT_EI_SALAISUUTTA)

        if (!maskiVirheet.isEmpty)
          virheet = virheet.incl("Maski (salaisuus: " + maski.salaisuus.map(s => "*".repeat(s.length)).orElse("") +
            ", maski: " + maski.maski.orElse("") + "): " + maskiVirheet.mkString(","))
      }
    })

    // tutkitaan onko maskeissa duplikaatteja
    val duplikaattiMaskit = maskit.get.asScala
      .filter(maski => maski != null && maski.salaisuus.isPresent)
      .groupBy(maski => maski.salaisuus)
      .filter(maskitBySalaisuus => maskitBySalaisuus._2.size > 1)
      .map(maskitBySalaisuus => maskitBySalaisuus._1.get())
    if (!duplikaattiMaskit.isEmpty)
      virheet = virheet.incl(VALIDATION_MASKIT_DUPLICATES +
        duplikaattiMaskit.map(s => "*".repeat(s.length)).mkString(","))

    virheet

  val oidPattern: Regex = (VALIDATION_OPH_OID_PREFIX + "(\\.[0-9]+)+").r
  def validateLahettavanVirkailijanOID(oid: Optional[String]): Set[String] =
    if(oid.isEmpty) return Set.empty
    if(oidPattern.matches(oid.get())) return Set.empty
    Set(VALIDATION_LAHETTAJAN_OID)

  def validateLahettaja(lahettaja: Optional[Lahettaja]): Set[String] =
    if(lahettaja.isEmpty) return Set(VALIDATION_LAHETTAJA_TYHJA)

    var virheet: Set[String] = Set.empty
    // TODO: validoi lähettäjän nimen max pituus
    if(lahettaja.get.sahkopostiOsoite.isEmpty || lahettaja.get.sahkopostiOsoite.get.length==0)
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_TYHJA)
    else if(!EmailValidator.getInstance(false).isValid(lahettaja.get.sahkopostiOsoite.get))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_INVALID)
    else if(!lahettaja.get.sahkopostiOsoite.get.endsWith("@opintopolku.fi"))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_DOMAIN)

    virheet

  def validateReplyTo(replyTo: Optional[String]): Set[String] =
    if (replyTo.isEmpty) return Set.empty

    if(!EmailValidator.getInstance(false).isValid(replyTo.get))
      return Set(VALIDATION_REPLYTO_INVALID)

    Set.empty

  def validateVastaanottajat(vastaanottajat: Optional[List[Vastaanottaja]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // vastaanottajat kenttä pitää olla määritelty
    if(vastaanottajat.isEmpty || vastaanottajat.get().isEmpty)
      return Set(VALIDATION_VASTAANOTTAJAT_TYHJA)

    // tarkastetaan onko vastaanottajalistalla null-arvoja
    if(vastaanottajat.get.stream().filter(vastaanottaja => vastaanottaja==null).count()>0)
      virheet = virheet.incl(VALIDATION_VASTAANOTTAJA_NULL)

    // validoidaan yksittäiset vastaanottajat
    vastaanottajat.get.asScala.toSet.map(vastaanottaja => {
      if(vastaanottaja!=null) {
        var vastaanottajaVirheet: Set[String] = Set.empty
        // TODO: validoi vastaanottajan nimen max pituus
        if (vastaanottaja.sahkopostiOsoite.isEmpty || vastaanottaja.sahkopostiOsoite.get.length == 0)
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA)
        else if (!EmailValidator.getInstance().isValid(vastaanottaja.sahkopostiOsoite.get))
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID)

        if (!vastaanottajaVirheet.isEmpty)
          virheet = virheet.incl("Vastaanottaja (nimi: " + vastaanottaja.nimi.orElse("") + ", sähköpostiosoite: " + vastaanottaja.sahkopostiOsoite + "): " +
            vastaanottajaVirheet.asJava.stream().collect(Collectors.joining(",")))
      }
    })

    // tutkitaan onko osoitteissa duplikaatteja
    val duplikaattiOsoitteet = vastaanottajat.get.asScala
      .filter(vastaanottaja => vastaanottaja!=null && vastaanottaja.sahkopostiOsoite.isPresent)
      .groupBy(vastaanottaja => vastaanottaja.sahkopostiOsoite)
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

  val kaannosAvainPattern: Regex = "[a-zA-Z0-9]+".r
  def validateLahettavaPalvelu(lahettavaPalvelu: Optional[String]): Set[String] =
    if(lahettavaPalvelu.isEmpty || lahettavaPalvelu.get.isEmpty) return Set(VALIDATION_LAHETTAVA_PALVELU_TYHJA)

    var virheet: Set[String] = Set.empty
    if(lahettavaPalvelu.get.length>Viesti.LAHETTAVAPALVELU_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA)
    if(!kaannosAvainPattern.matches(lahettavaPalvelu.get))
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_INVALID)

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

  def validatePrioriteetti(prioriteetti: Optional[String]): Set[String] =
    if (prioriteetti.isEmpty || (!prioriteetti.get.equals(Viesti.VIESTI_PRIORITEETTI_KORKEA) && !prioriteetti.get.equals(Viesti.VIESTI_PRIORITEETTI_NORMAALI)))
      Set(VALIDATION_PRIORITEETTI)
    else
      Set.empty

  def validateSailytysAika(sailytysAika: Optional[Int]): Set[String] =
    if(sailytysAika.isEmpty)
      Set(VALIDATION_SAILYTYSAIKA_TYHJA)
    else if(sailytysAika.get<Viesti.SAILYTYSAIKA_MIN_PITUUS || sailytysAika.get>Viesti.SAILYTYSAIKA_MAX_PITUUS)
      Set(VALIDATION_SAILYTYSAIKA)
    else
      Set.empty

  val kayttooikeusPattern: Regex = ("^.*_[0-9]+(\\.[0-9]+)+$").r
  def validateKayttooikeusRajoitukset(kayttooikeusRajoitukset: Optional[List[String]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok jos käyttöoikeusrajoituksia ei määritelty
    if(kayttooikeusRajoitukset.isEmpty)
      return virheet

    // tarkastetaan onko käyttöoikeusrajoituslistalla null-arvoja
    if (kayttooikeusRajoitukset.get.stream().filter(tunniste => tunniste == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)

    // validoidaan yksittäiset käyttöoikeudet
    kayttooikeusRajoitukset.get.asScala.toSet.map(rajoitus => {
      if (rajoitus != null) {
        var rajoitusVirheet: Set[String] = Set.empty

        if(!kayttooikeusPattern.matches(rajoitus))
          rajoitusVirheet = rajoitusVirheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID)

        if (!rajoitusVirheet.isEmpty)
          virheet = virheet.incl("Käyttöoikeusrajoitus \"" + rajoitus + "\": " +
            rajoitusVirheet.asJava.stream().collect(Collectors.joining(",")))
      }
    })

    // tutkitaan onko tunnistelistalla duplikaatteja
    val duplikaattiRajoitukset = kayttooikeusRajoitukset.get.asScala
      .filter(rajoitus => rajoitus != null)
      .groupBy(rajoitus => rajoitus)
      .filter(rajoitusByRajoitus => rajoitusByRajoitus._2.size > 1)
      .map(rajoitusByRajoitus => rajoitusByRajoitus._1)
    if (!duplikaattiRajoitukset.isEmpty)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaattiRajoitukset.asJavaCollection.stream().collect(Collectors.joining(",")))

    virheet

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

    virheet

  def validateLahetysJaKayttooikeusRajoitukset(lahetysTunniste: Optional[String], kayttooikeusRajoitukset: Optional[List[String]]): Set[String] =
    val lahetysMaaritelty = !(lahetysTunniste.isEmpty || "".equals(lahetysTunniste.get))

    // Käyttöoikeusrajoituksia ei voi määritellä viestikohtaisesti jos ne peritään lähetykseltä
    if(lahetysMaaritelty && kayttooikeusRajoitukset.isPresent)
      return Set(VALIDATION_KAYTTOOIKEUSRAJOITUS_EI_TYHJA)

    Set.empty

  def validateKorkeaPrioriteetti(prioriteetti: Optional[String], vastaanottajat: Optional[List[Vastaanottaja]]): Set[String] =
    if(prioriteetti.isPresent && prioriteetti.get.equals(Viesti.VIESTI_PRIORITEETTI_NORMAALI))
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

    if(sisalto.length + liitteidenKoko > ViestiValidator.VIESTI_MAX_SIZE)
      Set(VALIDATION_KOKO)
    else
      Set.empty

  def validateViesti(viesti: Viesti, lahetysMetadata: Option[LahetysMetadata], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Seq[String] =
    Seq(
      // validoidaan yksittäiset kentät
      validateOtsikko(viesti.otsikko),
      validateSisalto(viesti.sisalto),
      validateSisallonTyyppi(viesti.sisallonTyyppi),
      validateKielet(viesti.kielet),
      validateMaskit(viesti.maskit),
      validateLahettavanVirkailijanOID(viesti.lahettavanVirkailijanOid),
      validateLahettaja(viesti.lahettaja),
      validateReplyTo(viesti.replyTo),
      validateVastaanottajat(viesti.vastaanottajat),
      validateLiitteidenTunnisteet(viesti.liitteidenTunnisteet, liiteMetadatat, identiteetti),
      validateLahettavaPalvelu(viesti.lahettavaPalvelu),
      validateLahetysTunniste(viesti.lahetysTunniste, lahetysMetadata, identiteetti),
      validatePrioriteetti(viesti.prioriteetti),
      validateSailytysAika(viesti.sailytysAika),
      validateKayttooikeusRajoitukset(viesti.kayttooikeusRajoitukset),
      validateMetadata(viesti.metadata),

      // validoidaan kenttien väliset suhteet
      validateKorkeaPrioriteetti(viesti.prioriteetti, viesti.vastaanottajat),
      validateLahetysJaKayttooikeusRajoitukset(viesti.lahetysTunniste, viesti.kayttooikeusRajoitukset)
    ).flatten


end ViestiValidator
