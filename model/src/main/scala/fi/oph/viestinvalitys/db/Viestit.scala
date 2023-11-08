package fi.oph.viestinvalitys.db

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

enum ViestinTila:
  case SKANNAUS, ODOTTAA, LAHETYKSESSA, VIRHE, LAHETETTY, BOUNCE

class Viestit(tag: Tag) extends Table[(UUID, UUID, UUID, String, String)](tag, "viestit") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def viestipohjaTunniste = column[UUID]("viestipohja_tunniste")
  def lahetysTunniste = column[UUID]("lahetys_tunniste")
  def sahkopostiosoite = column[String]("sahkopostiosoite")
  def tila = column[String]("tila")
  def * = (tunniste, viestipohjaTunniste, lahetysTunniste, sahkopostiosoite, tila)
}