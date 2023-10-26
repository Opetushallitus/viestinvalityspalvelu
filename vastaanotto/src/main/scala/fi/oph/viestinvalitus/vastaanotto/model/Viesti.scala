package fi.oph.viestinvalitus.vastaanotto.model

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fi.oph.viestinvalitus.model.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty

object Viesti {
  final val OTSIKKO_MAX_PITUUS = 255
  final val SISALTO_MAX_PITUUS = 6*1024*1024 // SES-viesteissä maksimikoko 10 megatavua, mennään varmuuden vuoksi reilusti alle

  final val LAHETTAVAPALVELU_MAX_PITUUS = 127
  
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
              @(Schema @field)(example = "Opintopolku")
              @BeanProperty nimi: String,

              @(Schema @field)(description="Domainin pitää olla opintopolku.fi", example = "noreply@opintopolku.fi")
              @BeanProperty sahkopostiOsoite: String,
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
              @(Schema @field)(example = "Vallu Vastaanottaja")
              @BeanProperty nimi: String,

              @(Schema @field)(example = "vallu.vastaanottaja@example.com")
              @BeanProperty sahkopostiOsoite: String,
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
              @BeanProperty otsikko: String,

                   @(Schema @field)(example = "Syvällinen sisältö", requiredMode=RequiredMode.REQUIRED, maxLength = Viesti.SISALTO_MAX_PITUUS)
              @BeanProperty sisalto: String,

                   @(Schema @field)(allowableValues = Array(Viesti.VIESTI_SISALTOTYYPPI_TEXT, Viesti.VIESTI_SISALTOTYYPPI_HTML), requiredMode=RequiredMode.REQUIRED, example = "text")
              @BeanProperty sisallonTyyppi: String,

                   @(Schema @field)(description= "Järjestyksellä ei ole merkitystä", requiredMode=RequiredMode.REQUIRED, allowableValues = Array("fi", "sv", "en"), example = "[\"fi\", \"sv\"]")
              @BeanProperty kielet: java.util.List[String],

                   @(Schema @field)(example = "1.2.246.562.00.00000000000000006666")
              @BeanProperty lahettavanVirkailijanOid: java.util.Optional[String],

                   @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
              @BeanProperty lahettaja: Lahettaja,

                   @(Schema @field)(requiredMode=RequiredMode.REQUIRED)
              @BeanProperty vastaanottajat: java.util.List[Vastaanottaja],

                   @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) lataamia.", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
              @BeanProperty liitteidenTunnisteet: java.util.List[String],

                   @(Schema @field)(example = "hakemuspalvelu")
              @BeanProperty lahettavaPalvelu: String,

                   @(Schema @field)(description = "Täytyy olla saman käyttäjän (cas-identiteetti) luoma, jos tyhjä luodaan automaattisesti.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
             @BeanProperty lahetysTunniste: String,

                   @(Schema @field)(allowableValues = Array(Viesti.VIESTI_PRIORITEETTI_KORKEA, Viesti.VIESTI_PRIORITEETTI_NORMAALI), requiredMode=RequiredMode.REQUIRED, example = "normaali")
              @BeanProperty prioriteetti: String,

                   @(Schema @field)(requiredMode=RequiredMode.REQUIRED, minimum=Viesti.SAILYTYSAIKA_MIN_PITUUS_STR, maximum=Viesti.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
              @BeanProperty sailytysAika: Int,

                   @(Schema @field)(requiredMode=RequiredMode.REQUIRED, example = "[\"APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666\"]")
              @BeanProperty kayttooikeusRajoitukset: java.util.List[String],

                   @(Schema @field)(example = "{ \"key\": \"value\" }")
              @BeanProperty metadata: java.util.Map[String, String],
            ) {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null, null, null, null, null, null, null, null, null, null, -1, null, null)
  }
}

