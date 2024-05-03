package fi.oph.viestinvalitys.raportointi.integration

import upickle.default.*

case class OmatTiedot(
  asiointikieli: String,
  kutsumanimi: String,
  sukunimi: String
) derives ReadWriter
