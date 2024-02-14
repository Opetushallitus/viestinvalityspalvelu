package fi.oph.viestinvalitys.raportointi.model

import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import fi.oph.viestinvalitys.raportointi.resource.{ParametriUtil, RaportointiAPIConstants}

import java.util.Optional

case class VastaanottajatParams(lahetysTunniste: String,
                                alkaen: Optional[String],
                                enintaan: Optional[String],
                                sivutustila: Optional[String],
                                tila: Optional[String],
                                vastaanottajanEmail: Optional[String])
object LahetyksetParamValidator {

  def validateLahetysTunniste(lahetysTunniste: String): Set[String] =
    val uuid = ParametriUtil.asUUID(lahetysTunniste)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (uuid.isEmpty) Left(virheet.incl(LAHETYSTUNNISTE_INVALID)) else Right(virheet))
      .fold(l => l, r => r)

  def validateEmailParam(emailParam: Optional[String], errorMessage: String): Set[String] =
    val validatedEmail = ParametriUtil.asValidEmail(emailParam)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (emailParam.isPresent && validatedEmail.isEmpty) Left(virheet.incl(errorMessage)) else Right(virheet))
      .fold(l => l, r => r)

  def validateEnintaan(enintaan: Optional[String], min: Int, max: Int, errorMessage: String): Set[String] =
    val enintaanInt = ParametriUtil.asInt(enintaan)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (enintaan.isPresent && (enintaanInt.isEmpty || enintaanInt.get < min || enintaanInt.get > max))
          Left(virheet.incl(errorMessage))
        else Right(virheet))
      .fold(l => l, r => r)

  def validateRaportointiTila(tila: Optional[String], errorMessage: String): Set[String] =
    val raportointiTila = ParametriUtil.asValidRaportointitila(tila)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (tila.isPresent && raportointiTila.isEmpty) Left(virheet.incl(errorMessage)) else Right(virheet))
      .fold(l => l, r => r)

  def validateVastaanottajatParams(params: VastaanottajatParams): Seq[String] =
    Seq(
      validateLahetysTunniste(params.lahetysTunniste),
      validateEmailParam(params.alkaen, ALKAEN_EMAIL_TUNNISTE_INVALID),
      validateEnintaan(params.enintaan, VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID),
      validateRaportointiTila(params.sivutustila, SIVUTUS_TILA_INVALID),
      validateEmailParam(params.vastaanottajanEmail, VASTAANOTTAJA_INVALID),
      validateRaportointiTila(params.tila, TILA_INVALID)
    ).flatten

}
