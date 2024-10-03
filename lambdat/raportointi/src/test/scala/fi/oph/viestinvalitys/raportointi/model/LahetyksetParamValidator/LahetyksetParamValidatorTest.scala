package fi.oph.viestinvalitys.raportointi.model

import fi.oph.viestinvalitys.business.{RaportointiTila, VastaanottajanTila}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.*
import org.junit.jupiter.api.{Assertions, Test}

import java.util.{Optional, UUID}

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

  @Test def testValidateAlkaeUUIDParam(): Unit = {
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
}
