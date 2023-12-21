package fi.oph.viestinvalitys.vastaanotto.model

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import io.swagger.v3.oas.annotations.media.ExampleObject

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty

object Viesti {
  final val OTSIKKO_MAX_PITUUS = 255
  final val SISALTO_MAX_PITUUS = 6*1024*1024 // SES-viesteissä maksimikoko 10 megatavua, mennään varmuuden vuoksi reilusti alle

  final val LAHETTAVAPALVELU_MAX_PITUUS = 127
  final val VIESTI_NIMI_MAX_PITUUS = 64
  final val VIESTI_SALAISUUS_MIN_PITUUS = 8
  final val VIESTI_SALAISUUS_MAX_PITUUS = 1024
  final val VIESTI_MASKI_MIN_PITUUS = 8
  final val VIESTI_MASKI_MAX_PITUUS = 1024
  final val VIESTI_VIRKALIJAN_OID_MAX_PITUUS = 64

  final val VIESTI_VASTAANOTTAJAT_MAX_MAARA = VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR.toInt
  final val VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR = "2048"

  final val SAILYTYSAIKA_MIN_PITUUS = SAILYTYSAIKA_MIN_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MIN_PITUUS_STR = "1"
  final val SAILYTYSAIKA_MAX_PITUUS = SAILYTYSAIKA_MAX_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MAX_PITUUS_STR = "3650"

  final val VIESTI_PRIORITEETTI_KORKEA = "korkea"
  final val VIESTI_PRIORITEETTI_NORMAALI = "normaali"
  
  final val VIESTI_SISALTOTYYPPI_TEXT = "text"
  final val VIESTI_SISALTOTYYPPI_HTML = "html"
}

/**
 * Viestin lähettäjä
 *
 * @param nimi
 * @param sahkopostiOsoite
 */
case class Lahettaja(
  @(Schema @field)(example = "Opintopolku", maxLength = Viesti.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(description="Domainin pitää olla opintopolku.fi", example = "noreply@opintopolku.fi", requiredMode=RequiredMode.REQUIRED)
  @BeanProperty sahkopostiOsoite: Optional[String],
) {

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
case class Vastaanottaja(
  @(Schema @field)(example = "Vallu Vastaanottaja", maxLength = Viesti.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(example = "vallu.vastaanottaja@example.com", requiredMode=RequiredMode.REQUIRED)
  @BeanProperty sahkopostiOsoite: Optional[String],
) {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

case class Maski(
  @(Schema @field)(example = "https://salainen.linkki.johonkin", requiredMode=RequiredMode.REQUIRED,
    minLength = Viesti.VIESTI_SALAISUUS_MIN_PITUUS, maxLength = Viesti.VIESTI_SALAISUUS_MAX_PITUUS)
  @BeanProperty salaisuus: Optional[String],

  @(Schema @field)(example = "<salainen linkki peitetty>", requiredMode=RequiredMode.REQUIRED,
    minLength = Viesti.VIESTI_MASKI_MIN_PITUUS, maxLength = Viesti.VIESTI_MASKI_MAX_PITUUS)
  @BeanProperty maski: Optional[String],
) {

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
 * @param lahettaja                 Viestin lähettäjä [[Lahettaja]]
 * @param vastaanottajat            Viestin vastaanottajat [[Vastaanottaja]]
 * @param liitteidenTunnisteet      Viestin liitteiden UIDt [[UUID]]
 * @param lahettavaPalvelu          Lähettävän palvelun tunniste
 * @param lahetysTunniste           Massalähetyksen tunniste [[UUID]]
 * @param prioriteetti              Viestin prioriteetti, sallitut arvot "korkea" ja "normaali"
 * @param sailytysAika              Viestin säilytysaika päivissä (alkaa viestin lähetyspyynnön vastaanottamisesta)
 * @param kayttooikeusRajoitukset   Oikeudet jotka käyttäjällä pitää olla viestin katsomiseen raportointirajapinnan kautta
 * @param metadata                  Lähettävän palvelun viestiin liittämä vapaa key/value metadata
 */
@Schema(description = "Lähetettävä viesti")
case class Viesti(
  @(Schema @field)(example = "Onnistunut otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema @field)(example = "Syvällinen sisältö", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.SISALTO_MAX_PITUUS)
  @BeanProperty sisalto: Optional[String],

  @(Schema @field)(allowableValues = Array(Viesti.VIESTI_SISALTOTYYPPI_TEXT, Viesti.VIESTI_SISALTOTYYPPI_HTML), requiredMode=RequiredMode.REQUIRED, example = "text")
  @BeanProperty sisallonTyyppi: Optional[String],

  @(Schema @field)(description= "Järjestyksellä ei ole merkitystä", requiredMode=RequiredMode.REQUIRED, allowableValues = Array("fi", "sv", "en"), example = "[\"fi\", \"sv\"]")
  @BeanProperty kielet: Optional[util.List[String]],

  @(Schema@field)(description = "Merkkijonot jotka peitetään kun viesti näytetään raportointirajapinnassa")
  @BeanProperty maskit: Optional[util.List[Maski]],

  @(Schema @field)(example = "1.2.246.562.00.00000000000000006666", maxLength = Viesti.VIESTI_VIRKALIJAN_OID_MAX_PITUUS)
  @BeanProperty lahettavanVirkailijanOid: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
  @BeanProperty lahettaja: Optional[Lahettaja],

  @(Schema@field)(example = "ville.virkamies@oph.fi")
  @BeanProperty replyTo: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, maximum = Viesti.VIESTI_VASTAANOTTAJAT_MAX_MAARA_STR)
  @BeanProperty vastaanottajat: Optional[util.List[Vastaanottaja]],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) lataamia.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
  @BeanProperty liitteidenTunnisteet: Optional[util.List[String]],

  @(Schema @field)(example = "hakemuspalvelu")
  @BeanProperty lahettavaPalvelu: Optional[String],

  @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) luoma, jos tyhjä luodaan automaattisesti.", example = " ", nullable = true)
  @BeanProperty lahetysTunniste: Optional[String],

  @(Schema @field)(allowableValues = Array(Viesti.VIESTI_PRIORITEETTI_KORKEA, Viesti.VIESTI_PRIORITEETTI_NORMAALI), requiredMode=RequiredMode.REQUIRED, example = "normaali")
  @BeanProperty prioriteetti: Optional[String],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, minimum=Viesti.SAILYTYSAIKA_MIN_PITUUS_STR, maximum=Viesti.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
  @BeanProperty sailytysAika: Optional[Int],

  @(Schema @field)(requiredMode=RequiredMode.REQUIRED, example = "[\"APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666\"]")
  @BeanProperty kayttooikeusRajoitukset: Optional[util.List[String]],

  @(Schema @field)(example = "{ \"key\": [\"value1\", \"value2\"] }")
  @BeanProperty metadata: Optional[util.Map[String, util.List[String]]],
) {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
  }
}

