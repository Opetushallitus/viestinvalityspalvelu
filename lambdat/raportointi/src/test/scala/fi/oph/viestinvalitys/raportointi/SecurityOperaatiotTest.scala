package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioClient
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock

@TestInstance(Lifecycle.PER_CLASS)
class SecurityOperaatiotTest {

  val KATSELUOIKEUS = "VIESTINVALITYS_KATSELU"
  val LAHETYSOIKEUS = "VIESTINVALITYS_LAHETYS"
  val ORGANISAATIO = "1.2.246.562.10.240484683010"
  val ORGANISAATIO2 = "1.2.246.562.10.79559059674"

  val mockOrganisaatioClient = mock[OrganisaatioClient]
  @Test def testKayttajanOikeudet(): Unit =
    val OIKEUS = "OIKEUS1"

    when(mockOrganisaatioClient.getAllChildOidsFlat(ORGANISAATIO)).thenReturn(Set("1.2.246.562.10.2014041814455745619200"))
    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioClient)
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some("1.2.246.562.10.2014041814455745619200"))), securityOperaatiot.getKayttajanOikeudet())

  @Test def testKatseluoikeudet(): Unit =
    when(mockOrganisaatioClient.getAllChildOidsFlat(any[String])).thenReturn(Set.empty)
    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+KATSELUOIKEUS, "ROLE_APP_"+KATSELUOIKEUS+"_"+ORGANISAATIO, "ROLE_APP_"+KATSELUOIKEUS+"_"+ORGANISAATIO2), () => "", mockOrganisaatioClient)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))

  @Test def testLahetysoikeudet(): Unit =

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+LAHETYSOIKEUS, "ROLE_APP_"+LAHETYSOIKEUS+"_"+ORGANISAATIO), () => "", mockOrganisaatioClient)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO2)))))
    // lähetysoikeuksiin ei automaattisesti sisälly katseluikeus!
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Some(ORGANISAATIO)))))


  @Test def testPaakayttajanOikeudet(): Unit =

    val OPH_ORGANISAATIO = "1.2.246.562.10.48587687889"
    val PAAKAYTTAJA_OIKEUS = "VIESTINVALITYS_OPH_PAAKAYTTAJA"
    when(mockOrganisaatioClient.getAllChildOidsFlat(any[String])).thenReturn(Set.empty)

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+PAAKAYTTAJA_OIKEUS, "ROLE_APP_"+PAAKAYTTAJA_OIKEUS+"_"+OPH_ORGANISAATIO), () => "", mockOrganisaatioClient)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))


  @Test def testLahetyksellaEiOikeusrajauksia(): Unit =

    val securityOperaatiotKatselu = SecurityOperaatiot(() => Seq("ROLE_APP_" + KATSELUOIKEUS, "ROLE_APP_" + KATSELUOIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioClient)
    val securityOperaatiotLahetys = SecurityOperaatiot(() => Seq("ROLE_APP_" + LAHETYSOIKEUS, "ROLE_APP_" + LAHETYSOIKEUS + "_" + ORGANISAATIO), () => "", mockOrganisaatioClient)

    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusKatsellaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotLahetys.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
}
