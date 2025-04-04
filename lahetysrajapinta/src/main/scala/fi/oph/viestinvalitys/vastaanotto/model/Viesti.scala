package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Kayttooikeusrajoitukset.KayttooikeusrajoituksetBuilder
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.LAHETTAVAPALVELU_MAX_PITUUS
import fi.oph.viestinvalitys.vastaanotto.model.Maskit.MaskitBuilder
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object ViestiImpl {
}

/**
 * Viestin vastaanottaja
 *
 * @param nimi
 * @param sahkopostiOsoite
 */
case class VastaanottajaImpl(
  @(Schema @field)(example = "Vallu Vastaanottaja", maxLength = Viesti.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(example = "vallu.vastaanottaja@example.com", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.VIESTI_OSOITE_MAX_PITUUS)
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
    minLength = Viesti.VIESTI_SALAISUUS_MIN_PITUUS, maxLength = Viesti.VIESTI_SALAISUUS_MAX_PITUUS)
  @BeanProperty salaisuus: Optional[String],

  @(Schema @field)(example = "<salainen linkki peitetty>", requiredMode=RequiredMode.REQUIRED,
    minLength = Viesti.VIESTI_MASKI_MIN_PITUUS, maxLength = Viesti.VIESTI_MASKI_MAX_PITUUS)
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
  @(Schema @field)(example = "APP_ATARU_HAKEMUS_CRUD", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.VIESTI_OIKEUS_MAX_PITUUS)
  @BeanProperty oikeus: Optional[String],

  @(Schema @field)(example = "1.2.246.562.10.240484683010", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.VIESTI_ORGANISAATIO_MAX_PITUUS)
  @BeanProperty organisaatio: Optional[String],

) extends Kayttooikeus {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

class KayttooikeusrajoituksetBuilderImpl extends Kayttooikeusrajoitukset.KayttooikeusrajoituksetBuilder {

  val kayttooikeusRajoitukset = new util.ArrayList[Kayttooikeus]()

  override def withKayttooikeus(oikeus: String, organisaatio: String): KayttooikeusrajoituksetBuilder =
    kayttooikeusRajoitukset.add(KayttooikeusImpl(Optional.of(oikeus), Optional.of(organisaatio)))
    this

  override def build(): util.List[Kayttooikeus] =
    kayttooikeusRajoitukset
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
 *  - Collection-kenttien tyyppien on oltava java.util.List jotta:
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
 * @param idempotencyKey            Lähettävän palvelun määrittelemä viestikohtainen yksilöivä avain jolla varmistetaan ettei samaa viestiä lähetetä kahdesti
 * @param kayttooikeusRajoitukset   Oikeudet jotka käyttäjällä pitää olla viestin katsomiseen raportointirajapinnan kautta
 */
@Schema(name = "Viesti", description = "Lähetettävä viesti")
case class ViestiImpl(
  @(Schema @field)(example = "Onnistunut otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema @field)(example = "Syvällinen sisältö", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.SISALTO_MAX_PITUUS)
  @BeanProperty sisalto: Optional[String],

  @(Schema @field)(allowableValues = Array(Viesti.VIESTI_SISALTOTYYPPI_TEXT, Viesti.VIESTI_SISALTOTYYPPI_HTML), requiredMode=RequiredMode.REQUIRED, example = "text")
  @BeanProperty sisallonTyyppi: Optional[String],

  @(Schema @field)(description= "Järjestyksellä ei ole merkitystä", allowableValues = Array("fi", "sv", "en"), example = "[\"fi\", \"sv\"]")
  @BeanProperty kielet: Optional[util.List[String]],

  @(Schema@field)(description = "Merkkijonot jotka peitetään kun viesti näytetään raportointirajapinnassa", maxLength = Viesti.VIESTI_MASKIT_MAX_MAARA)
  @BeanProperty maskit: Optional[util.List[Maski]],

  @(Schema @field)(example = "1.2.246.562.00.00000000000000006666", maxLength = LahetysImpl.VIRKAILIJAN_OID_MAX_PITUUS)
  @BeanProperty lahettavanVirkailijanOid: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
  @BeanProperty lahettaja: Optional[Lahetys.Lahettaja],

  @(Schema@field)(example = "ville.virkamies@oph.fi")
  @BeanProperty replyTo: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.VIESTI_VASTAANOTTAJAT_MAX_MAARA)
  @BeanProperty vastaanottajat: Optional[util.List[Vastaanottaja]],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) lataamia.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]", maxLength = Viesti.VIESTI_LIITTEET_MAX_MAARA)
  @BeanProperty liitteidenTunnisteet: Optional[util.List[String]],

  @(Schema @field)(allowableValues = Array(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA, LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), requiredMode=RequiredMode.REQUIRED, example = LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI)
  @BeanProperty prioriteetti: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, minimum=LahetysImpl.SAILYTYSAIKA_MIN_PITUUS_STR, maximum=LahetysImpl.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
  @BeanProperty sailytysaika: Optional[Integer],

  @(Schema @field)(example = "{ \"key\": [\"value1\", \"value2\"] }", maxLength = Viesti.VIESTI_METADATA_AVAIMET_MAX_MAARA, description =
    "Avaimen maksimipituus on " + VIESTI_METADATA_AVAIN_MAX_PITUUS_STR + " merkkiä, " +
    "arvon maksimipituus on " + VIESTI_METADATA_ARVO_MAX_PITUUS_STR + " merkkiä, " +
    "sallittuja merkkejä ovat " + VIESTI_METADATA_SALLITUT_MERKIT + " " +
    "Yksittäisellä avaimella voi olla enintään " + VIESTI_METADATA_ARVOT_MAX_MAARA_STR + " arvoa")
  @BeanProperty metadata: Optional[util.Map[String, util.List[String]]],

  @(Schema@field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) luoma, jos tyhjä luodaan automaattisesti.", example = " ", nullable = true)
  @BeanProperty lahetysTunniste: Optional[String],

  @(Schema@field)(example = "hakemuspalvelu", maxLength = LAHETTAVAPALVELU_MAX_PITUUS)
  @BeanProperty lahettavaPalvelu: Optional[String],

  @(Schema@field)(description = "Lähettävän palvelun määrittelemä viestikohtainen yksilöivä avain jolla varmistetaan ettei samaa viestiä lähetetä kahdesti. Sallittuja merkkejä ovat " +
    VIESTI_IDEMPOTENCY_KEY_SALLITUT_MERKIT, example = "12345", maxLength = Viesti.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS)
  @BeanProperty idempotencyKey: Optional[String],

  @(Schema@field)(maxLength = Viesti.VIESTI_KAYTTOOIKEUS_MAX_MAARA)
  @BeanProperty kayttooikeusRajoitukset: Optional[util.List[Kayttooikeus]],
) extends Viesti {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
      Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
  }
}

class ViestiBuilderImpl(viesti: ViestiImpl) extends OtsikkoBuilder, SisaltoBuilder, KieletBuilder,
  VastaanottajatBuilder, PrioriteettiBuilder, SailysaikaBuilder, ViestiBuilder, ExistingLahetysBuilder, LahettajaBuilder, InlineLahetysBuilder {

  def this() = this(new ViestiImpl)

  def withOtsikko(otsikko: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(otsikko = Optional.of(otsikko)))

  def withTextSisalto(sisalto: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(sisallonTyyppi = Optional.of(Viesti.VIESTI_SISALTOTYYPPI_TEXT.toLowerCase), sisalto = Optional.of(sisalto)))

  def withHtmlSisalto(sisalto: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(sisallonTyyppi = Optional.of(Viesti.VIESTI_SISALTOTYYPPI_HTML.toLowerCase), sisalto = Optional.of(sisalto)))

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

  def withIdempotencyKey(avain: String): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(idempotencyKey = Optional.of(avain)))

  def withKayttooikeusRajoitukset(kayttooikeusRajoitukset: util.List[Kayttooikeus]): ViestiBuilderImpl =
    ViestiBuilderImpl(viesti.copy(kayttooikeusRajoitukset = Optional.of(kayttooikeusRajoitukset)))

  def build(): Viesti =
    this.viesti
}

