package fi.oph.viestinvalitys.vastaanotto.security


import fi.oph.viestinvalitys.vastaanotto.security.SecurityOperaatiot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
class SecurityOperaatiotTest {

  @Test def testKatseluoikeudet(): Unit =
    val ORGANISAATIO = "1.2.3.4"
    val ORGANISAATIO2 = "1.2.3.5"
    val OIKEUS = "VIESTINVALITYS_KATSELU"
    val securityOperaatiot = SecurityOperaatiot(() => Set("ROLE_APP_" + OIKEUS, "ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO, "ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO2), () => "omistaja")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja"))

  @Test def testLahetysoikeudet(): Unit =
    val ORGANISAATIO = "1.2.3.4"
    val OIKEUS = "VIESTINVALITYS_LAHETYS"
    val securityOperaatiot = SecurityOperaatiot(() => Set("ROLE_APP_" + OIKEUS, "ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())

  @Test def testPaakayttajanOikeudet(): Unit =
    val ORGANISAATIO = SecurityConstants.OPH_ORGANISAATIO_OID
    val OIKEUS = "VIESTINVALITYS_KATSELU"
    val PAAKAYTTAJA_OIKEUS = "VIESTINVALITYS_OPH_PAAKAYTTAJA"
    val securityOperaatiot = SecurityOperaatiot(() => Set("ROLE_APP_" + PAAKAYTTAJA_OIKEUS, "ROLE_APP_" + PAAKAYTTAJA_OIKEUS+"_"+ORGANISAATIO), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja"))

}
