package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.business.{Prioriteetti, SisallonTyyppi}
import Viesti.*
import ViestiBuilder.{TakesMaskiBuilder, TakesMetadataBuilder}
import Viesti.VastaanottajatBuilder.TakesVastaanottajaBuilder
import fi.oph.viestinvalitys.vastaanotto.resource.ParametriUtil
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import io.swagger.v3.oas.annotations.media.ExampleObject

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.annotation.varargs
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object ViestiImpl {
  final val OTSIKKO_MAX_PITUUS                  = 255
  final val SISALTO_MAX_PITUUS                  = 6*1024*1024 // SES-viesteissä maksimikoko 10 megatavua, mennään varmuuden vuoksi reilusti alle

  final val VIESTI_MAX_SIZE                     = VIESTI_MAX_SIZE_MB_STR.toInt * 1024 * 1024
  final val VIESTI_MAX_SIZE_MB_STR              = "8"

  final val LAHETTAVAPALVELU_MAX_PITUUS         = 127
  final val VIESTI_NIMI_MAX_PITUUS              = 64
  final val VIESTI_SALAISUUS_MIN_PITUUS         = 8
  final val VIESTI_SALAISUUS_MAX_PITUUS         = 1024
  final val VIESTI_MASKI_MIN_PITUUS             = 8
  final val VIESTI_MASKI_MAX_PITUUS             = 1024
  final val VIESTI_VIRKALIJAN_OID_MAX_PITUUS    = 64
  final val VIESTI_METADATA_AVAIN_MAX_PITUUS    = 64
  final val VIESTI_METADATA_AVAIMET_MAX_MAARA   = 1024
  final val VIESTI_METADATA_ARVO_MAX_PITUUS     = 64
  final val VIESTI_METADATA_ARVOT_MAX_MAARA     = 1024

  final val VIESTI_VASTAANOTTAJAT_MAX_MAARA     = VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR.toInt
  final val VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR = "2048"

  final val SAILYTYSAIKA_MIN_PITUUS             = SAILYTYSAIKA_MIN_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MIN_PITUUS_STR         = "1"
  final val SAILYTYSAIKA_MAX_PITUUS             = SAILYTYSAIKA_MAX_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MAX_PITUUS_STR         = "3650"

  final val VIESTI_PRIORITEETTI_KORKEA          = "korkea"
  final val VIESTI_PRIORITEETTI_NORMAALI        = "normaali"

  final val VIESTI_SISALTOTYYPPI_TEXT           = "text"
  final val VIESTI_SISALTOTYYPPI_HTML           = "html"
}

/**
 * Viestin lähettäjä
 *
 * @param nimi
 * @param sahkopostiOsoite
 */
case class LahettajaImpl(
  @(Schema @field)(example = "Opintopolku", maxLength = ViestiImpl.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(description="Domainin pitää olla opintopolku.fi", example = "noreply@opintopolku.fi", requiredMode=RequiredMode.REQUIRED)
  @BeanProperty sahkopostiOsoite: Optional[String],
) extends Lahettaja {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
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
 * @param lahettavanVirkailijanOid  Lähettävän virkailijan tunniste
 * @param lahettaja                 Viestin lähettäjä [[LahettajaImpl]]
 * @param vastaanottajat            Viestin vastaanottajat [[VastaanottajaImpl]]
 * @param liitteidenTunnisteet      Viestin liitteiden UIDt [[UUID]]
 * @param lahettavaPalvelu          Lähettävän palvelun tunniste
 * @param lahetysTunniste           Massalähetyksen tunniste [[UUID]]
 * @param prioriteetti              Viestin prioriteetti, sallitut arvot "korkea" ja "normaali"
 * @param sailytysAika              Viestin säilytysaika päivissä (alkaa viestin lähetyspyynnön vastaanottamisesta)
 * @param kayttooikeusRajoitukset   Oikeudet jotka käyttäjällä pitää olla viestin katsomiseen raportointirajapinnan kautta
 * @param metadata                  Lähettävän palvelun viestiin liittämä vapaa key/value metadata
 */
@Schema(name = "Viesti", description = "Lähetettävä viesti")
case class ViestiImpl(
  @(Schema @field)(example = "Onnistunut otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema @field)(example = "Syvällinen sisältö", requiredMode=RequiredMode.REQUIRED, maxLength = ViestiImpl.SISALTO_MAX_PITUUS)
  @BeanProperty sisalto: Optional[String],

  @(Schema @field)(allowableValues = Array(ViestiImpl.VIESTI_SISALTOTYYPPI_TEXT, ViestiImpl.VIESTI_SISALTOTYYPPI_HTML), requiredMode=RequiredMode.REQUIRED, example = "text")
  @BeanProperty sisallonTyyppi: Optional[String],

  @(Schema @field)(description= "Järjestyksellä ei ole merkitystä", requiredMode=RequiredMode.REQUIRED, allowableValues = Array("fi", "sv", "en"), example = "[\"fi\", \"sv\"]")
  @BeanProperty kielet: Optional[util.List[String]],

  @(Schema@field)(description = "Merkkijonot jotka peitetään kun viesti näytetään raportointirajapinnassa")
  @BeanProperty maskit: Optional[util.List[Maski]],

  @(Schema @field)(example = "1.2.246.562.00.00000000000000006666", maxLength = ViestiImpl.VIESTI_VIRKALIJAN_OID_MAX_PITUUS)
  @BeanProperty lahettavanVirkailijanOid: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
  @BeanProperty lahettaja: Optional[Lahettaja],

  @(Schema@field)(example = "ville.virkamies@oph.fi")
  @BeanProperty replyTo: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, maximum = ViestiImpl.VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR)
  @BeanProperty vastaanottajat: Optional[util.List[Vastaanottaja]],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) lataamia.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
  @BeanProperty liitteidenTunnisteet: Optional[util.List[String]],

  @(Schema @field)(example = "hakemuspalvelu")
  @BeanProperty lahettavaPalvelu: Optional[String],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) luoma, jos tyhjä luodaan automaattisesti.", example = " ", nullable = true)
  @BeanProperty lahetysTunniste: Optional[String],

  @(Schema @field)(allowableValues = Array(ViestiImpl.VIESTI_PRIORITEETTI_KORKEA, ViestiImpl.VIESTI_PRIORITEETTI_NORMAALI), requiredMode=RequiredMode.REQUIRED, example = "normaali")
  @BeanProperty prioriteetti: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, minimum=ViestiImpl.SAILYTYSAIKA_MIN_PITUUS_STR, maximum=ViestiImpl.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
  @BeanProperty sailytysAika: Optional[Integer],

  @(Schema @field)(example = "[\"APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666\"]")
  @BeanProperty kayttooikeusRajoitukset: Optional[util.List[String]],

  @(Schema @field)(example = "{ \"key\": [\"value1\", \"value2\"] }", maxLength = ViestiImpl.VIESTI_METADATA_ARVOT_MAX_MAARA)
  @BeanProperty metadata: Optional[util.Map[String, util.List[String]]],
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

class ViestiBuilderImpl() extends OtsikkoBuilder, SisaltoBuilder, KieletBuilder, LahettajaBuilder, LahettavaPalveluBuilder,
  VastaanottajatBuilder, PrioriteettiBuilder, SailysaikaBuilder, ViestiBuilder {

  var viesti = new ViestiImpl

  def withOtsikko(otsikko: String): SisaltoBuilder =
    viesti = viesti.copy(otsikko = Optional.of(otsikko))
    this

  def withTextSisalto(sisalto: String): KieletBuilder =
    viesti = viesti.copy(sisallonTyyppi = Optional.of(SisallonTyyppi.TEXT.toString.toLowerCase), sisalto = Optional.of(sisalto))
    this

  def withHtmlSisalto(sisalto: String): KieletBuilder =
    viesti = viesti.copy(sisallonTyyppi = Optional.of(SisallonTyyppi.HTML.toString.toLowerCase), sisalto = Optional.of(sisalto))
    this

  def withKielet(kielet: String*): LahettajaBuilder =
    viesti = viesti.copy(kielet = Optional.of(kielet.asJava))
    this

  def withLahettaja(nimi: Optional[String], sahkoposti: String): LahettavaPalveluBuilder =
    viesti = viesti.copy(lahettaja = Optional.of(LahettajaImpl(nimi, Optional.of(sahkoposti))))
    this

  def withLahettavaPalvelu(lahettavaPalvelu: String): ViestiBuilderImpl =
    viesti = viesti.copy(lahettavaPalvelu = Optional.of(lahettavaPalvelu))
    this

  def withVastaanottajat(b: TakesVastaanottajaBuilder): PrioriteettiBuilder =
    var vastaanottajat: Seq[VastaanottajaImpl] = Seq.empty

    b.withVastaanottajaBuilder((nimi, sahkoposti) =>
      vastaanottajat = vastaanottajat.appended(VastaanottajaImpl(nimi, Optional.of(sahkoposti))))
    viesti = viesti.copy(vastaanottajat = Optional.of(vastaanottajat.asJava))
    this

  def withNormaaliPrioriteetti(): SailysaikaBuilder =
    viesti = viesti.copy(prioriteetti = Optional.of(Prioriteetti.NORMAALI.toString.toLowerCase))
    this

  def withKorkeaPrioriteetti(): SailysaikaBuilder =
    viesti = viesti.copy(prioriteetti = Optional.of(Prioriteetti.KORKEA.toString.toLowerCase))
    this

  def withSailytysAika(sailytysAika: Integer): ViestiBuilderImpl =
    viesti = viesti.copy(sailytysAika = Optional.of(sailytysAika))
    this

  def withKayttooikeusRajoitukset(kayttooikeusRajoitukset: String*): ViestiBuilderImpl =
    viesti = viesti.copy(kayttooikeusRajoitukset = Optional.of(kayttooikeusRajoitukset.asJava))
    this

  def withMaskit(b: TakesMaskiBuilder): ViestiBuilderImpl =
    var maskit: Seq[MaskiImpl] = Seq.empty
    b.withMaskiBuilder((salaisuus, maski) =>
      maskit = maskit.appended(MaskiImpl(Optional.of(salaisuus), Optional.of(maski))))
    viesti = viesti.copy(maskit = Optional.of(maskit.asJava))
    this

  def withLahettavanVirkailijanOid(oid: String): ViestiBuilderImpl =
    viesti = viesti.copy(lahettavanVirkailijanOid = Optional.of(oid))
    this

  def withReplyTo(replyTo: String): ViestiBuilderImpl =
    viesti = viesti.copy(replyTo = Optional.of(replyTo))
    this

  def withLiitteidenTunnisteet(liitteidenTunnisteet: util.List[UUID]): ViestiBuilderImpl =
    viesti = viesti.copy(liitteidenTunnisteet = Optional.of(liitteidenTunnisteet.asScala.map(t => t.toString).asJava))
    this

  def withLahetysTunniste(lahetysTunniste: String): ViestiBuilderImpl =
    viesti = viesti.copy(lahetysTunniste = Optional.of(lahetysTunniste))
    this

  def withMetadata(b: TakesMetadataBuilder): ViestiBuilderImpl =
    var metadatat: Map[String, util.List[String]] = Map.empty
    b.withMetadataBuilder((key, values) =>
      metadatat = metadatat.updated(key, values))
    viesti = viesti.copy(metadata = Optional.of(metadatat.asJava))
    this

  def build(): Viesti =
    val DUMMY_OMISTAJA = "omistaja";
    val lahetysMetadata = LahetysMetadata(DUMMY_OMISTAJA)
    val liiteMetadata = ParametriUtil.validUUIDs(viesti.liitteidenTunnisteet).map(t => t -> LiiteMetadata(DUMMY_OMISTAJA, 0)).toMap

    val virheet = ViestiValidator.validateViesti(this.viesti, Option.apply(lahetysMetadata), liiteMetadata, DUMMY_OMISTAJA)
    if(!virheet.isEmpty) throw new BuilderException(virheet.toSet.asJava)

    this.viesti
}

