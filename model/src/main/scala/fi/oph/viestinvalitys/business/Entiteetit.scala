package fi.oph.viestinvalitys.business

import java.util.UUID

case class Lahettaja(nimi: String, sahkopostiOsoite: String)
case class Vastaanottaja(nimi: String, sahkopostiOsoite: String)

enum SisallonTyyppi:
  case TEXT, HTML

enum Kieli:
  case EN, FI, SV

enum Prioriteetti:
  case NORMAALI, KORKEA
case class ViestiRyhma(
                  otsikko: String,
                  sisalto: String,
                  sisallonTyyppi: SisallonTyyppi,
                  kielet: Set[Kieli],
                  lahettavanVirkailijanOid: Option[String],
                  lahettaja: Lahettaja,
                  vastaanottajat: Seq[Vastaanottaja],
                  liiteTunnisteet: Seq[UUID],
                  lahettavaPalvelu: String,
                  lahetysTunniste: Option[UUID],
                  prioriteetti: Prioriteetti,
                  sailytysAika: Int,
                  kayttooikeusRajoitukset: Set[String],
                  metadata: Map[String, String],
                  omistaja: String
                  )

case class Lahetys(tunniste: UUID, otsikko: String, omistaja: String)

enum LiitteenTila:
  case ODOTTAA, PUHDAS, SAASTUNUT, VIRHE

case class Liite(tunniste: UUID, nimi: String, contentType: String, koko: Int, omistaja: String, tila: LiitteenTila)

enum ViestinTila:
  case SKANNAUS, ODOTTAA, LAHETYKSESSA, VIRHE, LAHETETTY, BOUNCE

case class Viesti(
                   tunniste: UUID,
                   viestipohjaTunniste: UUID,
                   lahetysTunniste: UUID,
                   vastaanottajanNimi: String,
                   vastaanottajanSahkoposti: String,
                   tila: ViestinTila)
