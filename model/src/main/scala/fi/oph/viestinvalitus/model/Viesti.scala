package fi.oph.viestinvalitus.model

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

import java.util.UUID
import scala.beans.BeanProperty

enum SisallonTyyppi extends Enum[SisallonTyyppi] {
  case text, html
}

enum Kieli extends Enum[Kieli] {
  case fi, sv, en
}

enum Prioriteetti extends Enum[Prioriteetti] {
  case normaali, korkea
}

case class Lahettaja(
              @BeanProperty nimi: String,
              @BeanProperty sahkopostiOsoite: String,
            ) {
  def this() = {
    this(null, null)
  }
}

case class Vastaanottaja(
              @BeanProperty nimi: String,
              @BeanProperty sahkopostiOsoite: String,
            ) {
  def this() = {
    this(null, null)
  }
}

case class Viesti(
              @BeanProperty otsikko: String,                        // viestin otsikko, maksimipituus 255 merkkiä
              @BeanProperty sisalto: String,                        // viestin sisältö, maksimipituus ??? merkkiä TODO: määrittele maksimipituus
              @BeanProperty sisallonTyyppi: SisallonTyyppi,         // sisällön tyyppi, text / html
              @BeanProperty kielet: Seq[Kieli],                     // sisällön kielet, fi / sv / en
              @BeanProperty lahettavanVirkailijanOid: String,       // lähettävän virkailijan tunniste
              @BeanProperty lahettaja: Lahettaja,                   // viestin lähettäjä
              @BeanProperty vastaanottajat: Seq[Vastaanottaja],     // viestin vastaanottaja
              @BeanProperty liitteet: Seq[UUID],                    // viittaukset viestin liitteisiin
              @BeanProperty lahettavaPalvelu: String,               // lähettävän palvelun tunniste
              @BeanProperty prioriteetti: Prioriteetti,             // lähettävän palvelun tunniste
              @BeanProperty sailytysAika: Int,                      // viestin säilytysaika, päiviä
              @BeanProperty kayttooikeusRajoitukset: Seq[String],   // viestin katsomiseen tarvittavat käyttöoikeudet (jokin tarvitaan)
              @BeanProperty metadata: Map[String, String],          // lähettävän palvelun viestiin liittämä metadata
            ) {
  def this() = {
    this(null, null, null, null, null, null, null, null, null, null, -1, null, null)
  }
}