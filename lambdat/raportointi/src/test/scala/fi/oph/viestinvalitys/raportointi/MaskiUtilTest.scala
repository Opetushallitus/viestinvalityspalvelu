package fi.oph.viestinvalitys.raportointi

import com.github.f4b6a3.uuid.UuidCreator
import fi.oph.viestinvalitys.raportointi.resource.MaskiUtil
import org.junit.jupiter.api.{Assertions, Test, TestInstance}
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
class MaskiUtilTest {

  @Test def testMaskaaDefaultArvolla(): Unit =
    val maskit = Map(
      "1.2.246.562.11.00000000000002065719" -> None,
    )
    val input = "Opintopolku: hakemuksesi on vastaanotettu (Hakemusnumero: 1.2.246.562.11.00000000000002065719)"
    Assertions.assertEquals("Opintopolku: hakemuksesi on vastaanotettu (Hakemusnumero: xxxxx)", MaskiUtil.maskaaSalaisuudet(input, maskit))

  @Test def testMaskaaUseampia(): Unit =

    val maskit = Map("1.2.246.562.11.00000000000002065719" -> Some("salattu"),
      "Maija" -> Some("Etunimi"))
    val input = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body>" +
      "<H1>Hakemuksesi on vastaanotettu</H1><p>Hei Maija!</p><p>Hakemuksesi numero 1.2.246.562.11.00000000000002065719 on vastaanotettu</p>"
    Assertions.assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body>" +
      "<H1>Hakemuksesi on vastaanotettu</H1><p>Hei Etunimi!</p><p>Hakemuksesi numero salattu on vastaanotettu</p>",
      MaskiUtil.maskaaSalaisuudet(input, maskit))

  @Test def testMaskaaErikoismerkit(): Unit =
    val maskit = Map("1.2.246.562.11.00000000000002065719" -> Some("salattu"),
      "https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA" -> Some("salattuosoite"))
    val input = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body>" +
      "<H1>Hakemuksesi on vastaanotettu</H1><p>Hakemusnumero: 1.2.246.562.11.00000000000002065719</p>" +
      "<p><a href=\"https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA\">https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA</a>  Voit katsella ja muokata hakemustasi yllä olevan linkin kautta. Älä jaa linkkiä ulkopuolisille. Jos käytät yhteiskäyttöistä tietokonetta, muista kirjautua ulos sähköpostiohjelmasta.</p>"
    Assertions.assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body>" +
      "<H1>Hakemuksesi on vastaanotettu</H1><p>Hakemusnumero: salattu</p>" +
      "<p><a href=\"salattuosoite\">salattuosoite</a>  Voit katsella ja muokata hakemustasi yllä olevan linkin kautta. Älä jaa linkkiä ulkopuolisille. Jos käytät yhteiskäyttöistä tietokonetta, muista kirjautua ulos sähköpostiohjelmasta.</p>",
      MaskiUtil.maskaaSalaisuudet(input, maskit))

  @Test def testYhdistaUseitaMaskeja(): Unit =
    val maskit = Map("1.2.246.562.11.00000000000002065719" -> Some("salattu"))
    val maskit2 = Map("1.2.246.562.11.00000000000002065720" -> Some("salattu"), "1.2.246.562.11.00000000000002065721" -> Some("salattu"))
    val maskit3 = Map("1.2.246.562.11.00000000000002065721" -> Some("salattu2"))
    val maskilista = List(maskit, maskit2, maskit3)
    val mergemap = MaskiUtil.mergeMaps(maskilista)
    val input = "Opintopolku: hakemuksesi on vastaanotettu (Hakemusnumero: 1.2.246.562.11.00000000000002065721)"
    Assertions.assertEquals("Opintopolku: hakemuksesi on vastaanotettu (Hakemusnumero: salattu2)",
      MaskiUtil.maskaaSalaisuudet(input, mergemap))
}
