package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.OPH_ORGANISAATIO_OID
import fi.oph.viestinvalitys.raportointi.security.{SecurityConstants, SecurityOperaatiot}
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock

@TestInstance(Lifecycle.PER_CLASS)
class SecurityOperaatiotTest {

  val KATSELUOIKEUS = "APP_VIESTINVALITYS_KATSELU"
  val LAHETYSOIKEUS = "APP_VIESTINVALITYS_LAHETYS"
  val ORGANISAATIO = "1.2.246.562.10.240484683010"
  val ORGANISAATIO2 = "1.2.246.562.10.79559059674"

  val mockOrganisaatioService = mock[OrganisaatioService]

  @Test def testKayttajanOikeudet(): Unit =
    val OIKEUS = "APP_OIKEUS1"

    when(mockOrganisaatioService.getAllChildOidsFlat(ORGANISAATIO)).thenReturn(Set("1.2.246.562.10.2014041814455745619200"))
    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioService)
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some("1.2.246.562.10.2014041814455745619200"))), securityOperaatiot.getKayttajanOikeudet())

  @Test def testKatseluoikeudet(): Unit =
    when(mockOrganisaatioService.getAllChildOidsFlat(any[String])).thenReturn(Set.empty)
    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_"+KATSELUOIKEUS, "ROLE_"+KATSELUOIKEUS+"_"+ORGANISAATIO, "ROLE_"+KATSELUOIKEUS+"_"+ORGANISAATIO2), () => "", mockOrganisaatioService)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))

  @Test def testLahetysoikeudet(): Unit =

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_"+LAHETYSOIKEUS, "ROLE_"+LAHETYSOIKEUS+"_"+ORGANISAATIO), () => "", mockOrganisaatioService)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO2)))))
    // lähetysoikeuksiin ei automaattisesti sisälly katseluikeus!
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO)))))


  @Test def testPaakayttajanOikeudet(): Unit =
    
    reset(mockOrganisaatioService)
    val securityOperaatiot = SecurityOperaatiot(() => Seq(SECURITY_ROOLI_PAAKAYTTAJA_FULL, SECURITY_ROOLI_PAAKAYTTAJA_FULL+"_"+OPH_ORGANISAATIO_OID), () => "", mockOrganisaatioService)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    verify(mockOrganisaatioService, times(0)).getAllChildOidsFlat(any[String])

  @Test def testLahetyksellaEiOikeusrajauksia(): Unit =

    val securityOperaatiotKatselu = SecurityOperaatiot(() => Seq("ROLE_" + KATSELUOIKEUS, "ROLE_" + KATSELUOIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioService)
    val securityOperaatiotLahetys = SecurityOperaatiot(() => Seq("ROLE_" + LAHETYSOIKEUS, "ROLE_∫" + LAHETYSOIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioService)

    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusKatsellaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotLahetys.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
}
