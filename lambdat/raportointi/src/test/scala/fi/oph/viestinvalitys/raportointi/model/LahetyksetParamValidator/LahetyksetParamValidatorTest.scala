package fi.oph.viestinvalitys.raportointi.model

import fi.oph.viestinvalitys.business.{RaportointiTila, VastaanottajanTila}
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants.{ALKAEN_AIKA_TUNNISTE_INVALID, ALKAEN_EMAIL_TUNNISTE_INVALID, LAHETYSTUNNISTE_INVALID, ORGANISAATIO_INVALID, SIVUTUS_TILA_INVALID, VASTAANOTTAJAT_ENINTAAN_INVALID, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_MIN}
import fi.oph.viestinvalitys.vastaanotto.model.{LahetysValidator, ViestiImpl}
import org.junit.jupiter.api.{Assertions, Test}

import java.util.{Optional, UUID}

@Test
class LahetyksetValidatorTest {

  @Test def testValidateLahetysTunniste(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateLahetysTunniste(UUID.randomUUID().toString))
    Assertions.assertEquals(Set(LAHETYSTUNNISTE_INVALID), LahetyksetParamValidator.validateLahetysTunniste("foo"))
  }

  @Test def testValidateEmailParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEmailParam(Optional.of("validi.osoite@example.org"), ALKAEN_EMAIL_TUNNISTE_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEmailParam(Optional.empty(), ALKAEN_EMAIL_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_EMAIL_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo"), ALKAEN_EMAIL_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_EMAIL_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo.bar@"), ALKAEN_EMAIL_TUNNISTE_INVALID))
    Assertions.assertEquals(Set(ALKAEN_EMAIL_TUNNISTE_INVALID), LahetyksetParamValidator.validateEmailParam(Optional.of("foo.bar@example"), ALKAEN_EMAIL_TUNNISTE_INVALID))
  }

  @Test def testValidateEnintaanParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEnintaan(Optional.empty(), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MIN+1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set(VASTAANOTTAJAT_ENINTAAN_INVALID), LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MIN-1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
    Assertions.assertEquals(Set(VASTAANOTTAJAT_ENINTAAN_INVALID), LahetyksetParamValidator.validateEnintaan(Optional.of((VASTAANOTTAJAT_ENINTAAN_MAX+1).toString), VASTAANOTTAJAT_ENINTAAN_MIN, VASTAANOTTAJAT_ENINTAAN_MAX, VASTAANOTTAJAT_ENINTAAN_INVALID))
  }

  @Test def testValidateRaportointiTilaParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateRaportointiTila(Optional.of(RaportointiTila.epaonnistui.toString), SIVUTUS_TILA_INVALID))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateRaportointiTila(Optional.empty(), SIVUTUS_TILA_INVALID))
    Assertions.assertEquals(Set(SIVUTUS_TILA_INVALID), LahetyksetParamValidator.validateRaportointiTila(Optional.of("foo"), SIVUTUS_TILA_INVALID))
    Assertions.assertEquals(Set(SIVUTUS_TILA_INVALID), LahetyksetParamValidator.validateRaportointiTila(Optional.of(VastaanottajanTila.ODOTTAA.toString), SIVUTUS_TILA_INVALID))
  }

  @Test def testValidateAlkaenAikaParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateAlkaenAika(Optional.of("2024-03-06T20:05:26.102479Z")))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateAlkaenAika(Optional.empty()))
    Assertions.assertEquals(Set(ALKAEN_AIKA_TUNNISTE_INVALID), LahetyksetParamValidator.validateAlkaenAika(Optional.of("2024-02-30T20:05:26.102479Z")))
    Assertions.assertEquals(Set(ALKAEN_AIKA_TUNNISTE_INVALID), LahetyksetParamValidator.validateAlkaenAika(Optional.of("foo")))
    Assertions.assertEquals(Set(ALKAEN_AIKA_TUNNISTE_INVALID), LahetyksetParamValidator.validateAlkaenAika(Optional.of("")))
  }

  @Test def testValidateOrganisaatioParam(): Unit = {
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateOrganisaatio(Optional.of("1.2.246.562.10.73999728683")))
    Assertions.assertEquals(Set.empty, LahetyksetParamValidator.validateOrganisaatio(Optional.empty()))
    Assertions.assertEquals(Set(ORGANISAATIO_INVALID), LahetyksetParamValidator.validateOrganisaatio(Optional.of("1.2.246.562.29.00000000000000032799")))
    Assertions.assertEquals(Set(ORGANISAATIO_INVALID), LahetyksetParamValidator.validateOrganisaatio(Optional.of("foo")))
  }
}
