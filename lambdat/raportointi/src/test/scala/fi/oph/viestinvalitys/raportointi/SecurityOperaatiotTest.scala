package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.{OPH_ORGANISAATIO_OID, SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE}
import fi.oph.viestinvalitys.raportointi.security.{SecurityConstants, SecurityOperaatiot}
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL
import jakarta.servlet.http.HttpSession
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
  val LAPSI_ORGANISAATIO = "1.2.246.562.10.2014041814455745619200"
  val ORGANISAATIO2 = "1.2.246.562.10.79559059674"
  val OIKEUS = "APP_HAKEMUS_CRUD"

  val mockOrganisaatioService = mock[OrganisaatioService]
  val mockKantaoperaatiot = mock[KantaOperaatiot]
  val mockHttpSession = mock[HttpSession]

  @Test def testKayttajaSaaOikeudetLapsiorganisaatioihin(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    when(mockOrganisaatioService.getParentOids(LAPSI_ORGANISAATIO)).thenReturn(Set(ORGANISAATIO))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(null)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))), securityOperaatiot.getKayttajanOikeudet())

  @Test def testKayttajanOikeudetHaetaanSessiosta(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    val kayttooikeudetLapsilla = Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(kayttooikeudetLapsilla)
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(kayttooikeudetLapsilla, securityOperaatiot.getKayttajanOikeudet())
    verify(mockOrganisaatioService, times(0)).getParentOids(any[String])
    verify(mockKantaoperaatiot, times(0)).getKaikkikayttooikeudet()

  @Test def testKayttajanOikeudetParsitaanJosNiitaEiLoydySessiosta(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    val OIKEUS = "APP_OIKEUS1"
    when(mockOrganisaatioService.getParentOids(LAPSI_ORGANISAATIO)).thenReturn(Set(ORGANISAATIO, OPH_ORGANISAATIO_OID))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(null)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Option.apply(LAPSI_ORGANISAATIO))))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))), securityOperaatiot.getKayttajanOikeudet())
    verify(mockOrganisaatioService, times(1)).getParentOids(LAPSI_ORGANISAATIO)

  @Test def testKayttooikeustunnisteetHaetaanKannastaJosEiLoydySessiosta(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot, mockHttpSession)
    val tunnisteet = Set(12345)
    val uusinTunniste = 12346
    val parsitutKayttooikeudet = Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)).thenReturn(null)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))))
    when(mockKantaoperaatiot.getKayttooikeusTunnisteet(parsitutKayttooikeudet.toSeq)).thenReturn((tunnisteet, uusinTunniste))
    when(mockOrganisaatioService.getParentOids(ORGANISAATIO2)).thenReturn(Set.empty)
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + LAPSI_ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Some(tunnisteet), securityOperaatiot.getKayttajanKayttooikeustunnisteet())
    verify(mockKantaoperaatiot, times(1)).getKayttooikeusTunnisteet(any)
    verify(mockHttpSession, times(1)).setAttribute(SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE, uusinTunniste)

  @Test def testKayttooikeustunnisteetHaetaanSessiostaJosSaadaan(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    val OIKEUS = "APP_OIKEUS1"
    val tunnisteet = Set(12345)
    val uusinTunniste = 12346
    val kayttooikeudetLapsilla = Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(kayttooikeudetLapsilla)
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)).thenReturn(tunnisteet)
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE)).thenReturn(uusinTunniste)
    when(mockKantaoperaatiot.getUusinKayttooikeusTunniste()).thenReturn(uusinTunniste)
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Some(tunnisteet), securityOperaatiot.getKayttajanKayttooikeustunnisteet())
    verify(mockKantaoperaatiot, times(0)).getKayttooikeusTunnisteet(any)

  @Test def testKayttooikeustunnisteetHaetaanKannastaJosKantaanOnTullutUusia(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    val tunnisteet = Set(12345)
    val uusinTunniste = 12346
    val kayttooikeudetLapsilla = Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(kayttooikeudetLapsilla)
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)).thenReturn(tunnisteet)
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE)).thenReturn(uusinTunniste)
    when(mockKantaoperaatiot.getKayttooikeusTunnisteet(kayttooikeudetLapsilla.toSeq)).thenReturn((tunnisteet, uusinTunniste+1))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Some(tunnisteet), securityOperaatiot.getKayttajanKayttooikeustunnisteet())
    verify(mockKantaoperaatiot, times(1)).getKayttooikeusTunnisteet(any)
    verify(mockHttpSession, times(1)).setAttribute(SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE, uusinTunniste+1)

  @Test def testKatseluoikeudet(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    when(mockOrganisaatioService.getAllChildOidsFlat(any[String])).thenReturn(Set.empty)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set.empty)
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_"+KATSELUOIKEUS, "ROLE_"+KATSELUOIKEUS+"_"+ORGANISAATIO, "ROLE_"+KATSELUOIKEUS+"_"+ORGANISAATIO2),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))

  @Test def testLahetysoikeudet(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot, mockHttpSession)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, None)))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_"+LAHETYSOIKEUS, "ROLE_"+OIKEUS, "ROLE_"+OIKEUS+"_"+ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)))))
    // CASista välittyvässä oikeuslistassa on aina myös pelkkä rooli ilman organisaatiota
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(OIKEUS, Option.empty))))
    Assertions.assertEquals(false, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO2)))))
    // lähetysoikeuksiin ei automaattisesti sisälly katseluikeus!
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(false, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)))))


  @Test def testPaakayttajanOikeudet(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, None)))
    val securityOperaatiot = SecurityOperaatiot(() => Seq(SECURITY_ROOLI_PAAKAYTTAJA_FULL, SECURITY_ROOLI_PAAKAYTTAJA_FULL+"_"+OPH_ORGANISAATIO_OID),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsella())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaa())
    Assertions.assertEquals(true, securityOperaatiot.onOikeusKatsellaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    Assertions.assertEquals(true, securityOperaatiot.onOikeusLahettaaEntiteetti("omistaja", Set(Kayttooikeus(KATSELUOIKEUS, Some(ORGANISAATIO)))))
    verify(mockOrganisaatioService, times(0)).getAllChildOidsFlat(any[String])

  @Test def testLahetyksellaEiOikeusrajauksia(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot, mockHttpSession)
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set.empty)
    val securityOperaatiotKatselu = SecurityOperaatiot(
      () => Seq("ROLE_" + KATSELUOIKEUS, "ROLE_" + KATSELUOIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    val securityOperaatiotLahetys = SecurityOperaatiot(
      () => Seq("ROLE_" + LAHETYSOIKEUS, "ROLE_∫" + LAHETYSOIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)

    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusKatsellaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotLahetys.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
    Assertions.assertEquals(false, securityOperaatiotKatselu.onOikeusLahettaaEntiteetti("omistaja", Set.empty))
}
