package fi.oph.viestinvalitys.business

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
  case SKANNAUS, ODOTTAA, LAHETYKSESSA, VIRHE, LAHETETTY, BOUNCE

case class Kontakti(nimi: String, sahkoposti: String)

case class Viesti(
                   tunniste: UUID,
                   lahetys_tunniste: UUID,
                   otsikko: String,
                   sisalto: String,
                   sisallonTyyppi: SisallonTyyppi,
                   kielet: Set[Kieli],
                   lahettavanVirkailijanOID: Option[String],
                   lahettaja: Kontakti,
                   lahettavapalvelu: String
                 )

case class Vastaanottaja(
                   tunniste: UUID,
                   viestiTunniste: UUID,
                   kontakti: Kontakti,
                   tila: VastaanottajanTila,
                   prioriteetti: Prioriteetti
                 )
