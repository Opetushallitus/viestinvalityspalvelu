package fi.oph.viestinvalitys.db

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID

class Lahetykset(tag: Tag) extends Table[(UUID, String, String)](tag, "lahetykset") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def otsikko = column[String]("otsikko")
  def omistaja = column[String]("omistaja")

  def * = (tunniste, otsikko, omistaja)
}

class Viestit(tag: Tag) extends Table[(UUID, UUID, String, String, String, Boolean, Boolean, Boolean, Option[String], String, String, String, String)](tag, "viestit") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def lahetysTunniste = column[UUID]("lahetys_tunniste")
  def otsikko = column[String]("otsikko")
  def sisalto = column[String]("sisalto")
  def sisallonTyyppi = column[String]("sisallontyyppi")
  def kieletFi = column[Boolean]("kielet_fi")
  def kieletSv = column[Boolean]("kielet_sv")
  def kieletEn = column[Boolean]("kielet_en")
  def lahettavanVirkailijanOID = column[Option[String]]("lahettavanvirkailijanoid")
  def lahettajanNimi = column[String]("lahettajannimi")
  def lahettajanSahkoposti = column[String]("lahettajansahkoposti")
  def lahettavaPalvelu = column[String]("lahettavapalvelu")
  def prioriteetti = column[String]("prioriteetti")
  def * = (tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettavanVirkailijanOID, lahettajanNimi, lahettajanSahkoposti, lahettavaPalvelu, prioriteetti)
}

class Liitteet(tag: Tag) extends Table[(UUID, String, String, Int, String, String)](tag, "liitteet") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def nimi = column[String]("nimi")
  def contentType = column[String]("contenttype")
  def koko = column[Int]("koko")
  def omistaja = column[String]("omistaja")
  def tila = column[String]("tila")

  def * = (tunniste, nimi, contentType, koko, omistaja, tila)
}

class ViestitLiitteet(tag: Tag) extends Table[(UUID, UUID)](tag, "viestit_liitteet") {
  def viestiTunniste = column[UUID]("viesti_tunniste")
  def liiteTunniste = column[UUID]("liite_tunniste")
  def * = (viestiTunniste, liiteTunniste)
}

class Vastaanottajat(tag: Tag) extends Table[(UUID, UUID, String, String, String)](tag, "vastaanottajat") {
  def tunniste = column[UUID]("tunniste", O.PrimaryKey)
  def viestiTunniste = column[UUID]("viesti_tunniste")
  def nimi = column[String]("nimi")
  def sahkopostiosoite = column[String]("sahkopostiosoite")
  def tila = column[String]("tila")
  def * = (tunniste, viestiTunniste, nimi, sahkopostiosoite, tila)
}

class Metadata(tag: Tag) extends Table[(String, String, UUID)](tag, "metadata") {
  def avain = column[String]("avain")
  def arvo = column[String]("arvo")
  def viestiTunniste = column[UUID]("viesti_tunniste")
  def * = (avain, arvo, viestiTunniste)
}