package fi.oph.viestinvalitys.raportointi.model

import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import fi.oph.viestinvalitys.raportointi.resource.{ParametriUtil, RaportointiAPIConstants}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioOid

import java.time.Instant
import java.util.Optional
import scala.util.matching.Regex

case class VastaanottajatParams(lahetysTunniste: String,
                                alkaen: Optional[String],
                                enintaan: Optional[String],
                                tila: Optional[String],
                                vastaanottajanEmail: Optional[String],
                                organisaatio: Optional[String])

case class LahetyksetParams(alkaen: Optional[String],
                            enintaan: Optional[String],
                            vastaanottajanEmail: Optional[String],
                            organisaatio: Optional[String],
                            viesti: Optional[String],
                            palvelu: Optional[String],
                            lahettaja: Optional[String],
                            hakuAlkaen: Optional[String],
                            hakuPaattyen: Optional[String])
object LahetyksetParamValidator {

  def validateAlkaenUUID(alkaen: Optional[String]): Set[String] =
    val alkaenAika = ParametriUtil.asUUID(alkaen)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (alkaen.isPresent && alkaenAika.isEmpty) Left(virheet.incl(RaportointiAPIConstants.ALKAEN_UUID_TUNNISTE_INVALID)) else Right(virheet))
      .fold(l => l, r => r)

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

  def validateHakuAikavaliParams(hakuAlkaenParam: Optional[String], hakuPaattyenParam: Optional[String]): Set[String] =
    val validatedHakuAlkaen = ParametriUtil.asInstant(hakuAlkaenParam)
    val validatedHakuPaattyen = ParametriUtil.asInstant(hakuPaattyenParam)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (hakuAlkaenParam.isPresent && (validatedHakuAlkaen.isEmpty || validatedHakuAlkaen.get.isAfter(Instant.now))) Left(virheet.incl(HAKU_ALKAEN_INVALID)) else Right(virheet))
      .flatMap(virheet =>
        if (hakuPaattyenParam.isPresent && (validatedHakuAlkaen.isEmpty || validatedHakuPaattyen.isEmpty || !validatedHakuPaattyen.get.isAfter(validatedHakuAlkaen.get))) Left(virheet.incl(HAKU_PAATTYEN_INVALID)) else Right(virheet))
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

  def validateOrganisaatio(organisaatio: Optional[String]): Set[String] =
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (organisaatio.isPresent && !OrganisaatioOid.isValid(organisaatio.get())) Left(virheet.incl(ORGANISAATIO_INVALID)) else Right(virheet))
      .fold(l => l, r => r)

  def validateHakusanaParam(hakusanaParam: Optional[String]): Set[String] =
    val validatedHakusana = ParametriUtil.asValidHakusana(hakusanaParam)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (hakusanaParam.isPresent && validatedHakusana.isEmpty) Left(virheet.incl(HAKUSANA_INVALID)) else Right(virheet))
      .fold(l => l, r => r)

  def validateLahettajaParam(lahettajaOidParam: Optional[String]): Set[String] =
    val validatedLahettajaOid = ParametriUtil.asValidHenkiloOid(lahettajaOidParam)
    Right(Set.empty.asInstanceOf[Set[String]])
      .flatMap(virheet =>
        if (lahettajaOidParam.isPresent && validatedLahettajaOid.isEmpty) Left(virheet.incl(LAHETTAJA_INVALID)) else Right(virheet))
      .fold(l => l, r => r)

  def validateVastaanottajatParams(params: VastaanottajatParams): Seq[String] =
    Seq(
      validateLahetysTunniste(params.lahetysTunniste),
      validateAlkaenUUID(params.alkaen),
      validateEnintaan(params.enintaan, VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID),
      validateEmailParam(params.vastaanottajanEmail, VASTAANOTTAJA_INVALID),
      validateRaportointiTila(params.tila, TILA_INVALID),
      validateOrganisaatio(params.organisaatio)
    ).flatten

  def validateLahetyksetParams(params: LahetyksetParams): Seq[String] =
    Seq(
      validateAlkaenUUID(params.alkaen),
      validateEnintaan(params.enintaan, LAHETYKSET_ENINTAAN_MIN, LAHETYKSET_ENINTAAN_MAX, LAHETYKSET_ENINTAAN_INVALID),
      validateEmailParam(params.vastaanottajanEmail, VASTAANOTTAJA_INVALID),
      validateOrganisaatio(params.organisaatio),
      validateHakusanaParam(params.viesti),
      validateHakusanaParam(params.palvelu),
      validateLahettajaParam(params.lahettaja),
      validateHakuAikavaliParams(params.hakuAlkaen, params.hakuPaattyen)
    ).flatten
}
