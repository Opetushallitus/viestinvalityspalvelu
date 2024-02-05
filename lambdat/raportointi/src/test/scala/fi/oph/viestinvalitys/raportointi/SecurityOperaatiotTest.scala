package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
class SecurityOperaatiotTest {

  @Test def testLahetysRoundtrip(): Unit =
    val ORGANISAATIO = "1.2.3.4"
    val OIKEUS = "OIKEUS1"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO), () => "")
    Assertions.assertEquals(Set(Kayttooikeus(Option.apply(ORGANISAATIO), OIKEUS)), securityOperaatiot.getKayttajanOikeudet())

  @Test def testKatseluoikeudet(): Unit =

    val ORGANISAATIO = "1.2.3.4"
    val ORGANISAATIO2 = "1.2.3.5"
    val OIKEUS = "VIESTINVALITYS_KATSELU"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO, "ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO2), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(Option.apply(ORGANISAATIO), OIKEUS))))

  @Test def testLahetysoikeudet(): Unit =

    val ORGANISAATIO = "1.2.3.4"
    val ORGANISAATIO2 = "1.2.3.5"
    val OIKEUS = "VIESTINVALITYS_LAHETYS"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(Option.apply(ORGANISAATIO), OIKEUS))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(Option.apply(ORGANISAATIO2), OIKEUS))))

  @Test def testPaakayttajanOikeudet(): Unit =

    val ORGANISAATIO = "1.2.3.4"
    val OIKEUS = "VIESTINVALITYS_KATSELU"
    val PAAKAYTTAJA_OIKEUS = "VIESTINVALITYS_OPH_PAAKAYTTAJA_1.2.246.562.10.48587687889"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + PAAKAYTTAJA_OIKEUS), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(Option.apply(ORGANISAATIO), OIKEUS))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(Option.apply(ORGANISAATIO), OIKEUS))))

}
