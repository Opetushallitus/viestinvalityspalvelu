package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.{OPH_ORGANISAATIO_OID, SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE}
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

  /**
   * Data-alustuksien skenaarioissa mockataan
   * - sessioattribuutit jossa käyttöoikeuksia ja niiden tunnisteita säilötään
   * - kantaoperaatiot.getKaikkikayttooikeudet() - palvelussa käytössä olevat käyttöoikeusrajaukset
   * - kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet) - käyttäjän käyttöoikeuksien tunnisteet
   *   sekä uusin käyttöoikeustunniste
   * - kantaOperaatiot.getUusinKayttooikeusTunniste() - uusin käyttöoikeustunniste
   * - organisaatioService.getParentOids(oid) - käyttöoikeuden organisaation yläorganisaatiot
   * - SecurityOperaatiot alustuksessa lista kirjautuneen käyttäjän käyttöoikeuksista
   */
  @Test def testKayttajaSaaOikeudetLapsiorganisaatioihin(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    // Kun käyttöoikeuksia ei ole ladattu sessioon
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(null)
    // ja käyttäjältä löytyy oikeus käyttöoikeusrajauksessa olevan organisaation yläorganisaatioon
    when(mockOrganisaatioService.getParentOids(LAPSI_ORGANISAATIO)).thenReturn(Set(ORGANISAATIO, OPH_ORGANISAATIO_OID))
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    // käyttäjä saa oikeudet myös lapsiorganisaatioon
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))), securityOperaatiot.getKayttajanOikeudet())
    // käyttöoikeuden yläorganisaatio on haettu organisaatiopalvelusta (cache)
    verify(mockOrganisaatioService, times(1)).getParentOids(LAPSI_ORGANISAATIO)

  @Test def testKayttajanOikeudetHaetaanSessiosta(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    val kayttooikeudetLapsilla = Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    // jos käyttäjän oikeudet ovat sessioattribuutissa
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(kayttooikeudetLapsilla)
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    // palautetaan käyttäjän oikeudet sessiosta
    Assertions.assertEquals(kayttooikeudetLapsilla, securityOperaatiot.getKayttajanOikeudet())
    // eikä tutkita organisaatiohierarkiaa eikä palvelun käyttöoikeuksia kannasta
    verify(mockOrganisaatioService, times(0)).getParentOids(any[String])
    verify(mockKantaoperaatiot, times(0)).getKaikkikayttooikeudet()

  @Test def testOphOrganisaatiollaSaaAinaOikeudet(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot)
    // jos käyttäjän oikeuksia ei ole ladattu sessioon,
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(null)
    // palvelun käyttöoikeusrajauksissa on alemman tason organisaatio
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Option.apply(LAPSI_ORGANISAATIO))))
    // ja käyttäjällä on vastaava käyttöoikeus oph-organisaatiolla
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + OPH_ORGANISAATIO_OID),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    // käyttäjä saa oikeudet lapsiorganisaatioon
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))), securityOperaatiot.getKayttajanOikeudet())
    // eikä tarvitse tutkia organisaatiohierarkiaa
    verify(mockOrganisaatioService, times(0)).getParentOids(any[String])

  @Test def testKayttooikeustunnisteetHaetaanKannastaJosEiLoydySessiosta(): Unit =
    reset(mockOrganisaatioService, mockKantaoperaatiot, mockHttpSession)
    val kayttajanKayttooikeustunnisteet = Set(12345)
    val uusinTunniste = 12346
    val kayttajanKayttooikeudet = Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    // jos käyttäjän käyttöoikeuksien tunnisteita ei ole ladattu sessioon
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)).thenReturn(null)
    // ja käyttäjällä on palvelun käyttöoikeusrajausta vastaava oikeus
    when(mockKantaoperaatiot.getKaikkikayttooikeudet()).thenReturn(Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO))))
    when(mockKantaoperaatiot.getKayttooikeusTunnisteet(kayttajanKayttooikeudet.toSeq)).thenReturn((kayttajanKayttooikeustunnisteet, uusinTunniste))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + LAPSI_ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    Assertions.assertEquals(Some(kayttajanKayttooikeustunnisteet), securityOperaatiot.getKayttajanKayttooikeustunnisteet())
    // käyttäjän käyttöoikeustunnisteet haetaan kannasta ja laitetaan sessioon
    verify(mockKantaoperaatiot, times(1)).getKayttooikeusTunnisteet(kayttajanKayttooikeudet.toSeq)
    verify(mockHttpSession, times(1)).setAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, kayttajanKayttooikeustunnisteet)
    // ja kannan uusin tunniste tallennetaan sessioon
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
    val kayttajanKayttooikeustunnisteet = Set(12345)
    val vanhentunutUusinTunniste = 12346
    val kayttooikeudetSessiossa = Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)))
    // jos läyttäjän käyttöoikeustunnisteet on sessiossa
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUDET)).thenReturn(kayttooikeudetSessiossa)
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)).thenReturn(kayttajanKayttooikeustunnisteet)
    // ja kannasta löytyy uudempi tunniste kuin sessioon tallennettu
    when(mockHttpSession.getAttribute(SecurityConstants.SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE)).thenReturn(vanhentunutUusinTunniste)
    when(mockKantaoperaatiot.getKayttooikeusTunnisteet(kayttooikeudetSessiossa.toSeq)).thenReturn((kayttajanKayttooikeustunnisteet, vanhentunutUusinTunniste+1))
    val securityOperaatiot = SecurityOperaatiot(
      () => Seq("ROLE_" + OIKEUS + "_" + ORGANISAATIO),
      () => "",
      mockOrganisaatioService,
      mockKantaoperaatiot,
      mockHttpSession)
    // haetaan kannasta käyttöoikeuksien tunnisteet uudelleen
    Assertions.assertEquals(Some(kayttajanKayttooikeustunnisteet), securityOperaatiot.getKayttajanKayttooikeustunnisteet())
    verify(mockKantaoperaatiot, times(1)).getKayttooikeusTunnisteet(kayttooikeudetSessiossa.toSeq)
    // ja päivitetään sessio
    verify(mockHttpSession, times(1)).setAttribute(SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, kayttajanKayttooikeustunnisteet)
    verify(mockHttpSession, times(1)).setAttribute(SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE, vanhentunutUusinTunniste+1)

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
