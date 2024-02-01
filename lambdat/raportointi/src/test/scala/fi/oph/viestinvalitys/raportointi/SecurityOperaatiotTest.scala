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
}
