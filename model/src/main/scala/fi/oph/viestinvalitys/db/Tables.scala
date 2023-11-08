package fi.oph.viestinvalitys.db

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Lahetykset(tag: Tag) extends Table[(UUID, String, String)](tag, "lahetykset") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def otsikko = column[String]("otsikko")
  def omistaja = column[String]("omistaja")

  def * = (tunniste, otsikko, omistaja)
}

class Viestipohjat(tag: Tag) extends Table[(UUID, String)](tag, "viestipohjat") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def otsikko = column[String]("otsikko")

  def * = (tunniste, otsikko)
}

class Liitteet(tag: Tag) extends Table[(UUID, String, String, Int, String, String)](tag, "liitteet") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def nimi = column[String]("nimi")
  def contentType = column[String]("content_type")
  def koko = column[Int]("koko")
  def omistaja = column[String]("omistaja")
  def tila = column[String]("tila")

  def * = (tunniste, nimi, contentType, koko, omistaja, tila)
}

class Viestit(tag: Tag) extends Table[(UUID, UUID, UUID, String, String)](tag, "viestit") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def viestipohjaTunniste = column[UUID]("viestipohja_tunniste")
  def lahetysTunniste = column[UUID]("lahetys_tunniste")
  def sahkopostiosoite = column[String]("sahkopostiosoite")
  def tila = column[String]("tila")
  def * = (tunniste, viestipohjaTunniste, lahetysTunniste, sahkopostiosoite, tila)
}