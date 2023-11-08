package fi.oph.viestinvalitys.db

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Lahetykset(tag: Tag) extends Table[(UUID, String, String)](tag, "lahetykset") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def otsikko = column[String]("otsikko")
  def omistaja = column[String]("omistaja")

  def * = (tunniste, otsikko, omistaja)
}