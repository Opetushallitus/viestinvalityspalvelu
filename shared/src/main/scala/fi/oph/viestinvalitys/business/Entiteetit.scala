package fi.oph.viestinvalitys.business

import java.time.Instant
import java.util.UUID

enum SisallonTyyppi:
  case TEXT, HTML

enum Kieli:
  case EN, FI, SV

enum Prioriteetti:
  case NORMAALI, KORKEA

case class Lahetys(
                    tunniste: UUID,
                    otsikko: String,
                    omistaja: String,
                    lahettavaPalvelu: String,
                    lahettavanVirkailijanOID: Option[String],
                    lahettaja: Kontakti,
                    replyTo: Option[String],
                    prioriteetti: Prioriteetti,
                    luotu: Instant
                  )

enum LiitteenTila:
  case SKANNAUS, PUHDAS, SAASTUNUT, VIRHE

case class Liite(tunniste: UUID, nimi: String, contentType: String, koko: Int, omistaja: String, tila: LiitteenTila)

enum VastaanottajanTila:
  case SKANNAUS, ODOTTAA, LAHETYKSESSA, VIRHE, LAHETETTY, SEND, DELIVERY, BOUNCE, COMPLAINT, REJECT, DELIVERYDELAY

enum RaportointiTila:
  case epaonnistui, kesken, valmis

case object raportointiTilat {
  val epaonnistuneet = Set(VastaanottajanTila.VIRHE, VastaanottajanTila.BOUNCE, VastaanottajanTila.COMPLAINT, VastaanottajanTila.REJECT)
  val kesken = Set(VastaanottajanTila.SKANNAUS, VastaanottajanTila.ODOTTAA, VastaanottajanTila.LAHETYKSESSA, VastaanottajanTila.LAHETETTY, VastaanottajanTila.SEND, VastaanottajanTila.DELIVERYDELAY)
  val valmiit = Set(VastaanottajanTila.DELIVERY)
}

case class Kontakti(nimi: Option[String], sahkoposti: String)

case class Kayttooikeus(oikeus: String, organisaatio: Option[String])

case class Viesti(
                   tunniste: UUID,
                   lahetysTunniste: UUID,
                   otsikko: String,
                   sisalto: String,
                   sisallonTyyppi: SisallonTyyppi,
                   kielet: Set[Kieli],
                   maskit: Map[String, Option[String]],
                   lahettavaPalvelu: String,
                   lahettavanVirkailijanOID: Option[String],
                   lahettaja: Kontakti,
                   replyTo: Option[String],
                   omistaja: String,
                   prioriteetti: Prioriteetti
                 )

/** Sisältää vain viesti-taulun tiedot, ei lisää lähetyksen tietoja */
case class RaportointiViesti(
                   tunniste: UUID,
                   lahetysTunniste: UUID,
                   otsikko: String,
                   sisalto: String,
                   sisallonTyyppi: SisallonTyyppi,
                   kielet: Set[Kieli],
                   maskit: Map[String, Option[String]],
                   omistaja: String,
                   prioriteetti: Prioriteetti
                 )

case class Vastaanottaja(
                   tunniste: UUID,
                   viestiTunniste: UUID,
                   kontakti: Kontakti,
                   tila: VastaanottajanTila,
                   prioriteetti: Prioriteetti,
                   sesTunniste: Option[String]
                 )

case class VastaanottajanSiirtyma(
                    aika: Instant,
                    tila: VastaanottajanTila,
                    lisatiedot: String
                 )
