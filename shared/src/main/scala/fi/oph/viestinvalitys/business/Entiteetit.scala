package fi.oph.viestinvalitys.business

import java.time.Instant
import java.util.UUID

enum SisallonTyyppi:
  case TEXT, HTML

enum Kieli:
  case EN, FI, SV

enum Prioriteetti:
  case NORMAALI, KORKEA

case class Lahetys(tunniste: UUID, otsikko: String, omistaja: String)

enum LiitteenTila:
  case SKANNAUS, PUHDAS, SAASTUNUT, VIRHE

case class Liite(tunniste: UUID, nimi: String, contentType: String, koko: Int, omistaja: String, tila: LiitteenTila)

enum VastaanottajanTila:
  case SKANNAUS, ODOTTAA, LAHETYKSESSA, VIRHE, LAHETETTY, SEND, DELIVERY, BOUNCE, COMPLAINT, REJECT, DELIVERYDELAY

case class Kontakti(nimi: Option[String], sahkoposti: String)

case class Viesti(
                   tunniste: UUID,
                   lahetys_tunniste: UUID,
                   otsikko: String,
                   sisalto: String,
                   sisallonTyyppi: SisallonTyyppi,
                   kielet: Set[Kieli],
                   lahettavanVirkailijanOID: Option[String],
                   lahettaja: Kontakti,
                   replyTo: Option[String],
                   lahettavapalvelu: Option[String],
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
