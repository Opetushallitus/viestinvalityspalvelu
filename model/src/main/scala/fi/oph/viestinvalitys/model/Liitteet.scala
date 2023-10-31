package fi.oph.viestinvalitys.model

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Liitteet(tag: Tag) extends Table[(UUID, String, String, Int, String)](tag, "liitteet") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def nimi = column[String]("nimi")
  def contentType = column[String]("content_type")
  def koko = column[Int]("koko")
  def omistaja = column[String]("omistaja")

  def * = (tunniste, nimi, contentType, koko, omistaja)
}