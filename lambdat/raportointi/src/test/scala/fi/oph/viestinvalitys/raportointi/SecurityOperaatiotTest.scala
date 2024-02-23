package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
class SecurityOperaatiotTest {

  val KATSELUOIKEUS = "VIESTINVALITYS_KATSELU"
  val LAHETYSOIKEUS = "VIESTINVALITYS_LAHETYS"
  val ORGANISAATIO = "1.2.3.4"
  val ORGANISAATIO2 = "1.2.3.5"

  @Test def testKayttajanOikeudet(): Unit =
    val ORGANISAATIO = "1.2.3.4"
    val OIKEUS = "OIKEUS1"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_" + OIKEUS + "_" + ORGANISAATIO), () => "")
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Option.apply(ORGANISAATIO))), securityOperaatiot.getKayttajanOikeudet())

  @Test def testKatseluoikeudet(): Unit =

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+KATSELUOIKEUS, "ROLE_APP_"+KATSELUOIKEUS+"_"+ORGANISAATIO, "ROLE_APP_"+KATSELUOIKEUS+"_"+ORGANISAATIO2), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.apply(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.apply(ORGANISAATIO)))))

  @Test def testLahetysoikeudet(): Unit =

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+LAHETYSOIKEUS, "ROLE_APP_"+LAHETYSOIKEUS+"_"+ORGANISAATIO), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.apply(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.apply(ORGANISAATIO2)))))
    // lähetysoikeuksiin ei automaattisesti sisälly katseluikeus!
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(LAHETYSOIKEUS, Option.apply(ORGANISAATIO)))))


  @Test def testPaakayttajanOikeudet(): Unit =

    val OPH_ORGANISAATIO = "1.2.246.562.10.48587687889"
    val PAAKAYTTAJA_OIKEUS = "VIESTINVALITYS_OPH_PAAKAYTTAJA"

    val securityOperaatiot = SecurityOperaatiot(() => Seq("ROLE_APP_"+PAAKAYTTAJA_OIKEUS, "ROLE_APP_"+PAAKAYTTAJA_OIKEUS+"_"+OPH_ORGANISAATIO), () => "")
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.apply(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.apply(ORGANISAATIO)))))


  @Test def testLahetyksellaEiOikeusrajauksia(): Unit =

    val securityOperaatiotKatselu = SecurityOperaatiot(() => Seq("ROLE_APP_" + KATSELUOIKEUS, "ROLE_APP_" + KATSELUOIKEUS + "_" + ORGANISAATIO), () => "")
    val securityOperaatiotLahetys = SecurityOperaatiot(() => Seq("ROLE_APP_" + LAHETYSOIKEUS, "ROLE_APP_" + LAHETYSOIKEUS + "_" + ORGANISAATIO), () => "")

    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusKatsellaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotLahetys.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
}
