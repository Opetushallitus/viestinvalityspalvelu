package fi.oph.viestinvalitys.vastaanotto.model

import org.apache.commons.validator.routines.EmailValidator

import java.util.{Optional, UUID}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/**
 * Sisältää liitteen validointiin tarvittavan metadatan
 */
case class LiiteMetadata(omistaja: String, koko: Int)

/**
 * Palauttaa käyttäjän joka on luonut järjestelmään annettua tunnistetta vastaavan lähetyksen
 */
trait LahetysTunnisteIdentityProvider:
  def tunnisteAvailableForIdentity(lahetysTunniste: String): Option[String]

/**
 * Validoi järjestelmään syötetyn viestin kentät
 */
object ViestiValidator:

  final val VALIDATION_OTSIKKO_TYHJA                      = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA                = "otsikko: Otsikko ei voi pidempi kuin " + Viesti.OTSIKKO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALTO_TYHJA                      = "sisalto: Kenttä on pakollinen"
  final val VALIDATION_SISALTO_LIIAN_PITKA                = "sisalto: Sisältö ei voi pidempi kuin " + Viesti.SISALTO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_SISALLONTYYPPI                     = "sisallonTyyppi: Sisällön tyypin täytyy olla joko \"" + Viesti.VIESTI_SISALTOTYYPPI_TEXT + "\" tai \"" + Viesti.VIESTI_SISALTOTYYPPI_HTML + "\""

  final val VALIDATION_KIELET_TYHJA                       = "kielet: Kenttä on pakollinen"
  final val VALIDATION_KIELI_EI_SALLITTU                  = "kielet: Kieli ei ole sallittu (\"fi\", \"sv\" ja \"en\"): "
  final val VALIDATION_KIELI_NULL                         = "kielet: Kenttä sisältää null-arvoja"

  final val VALIDATION_LAHETTAJAN_OID                     = "lähettäjänOid: Oid ei ole validi (1.2.246.562-alkuinen) oph-oid"

  final val VALIDATION_OPH_OID_PREFIX                     = "1.2.246.562"

  final val VALIDATION_LAHETTAJA_TYHJA                    = "lähettäjä: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_NIMI_TYHJA              = "lähettäjä: Lähettäjän nimi -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_TYHJA            = "lähettäjä: Lähettäjän sähköpostiosoite -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_INVALID          = "lähettäjä: Lähettäjän sähköpostiosoite ei ole validi sähköpostiosoite"
  final val VALIDATION_LAHETTAJAN_OSOITE_DOMAIN           = "lähettäjä: Lähettäjän sähköpostiosoite ei ole opintopolku.fi -domainissa"

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

  final val VALIDATION_SAILYTYSAIKA                       = "sailytysAika: Säilytysajan tulee olla " + Viesti.SAILYTYSAIKA_MIN_PITUUS + "-" + Viesti.SAILYTYSAIKA_MAX_PITUUS + " päivää"

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_TYHJA         = "kayttooikeusRajoitukset: Kenttä on pakollinen"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL          = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE     = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: "
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID       = "käyttöoikeusrajoitus ei ole organisaatiorajoitettu (ts. ei pääty _<oid>)"

  final val VALIDATION_METADATA_NULL                      = "metadata: Kenttä sisältää null-arvoja: "

  final val VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT = "prioriteetti: Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja"

  def validateOtsikko(otsikko: String): Set[String] =
    var errors: Set[String] = Set.empty

    if(otsikko==null || otsikko.length==0)
      errors = errors.incl(VALIDATION_OTSIKKO_TYHJA)
    else if(otsikko.length > Viesti.OTSIKKO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_OTSIKKO_LIIAN_PITKA)

    errors

  def validateSisalto(sisalto: String): Set[String] =
    var errors: Set[String] = Set.empty

    if (sisalto == null || sisalto.length == 0)
      errors = errors.incl(VALIDATION_SISALTO_TYHJA)
    else if (sisalto.length > Viesti.SISALTO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_SISALTO_LIIAN_PITKA)

    errors

  def validateSisallonTyyppi(sisallonTyyppi: String): Set[String] =
    if(sisallonTyyppi==null || (!sisallonTyyppi.equals(Viesti.VIESTI_SISALTOTYYPPI_TEXT) && !sisallonTyyppi.equals(Viesti.VIESTI_SISALTOTYYPPI_HTML)))
      Set(VALIDATION_SISALLONTYYPPI)
    else
      Set.empty

  final val SALLITUT_KIELET = Set("fi", "sv", "en")
  def validateKielet(kielet: java.util.List[String]): Set[String] =
    if(kielet==null || kielet.isEmpty) return Set(VALIDATION_KIELET_TYHJA)

    // validoidaan yksittäiset kielet
    var virheet: Set[String] = Set.empty
    kielet.asScala.map(kieli => {
      if(kieli!=null && !SALLITUT_KIELET.contains(kieli))
        virheet = virheet.incl(VALIDATION_KIELI_EI_SALLITTU + kieli)
    })

    // tutkitaan onko kielissä null-arvoja
    if (kielet.stream().filter(kieli => kieli == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KIELI_NULL)

    virheet

  val oidPattern: Regex = (VALIDATION_OPH_OID_PREFIX + "(\\.[0-9]+)+").r
  def validateLahettavanVirkailijanOID(oid: Optional[String]): Set[String] =
    if(oid.isEmpty) return Set.empty
    if(oidPattern.matches(oid.get())) return Set.empty
    Set(VALIDATION_LAHETTAJAN_OID)

  def validateLahettaja(lahettaja: Lahettaja): Set[String] =
    if(lahettaja==null) return Set(VALIDATION_LAHETTAJA_TYHJA)

    var virheet: Set[String] = Set.empty
    if(lahettaja.nimi==null || lahettaja.nimi.length==0)
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_NIMI_TYHJA)
    if(lahettaja.sahkopostiOsoite==null || lahettaja.sahkopostiOsoite.length==0)
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_TYHJA)
    else if(!EmailValidator.getInstance(false).isValid(lahettaja.sahkopostiOsoite))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_INVALID)
    else if(!lahettaja.sahkopostiOsoite.endsWith("@opintopolku.fi"))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_DOMAIN)

    virheet

  def validateVastaanottajat(vastaanottajat: java.util.List[Vastaanottaja]): Set[String] =
    var virheet: Set[String] = Set.empty

    // tarkastetaan onko vastaanottajalistalla null-arvoja
    if(vastaanottajat.stream().filter(vastaanottaja => vastaanottaja==null).count()>0)
      virheet = virheet.incl(VALIDATION_VASTAANOTTAJA_NULL)

    // validoidaan yksittäiset vastaanottajat
    vastaanottajat.asScala.toSet.map(vastaanottaja => {
      if(vastaanottaja!=null) {
        var vastaanottajaVirheet: Set[String] = Set.empty
        if (vastaanottaja.nimi == null || vastaanottaja.nimi.length == 0)
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_NIMI_TYHJA)
        if (vastaanottaja.sahkopostiOsoite == null || vastaanottaja.sahkopostiOsoite.length == 0)
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA)
        else if (!EmailValidator.getInstance().isValid(vastaanottaja.sahkopostiOsoite))
          vastaanottajaVirheet = vastaanottajaVirheet.incl(VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID)

        if (!vastaanottajaVirheet.isEmpty)
          virheet = virheet.incl("Vastaanottaja (nimi: " + vastaanottaja.nimi + ", sähköpostiosoite: " + vastaanottaja.sahkopostiOsoite + "): " +
            vastaanottajaVirheet.asJava.stream().collect(Collectors.joining(",")))
      }
    })

    // tutkitaan onko osoitteissa duplikaatteja
    val duplikaattiOsoitteet = vastaanottajat.asScala
      .filter(vastaanottaja => vastaanottaja!=null && vastaanottaja.sahkopostiOsoite!=null)
      .groupBy(vastaanottaja => vastaanottaja.sahkopostiOsoite)
      .filter(vastaanottajatByOsoite => vastaanottajatByOsoite._2.size>1)
      .map(vastaanottajatByOsoite => vastaanottajatByOsoite._1)
    if(!duplikaattiOsoitteet.isEmpty)
      virheet = virheet.incl(VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + duplikaattiOsoitteet.asJavaCollection.stream().collect(Collectors.joining(",")))

    virheet

  def validateLiitteidenTunnisteet(tunnisteet: java.util.List[String], liiteMetadatat: Map[UUID, LiiteMetadata], identiteetti: String): Set[String] =
    var virheet: Set[String] = Set.empty

    // tarkastetaan onko liitetunnistelistalla null-arvoja
    if(tunnisteet.stream().filter(tunniste => tunniste==null).count()>0)
      virheet = virheet.incl(VALIDATION_LIITETUNNISTE_NULL)

    // validoidaan yksittäiset liitetunnisteet
    tunnisteet.asScala.toSet.map(tunniste => {
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
    val duplikaattiTunnisteet = tunnisteet.asScala
      .filter(tunniste => tunniste != null)
      .groupBy(tunniste => tunniste)
      .filter(tunnisteByTunniste => tunnisteByTunniste._2.size > 1)
      .map(tunnisteByTunniste => tunnisteByTunniste._1)
    if (!duplikaattiTunnisteet.isEmpty)
      virheet = virheet.incl(VALIDATION_LIITETUNNISTE_DUPLICATE + duplikaattiTunnisteet.asJavaCollection.stream().collect(Collectors.joining(",")))

    virheet

  val kaannosAvainPattern: Regex = "[a-zA-Z0-9]+".r
  def validateLahettavaPalvelu(lahettavaPalvelu: String): Set[String] =
    if(lahettavaPalvelu==null || lahettavaPalvelu.isEmpty) return Set(VALIDATION_LAHETTAVA_PALVELU_TYHJA)

    var virheet: Set[String] = Set.empty
    if(lahettavaPalvelu.length>Viesti.LAHETTAVAPALVELU_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA)
    if(!kaannosAvainPattern.matches(lahettavaPalvelu))
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_INVALID)

    virheet

  def validateLahetysTunniste(tunniste: String, lahetysTunnisteIdentityProvider: LahetysTunnisteIdentityProvider, identiteetti: String): Set[String] =
    if(tunniste==null) return Set.empty

    try
      UUID.fromString(tunniste)

      val tunnisteAvailableForIdentity = lahetysTunnisteIdentityProvider.tunnisteAvailableForIdentity(tunniste)
      if (tunnisteAvailableForIdentity.isEmpty || !identiteetti.equals(tunnisteAvailableForIdentity.get))
        return Set(VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA)
    catch
      case e: Exception => return Set(VALIDATION_LAHETYSTUNNISTE_INVALID)

    Set.empty

  def validatePrioriteetti(prioriteetti: String): Set[String] =
    if (prioriteetti == null || (!prioriteetti.equals(Viesti.VIESTI_PRIORITEETTI_KORKEA) && !prioriteetti.equals(Viesti.VIESTI_PRIORITEETTI_NORMAALI)))
      Set(VALIDATION_PRIORITEETTI)
    else
      Set.empty

  def validateSailytysAika(sailytysAika: Int): Set[String] =
    if(sailytysAika<Viesti.SAILYTYSAIKA_MIN_PITUUS || sailytysAika>Viesti.SAILYTYSAIKA_MAX_PITUUS)
      Set(VALIDATION_SAILYTYSAIKA)
    else
      Set.empty

  val kayttooikeusPattern: Regex = ("^.*_[0-9]+(\\.[0-9]+)+$").r
  def validateKayttooikeusRajoitukset(kayttooikeusRajoitukset: java.util.List[String]): Set[String] =
    var virheet: Set[String] = Set.empty

    // tarkastetaan onko rajoitukset määritetty
    if(kayttooikeusRajoitukset==null)
      return Set(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)

    // tarkastetaan onko käyttöoikeusrajoituslistalla null-arvoja
    if (kayttooikeusRajoitukset.stream().filter(tunniste => tunniste == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)

    // validoidaan yksittäiset liitetunnisteet
    kayttooikeusRajoitukset.asScala.toSet.map(rajoitus => {
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
    val duplikaattiRajoitukset = kayttooikeusRajoitukset.asScala
      .filter(rajoitus => rajoitus != null)
      .groupBy(rajoitus => rajoitus)
      .filter(rajoitusByRajoitus => rajoitusByRajoitus._2.size > 1)
      .map(rajoitusByRajoitus => rajoitusByRajoitus._1)
    if (!duplikaattiRajoitukset.isEmpty)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaattiRajoitukset.asJavaCollection.stream().collect(Collectors.joining(",")))

    virheet

  def validateMetadata(metadata: java.util.Map[String, String]): Set[String] =
    // tutkitaan onko metadatassa null-arvoja
    val nullArvot = metadata.asScala
      .filter(entry => entry._2==null)
      .map(entry => entry._1)
    if (!nullArvot.isEmpty)
      Set(VALIDATION_METADATA_NULL + nullArvot.asJavaCollection.stream().collect(Collectors.joining(",")))
    else
      Set.empty

  def validateKorkeaPrioriteetti(prioriteetti: String, vastaanottajat: java.util.List[Vastaanottaja]): Set[String] = {
    if(prioriteetti.equals(Viesti.VIESTI_PRIORITEETTI_NORMAALI))
      return Set.empty

    if(vastaanottajat.size()>1)
      return Set(VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT)

    Set.empty
  }

end ViestiValidator

