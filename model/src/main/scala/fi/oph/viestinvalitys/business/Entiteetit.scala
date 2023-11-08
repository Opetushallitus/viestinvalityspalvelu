package fi.oph.viestinvalitys.business

import java.util.UUID

case class Lahetys(tunniste: UUID, otsikko: String, omistaja: String)

case class ViestiPohja(tunniste: UUID, otsikko: String)

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
