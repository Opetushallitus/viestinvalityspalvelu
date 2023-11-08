package fi.oph.viestinvalitys.db

import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

enum LiitteenTila:
  case ODOTTAA, PUHDAS, SAASTUNUT, VIRHE

class Liitteet(tag: Tag) extends Table[(UUID, String, String, Int, String, String)](tag, "liitteet") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def nimi = column[String]("nimi")
  def contentType = column[String]("content_type")
  def koko = column[Int]("koko")
  def omistaja = column[String]("omistaja")
  def tila = column[String]("tila")

  def * = (tunniste, nimi, contentType, koko, omistaja, tila)
}