package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Maskit.MaskitBuilder
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.*
import fi.oph.viestinvalitys.vastaanotto.resource.ParametriUtil
import io.swagger.v3.oas.annotations.media.{Schema}
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object ViestiImpl {
  final val OTSIKKO_MAX_PITUUS                  = 255
  final val SISALTO_MAX_PITUUS                  = 6*1024*1024 // SES-viesteissä maksimikoko 10 megatavua, mennään varmuuden vuoksi reilusti alle

  final val VIESTI_MAX_SIZE                     = VIESTI_MAX_SIZE_MB_STR.toInt * 1024 * 1024
  final val VIESTI_MAX_SIZE_MB_STR              = "8"

  final val VIESTI_NIMI_MAX_PITUUS              = 64
  final val VIESTI_SALAISUUS_MIN_PITUUS         = 8
  final val VIESTI_SALAISUUS_MAX_PITUUS         = 1024
  final val VIESTI_MASKI_MIN_PITUUS             = 8
  final val VIESTI_MASKI_MAX_PITUUS             = 1024
  final val VIESTI_METADATA_AVAIN_MAX_PITUUS    = 64
  final val VIESTI_METADATA_ARVO_MAX_PITUUS     = 64
  final val VIESTI_METADATA_ARVOT_MAX_MAARA     = 1024

  final val VIESTI_METADATA_AVAIMET_MAX_MAARA   = 1024
  final val VIESTI_MASKIT_MAX_MAARA             = 32
  final val VIESTI_VASTAANOTTAJAT_MAX_MAARA     = 2048
  final val VIESTI_LIITTEET_MAX_MAARA           = 128

  final val VIESTI_ORGANISAATIO_MAX_PITUUS      = 64
  final val VIESTI_OIKEUS_MAX_PITUUS            = 64
  final val VIESTI_KAYTTOOIKEUS_MAX_MAARA       = 128

  final val VIESTI_SISALTOTYYPPI_TEXT           = "text"
  final val VIESTI_SISALTOTYYPPI_HTML           = "html"
}

/**
 * Viestin vastaanottaja
 *
 * @param nimi
 * @param sahkopostiOsoite
 */
case class VastaanottajaImpl(
  @(Schema @field)(example = "Vallu Vastaanottaja", maxLength = ViestiImpl.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(example = "vallu.vastaanottaja@example.com", requiredMode=RequiredMode.REQUIRED)
  @BeanProperty sahkopostiOsoite: Optional[String],
) extends Vastaanottaja {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

class VastaanottajatBuilderImpl(vastaanottajat: Seq[Viesti.Vastaanottaja]) extends Vastaanottajat.VastaanottajatBuilder {

  def this() = this(Seq.empty)

  override def withVastaanottaja(nimi: Optional[String], sahkopostiOsoite: String): Vastaanottajat.VastaanottajatBuilder =
    VastaanottajatBuilderImpl(vastaanottajat.appended(new VastaanottajaImpl(nimi, Optional.of(sahkopostiOsoite))))

  override def build(): util.List[Viesti.Vastaanottaja] =
    vastaanottajat.asJava
}

case class MaskiImpl(
  @(Schema @field)(example = "https://salainen.linkki.johonkin", requiredMode=RequiredMode.REQUIRED,
    minLength = ViestiImpl.VIESTI_SALAISUUS_MIN_PITUUS, maxLength = ViestiImpl.VIESTI_SALAISUUS_MAX_PITUUS)
  @BeanProperty salaisuus: Optional[String],

  @(Schema @field)(example = "<salainen linkki peitetty>", requiredMode=RequiredMode.REQUIRED,
    minLength = ViestiImpl.VIESTI_MASKI_MIN_PITUUS, maxLength = ViestiImpl.VIESTI_MASKI_MAX_PITUUS)
  @BeanProperty maski: Optional[String],
) extends Maski {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

class MaskitBuilderImpl extends Maskit.MaskitBuilder {

  val maskit = new util.ArrayList[Maski]()
  override def withMaski(salaisuus: String, maski: String): MaskitBuilder =
    maskit.add(MaskiImpl(Optional.of(salaisuus), Optional.of(maski)))
    this

  override def build(): util.List[Maski] =
    maskit
}

/**
 * Viestin käyttöoikeus
 *
 * @param organisaatio
 * @param oikeus
 */
case class KayttooikeusImpl(
  @(Schema @field)(example = "1.2.246.562.00.00000000000000006666", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.VIESTI_ORGANISAATIO_MAX_PITUUS)
  @BeanProperty organisaatio: Optional[String],

  @(Schema @field)(example = "APP_ATARU_HAKEMUS_CRUD", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.VIESTI_OIKEUS_MAX_PITUUS)
  @BeanProperty oikeus: Optional[String],
) extends Kayttooikeus {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

class MetadatatBuilderImpl extends Metadatat.MetadatatBuilder {

  val metadatat: util.Map[String, util.List[String]] = new util.HashMap[String, util.List[String]]()
  override def withMetadata(avain: String, arvot: util.List[String]): Metadatat.MetadatatBuilder =
    metadatat.put(avain, arvot)
    this

  override def build(): util.Map[String, util.List[String]] =
    metadatat
}


/**
 * Vastaanotettava viesti.
 *
 * Huomioita:
 *  - Erillinen raportoinnin viestiä esittävästä luokasta koska kentät todennäköisesti eri, esim. raportointi sisältänee aikaleimoja
 *  - Collection-kenttien tyyppien on java.util.List jotta:
 *    - OpenApi ymmärtää minkä tyyppisistä kentistä on kysymys
 *    - deep equals toimii testeissä
 *
 * @param otsikko                   Viestin otsikko
 * @param sisalto                   Viestin sisältö
 * @param sisallonTyyppi            Sisällön tyyppi, sallitut arvot "text" ja "html"
 * @param kielet                    Sisällön kielet, sallitus arvot "fi", "sv", "en"
 * @param maskit                    Raportointiapissa peitettävät salaisuudet [[MaskiImp]]
 * @param lahettavanVirkailijanOid  Lähettävän virkailijan tunniste
 * @param lahettaja                 Viestin lähettäjä [[LahettajaImpl]]
 * @param replyTo                   Vastausosoite
 * @param vastaanottajat            Viestin vastaanottajat [[VastaanottajaImpl]]
 * @param liitteidenTunnisteet      Viestin liitteiden UIDt [[UUID]]
 * @param prioriteetti              Viestin prioriteetti, sallitut arvot "korkea" ja "normaali"
 * @param sailytysAika              Viestin säilytysaika päivissä (alkaa viestin lähetyspyynnön vastaanottamisesta)
 * @param metadata                  Lähettävän palvelun viestiin liittämä vapaa key/value metadata
 * @param lahetysTunniste           Massalähetyksen tunniste [[UUID]]
 * @param lahettavaPalvelu          Lähettävän palvelun tunniste
 * @param kayttooikeusRajoitukset   Oikeudet jotka käyttäjällä pitää olla viestin katsomiseen raportointirajapinnan kautta
 */
@Schema(name = "Viesti", description = "Lähetettävä viesti")
case class ViestiImpl(
  @(Schema @field)(example = "Onnistunut otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema @field)(example = "Syvällinen sisältö", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.SISALTO_MAX_PITUUS)
  @BeanProperty sisalto: Optional[String],

  @(Schema @field)(allowableValues = Array(ViestiImpl.VIESTI_SISALTOTYYPPI_TEXT, ViestiImpl.VIESTI_SISALTOTYYPPI_HTML), requiredMode=RequiredMode.REQUIRED, example = "text")
  @BeanProperty sisallonTyyppi: Optional[String],

  @(Schema @field)(description= "Järjestyksellä ei ole merkitystä", allowableValues = Array("fi", "sv", "en"), example = "[\"fi\", \"sv\"]")
  @BeanProperty kielet: Optional[util.List[String]],

  @(Schema@field)(description = "Merkkijonot jotka peitetään kun viesti näytetään raportointirajapinnassa", maxLength = ViestiImpl.VIESTI_MASKIT_MAX_MAARA)
  @BeanProperty maskit: Optional[util.List[Maski]],

  @(Schema @field)(example = "1.2.246.562.00.00000000000000006666", maxLength = LahetysImpl.VIRKAILIJAN_OID_MAX_PITUUS)
  @BeanProperty lahettavanVirkailijanOid: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
  @BeanProperty lahettaja: Optional[Lahetys.Lahettaja],

  @(Schema@field)(example = "ville.virkamies@oph.fi")
  @BeanProperty replyTo: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.VIESTI_VASTAANOTTAJAT_MAX_MAARA)
  @BeanProperty vastaanottajat: Optional[util.List[Vastaanottaja]],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) lataamia.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", maxLength = ViestiImpl.VIESTI_LIITTEET_MAX_MAARA)
  @BeanProperty liitteidenTunnisteet: Optional[util.List[String]],

  @(Schema @field)(allowableValues = Array(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA, LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), requiredMode=RequiredMode.REQUIRED, example = LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI)
  @BeanProperty prioriteetti: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, minimum=LahetysImpl.SAILYTYSAIKA_MIN_PITUUS_STR, maximum=LahetysImpl.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
  @BeanProperty sailytysaika: Optional[Integer],

  @(Schema @field)(example = "{ \"key\": [\"value1\", \"value2\"] }", maxLength = ViestiImpl.VIESTI_METADATA_ARVOT_MAX_MAARA)
  @BeanProperty metadata: Optional[util.Map[String, util.List[String]]],

  @(Schema@field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) luoma, jos tyhjä luodaan automaattisesti.", example = " ", nullable = true)
  @BeanProperty lahetysTunniste: Optional[String],

  @(Schema@field)(example = "hakemuspalvelu")
  @BeanProperty lahettavaPalvelu: Optional[String],

  @(Schema@field)(maxLength = ViestiImpl.VIESTI_KAYTTOOIKEUS_MAX_MAARA)
  @BeanProperty kayttooikeusRajoitukset: Optional[util.List[Kayttooikeus]],
) extends Viesti {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
  }
}

class ViestiBuilderImpl(viesti: ViestiImpl) extends OtsikkoBuilder, SisaltoBuilder, KieletBuilder,
  VastaanottajatBuilder, PrioriteettiBuilder, SailysaikaBuilder, ViestiBuilder, ExistingLahetysBuilder, LahettajaBuilder, InlineLahetysBuilder {

  def this() = this(new ViestiImpl)

  def withOtsikko(otsikko: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(otsikko = Optional.of(otsikko)))

  def withTextSisalto(sisalto: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(sisallonTyyppi = Optional.of(ViestiImpl.VIESTI_SISALTOTYYPPI_TEXT.toLowerCase), sisalto = Optional.of(sisalto)))

  def withHtmlSisalto(sisalto: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(sisallonTyyppi = Optional.of(ViestiImpl.VIESTI_SISALTOTYYPPI_HTML.toLowerCase), sisalto = Optional.of(sisalto)))

  def withKielet(kielet: String*): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(kielet = Optional.of(kielet.asJava)))

  def withVastaanottajat(vastaanottajat: util.List[Vastaanottaja]): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(vastaanottajat = Optional.of(vastaanottajat)))

  def withNormaaliPrioriteetti(): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(prioriteetti = Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI.toLowerCase)))

  def withKorkeaPrioriteetti(): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(prioriteetti = Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA.toLowerCase)))

  def withSailytysAika(sailytysaika: Integer): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(sailytysaika = Optional.of(sailytysaika)))

  def withMaskit(maskit: util.List[Maski]): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(maskit = Optional.of(maskit)))

  def withReplyTo(replyTo: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(replyTo = Optional.of(replyTo)))

  def withLiitteidenTunnisteet(liitteidenTunnisteet: util.List[UUID]): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(liitteidenTunnisteet = Optional.of(liitteidenTunnisteet.asScala.map(t => t.toString).asJava)))

  def withMetadatat(metadatat: util.Map[String, util.List[String]]): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(metadata = Optional.of(metadatat)))

  def withLahetysTunniste(lahetysTunniste: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(lahetysTunniste = Optional.of(lahetysTunniste)))

  def withLahettavaPalvelu(lahettavaPalvelu: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(lahettavaPalvelu = Optional.of(lahettavaPalvelu)))

  def withLahettavanVirkailijanOid(oid: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(lahettavanVirkailijanOid = Optional.of(oid)))

  def withLahettaja(nimi: Optional[String], sahkoposti: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(lahettaja = Optional.of(LahettajaImpl(nimi, Optional.of(sahkoposti)))))

  def withKayttooikeusRajoitukset(kayttooikeusRajoitukset: Kayttooikeus*): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(kayttooikeusRajoitukset = Optional.of(kayttooikeusRajoitukset.asJava)))

  def build(): Viesti =
    this.viesti
}

