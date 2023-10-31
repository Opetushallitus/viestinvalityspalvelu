package fi.oph.viestinvalitys.model

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Viestipohjat(tag: Tag) extends Table[(UUID, String)](tag, "viestipohjat") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def otsikko = column[String]("otsikko")

  def * = (tunniste, otsikko)
}