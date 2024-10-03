package fi.oph.viestinvalitys.raportointi.model

import fi.oph.viestinvalitys.business.{RaportointiTila, VastaanottajanTila}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import org.junit.jupiter.api.{Assertions, Test}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Optional, UUID}
import scala.util.Random

@Test
class LahetyksetValidatorTest {

  @Test def testValidateLahetysTunniste(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateLahetysTunniste(UUID.randomUUID().toString))
    Assertions.assertEquals(Set(LAHETYSTUNNISTE_INVALID), LahetyksetParamValidator.validateLahetysTunniste("foo"))
  }

  @Test def testValidateEmailParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEmailParam(Optional.of("validi.osoite@example.org"), ALKAEN_UUID_TUNNISTE_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEmailParam(Optional.empty(), ALKAEN_UUID_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_UUID_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo"), ALKAEN_UUID_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_UUID_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo.bar@"), ALKAEN_UUID_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_UUID_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo.bar@example"), ALKAEN_UUID_TUNNISTE_INVALID))
  }

  @Test def testValidateEnintaanParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEnintaan(Optional.empty(), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MIN+1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set(VASTAANOTTAJAT_ENINTAAN_INVALID), LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MIN-1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set(VASTAANOTTAJAT_ENINTAAN_INVALID), LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MAX+1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
  }

  @Test def testValidateRaportointiTilaParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateRaportointiTila(Optional.of(RaportointiTila.epaonnistui.toString), TILA_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateRaportointiTila(Optional.empty(), TILA_INVALID))
    Assertions.assertEquals(Set(TILA_INVALID), LahetyksetParamValidator.validateRaportointiTila(Optional.of("foo"), TILA_INVALID))
    Assertions.assertEquals(Set(TILA_INVALID), LahetyksetParamValidator.validateRaportointiTila(Optional.of(VastaanottajanTila.ODOTTAA.toString), TILA_INVALID))
  }

  @Test def testValidateAlkaenUUIDParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateAlkaenUUID(Optional.of(UUID.randomUUID().toString)))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateAlkaenUUID(Optional.empty()))
    Assertions.assertEquals(Set(ALKAEN_UUID_TUNNISTE_INVALID), LahetyksetParamValidator.validateAlkaenUUID(Optional.of("foo")))
    Assertions.assertEquals(Set(ALKAEN_UUID_TUNNISTE_INVALID), LahetyksetParamValidator.validateAlkaenUUID(Optional.of("")))
  }

  @Test def testValidateOrganisaatioParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateOrganisaatio(Optional.of("1.2.246.562.10.73999728683")))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateOrganisaatio(Optional.empty()))
    Assertions.assertEquals(Set(ORGANISAATIO_INVALID), LahetyksetParamValidator.validateOrganisaatio(Optional.of("1.2.246.562.29.00000000000000032799")))
    Assertions.assertEquals(Set(ORGANISAATIO_INVALID), LahetyksetParamValidator.validateOrganisaatio(Optional.of("foo")))
  }

  @Test def testValidateHakusanaParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateHakusanaParam(Optional.of("opintopolku")))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateHakusanaParam(Optional.empty()))
    Assertions.assertEquals(Set(HAKUSANA_INVALID), LahetyksetParamValidator.validateHakusanaParam(Optional.of("haku")))
    Assertions.assertEquals(Set(HAKUSANA_INVALID), LahetyksetParamValidator.validateHakusanaParam(Optional.of(Random.nextString(HAKUSANA_MAX_LENGTH+1))))
  }

  @Test def testValidateHakuAikavaliParams(): Unit = {
    val future = Instant.now.plus(1, ChronoUnit.HOURS).toString
    val validAlku = Instant.now.minus(1, ChronoUnit.HOURS).toString
    val validLoppu = Instant.now.minus(30, ChronoUnit.MINUTES).toString
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of(validAlku), Optional.of(validLoppu)))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of(validAlku), Optional.empty()))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateHakuAikavaliParams(Optional.empty(), Optional.empty()))
    Assertions.assertEquals(Set(HAKU_ALKAEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of(future), Optional.empty()))
    Assertions.assertEquals(Set(HAKU_ALKAEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of("Tue, 01 Oct 2024 05:00:00 GMT"), Optional.empty()))
    Assertions.assertEquals(Set(HAKU_ALKAEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of("Tue, 01 Oct 2024 05:00:00 GMT"), Optional.of("foo")))
    Assertions.assertEquals(Set(HAKU_PAATTYEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.empty(), Optional.of(validLoppu)))
    Assertions.assertEquals(Set(HAKU_PAATTYEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of(validAlku), Optional.of("foo")))
    Assertions.assertEquals(Set(HAKU_PAATTYEN_INVALID), LahetyksetParamValidator.validateHakuAikavaliParams(Optional.of(Instant.now.minus(1, ChronoUnit.HOURS).toString), Optional.of(Instant.now.minus(2, ChronoUnit.HOURS).toString)))
  }
}
