package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Liite.*

import java.net.URLConnection
import scala.beans.BeanProperty

case class LiiteImpl(@BeanProperty tiedostoNimi: String, @BeanProperty sisaltoTyyppi: String, @BeanProperty bytes: Array[Byte]) extends Liite

class LiiteBuilderImpl(liite: LiiteImpl) extends TiedostoNimiBuilder, BytesBuilder, LiiteBuilder {

  def this() =
    this(LiiteImpl(null, null, null))

  override def withFileName(tiedostoNimi: String): BytesBuilder =
    LiiteBuilderImpl(liite.copy(tiedostoNimi = tiedostoNimi))

  override def withBytes(bytes: Array[Byte]): LiiteBuilder =
    LiiteBuilderImpl(liite.copy(bytes = bytes))

  override def withContentType(sisaltoTyyppi: String): LiiteBuilder =
    LiiteBuilderImpl(liite.copy(sisaltoTyyppi = sisaltoTyyppi))

  override def build(): Liite =
    if(liite.sisaltoTyyppi==null)
      liite.copy(sisaltoTyyppi = URLConnection.guessContentTypeFromName(liite.tiedostoNimi))
    else
      liite
}
