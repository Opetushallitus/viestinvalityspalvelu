package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Liite.*

import java.net.URLConnection
import scala.beans.BeanProperty

case class LiiteImpl(@BeanProperty tiedostoNimi: String, @BeanProperty sisaltoTyyppi: String, @BeanProperty bytes: Array[Byte]) extends Liite

class LiiteBuilderImpl() extends TiedostoNimiBuilder, BytesBuilder, LiiteBuilder {

  var liite = LiiteImpl(null, null, null)

  override def withFileName(tiedostoNimi: String): BytesBuilder =
    liite = liite.copy(tiedostoNimi = tiedostoNimi)
    this

  override def withBytes(bytes: Array[Byte]): LiiteBuilder =
    liite = liite.copy(bytes = bytes)
    this

  override def withContentType(sisaltoTyyppi: String): LiiteBuilder =
    liite = liite.copy(sisaltoTyyppi = sisaltoTyyppi)
    this

  override def build(): Liite =
    if(liite.sisaltoTyyppi==null)
      liite = liite.copy(sisaltoTyyppi = URLConnection.guessContentTypeFromName(liite.tiedostoNimi))

    liite
}
