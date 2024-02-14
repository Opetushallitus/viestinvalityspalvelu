package fi.oph.viestinvalitys.raportointi.model

import com.fasterxml.jackson.annotation.JsonInclude

import java.util
import java.util.Optional
import scala.beans.BeanProperty


class PalautaLahetyksetResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class PalautaLahetyksetSuccessResponse(
                                             @BeanProperty lahetykset: java.util.List[PalautaLahetysSuccessResponse],
                                             @BeanProperty seuraavatAlkaen: Optional[String]
                                           ) extends PalautaLahetyksetResponse

case class PalautaLahetyksetFailureResponse(
                                             @BeanProperty virheet: util.List[String],
                                           ) extends PalautaLahetyksetResponse


class PalautaLahetysResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class PalautaLahetysSuccessResponse(
                                          @BeanProperty lahetysTunniste: String,
                                          @BeanProperty otsikko: String,
                                          @BeanProperty omistaja: String,
                                          @BeanProperty lahettavaPalvelu: String,
                                          @BeanProperty lahettavanVirkailijanOID: String,
                                          @BeanProperty lahettajanNimi: String,
                                          @BeanProperty lahettajanSahkoposti: String,
                                          @BeanProperty replyTo: String,
                                          @BeanProperty luotu: String,
                                          @BeanProperty tilat: java.util.List[VastaanottajatTilassa]
                                        ) extends PalautaLahetysResponse

case class PalautaLahetysFailureResponse(
                                          @BeanProperty virhe: String,
                                        ) extends PalautaLahetysResponse

case class VastaanottajatTilassa(
                                  @BeanProperty vastaanottotila: String,
                                  @BeanProperty vastaanottajaLkm: Int
                                )

class VastaanottajatResponse() {}

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajaResponse(
                                  @BeanProperty tunniste: String,
                                  @BeanProperty nimi: Optional[String],
                                  @BeanProperty sahkoposti: String,
                                  @BeanProperty viestiTunniste: String,
                                  @BeanProperty tila: String
                                )

@JsonInclude(JsonInclude.Include.NON_ABSENT)
case class VastaanottajatSuccessResponse(
                                          @BeanProperty vastaanottajat: java.util.List[VastaanottajaResponse],
                                          @BeanProperty seuraavatAlkaen: Optional[String],
                                          @BeanProperty viimeisenTila: Optional[String],
                                        ) extends VastaanottajatResponse

case class VastaanottajatFailureResponse(
                                          @BeanProperty virheet: util.List[String],
                                        ) extends VastaanottajatResponse

