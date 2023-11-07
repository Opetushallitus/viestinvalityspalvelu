package fi.oph.viestinvalitys.model

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Viestit(tag: Tag) extends Table[(UUID, UUID, UUID, String)](tag, "viestit") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def viestipohjaTunniste = column[UUID]("viestipohja_tunniste")
  def lahetysTunniste = column[UUID]("lahetys_tunniste")
  def sahkopostiosoite = column[String]("sahkopostiosoite")
  def * = (tunniste, viestipohjaTunniste, lahetysTunniste, sahkopostiosoite)
}