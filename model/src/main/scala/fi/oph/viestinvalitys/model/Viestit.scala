package fi.oph.viestinvalitys.model

import slick.jdbc.PostgresProfile.api.*

class Viestit(tag: Tag) extends Table[(Int, String)](tag, "viestit") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc) // This is the primary key column
  def heading = column[String]("heading")
  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id, heading)
}