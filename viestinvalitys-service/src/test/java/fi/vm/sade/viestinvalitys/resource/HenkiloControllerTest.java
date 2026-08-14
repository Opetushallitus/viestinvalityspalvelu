package fi.vm.sade.viestinvalitys.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class HenkiloControllerTest extends ViestinvalitysServiceApiTest {

  @RegisterExtension
  static WireMockExtension virkailija =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("host.virkailija", virkailija::baseUrl);
    registry.add("viestinvalitys.jarjestelmatunnus.cas-username", () -> "viestinvalityspalvelu");
    registry.add("viestinvalitys.jarjestelmatunnus.cas-password", () -> "cas-kayttajan-salasana");
  }

  private static final String ASIOINTIKIELI_PATH =
      "/oppijanumerorekisteri-service/henkilo/" + TEST_KAYTTAJA_OID + "/asiointiKieli";
  private static final String ONR_CAS_CALLBACK =
      "/oppijanumerorekisteri-service/j_spring_cas_security_check";

  @BeforeEach
  void resetStubs() {
    virkailija.resetAll();
  }

  private void stubCasServiceTicketFlow() {
    virkailija.stubFor(
        post(urlPathEqualTo("/cas/v1/tickets"))
            .withRequestBody(containing("username=viestinvalityspalvelu"))
            .withRequestBody(containing("password=cas-kayttajan-salasana"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Location", virkailija.baseUrl() + "/cas/v1/tickets/TGT-123")));
    virkailija.stubFor(
        post(urlPathEqualTo("/cas/v1/tickets/TGT-123"))
            .withRequestBody(containing("service="))
            .willReturn(aResponse().withStatus(200).withBody("ST-456")));
    virkailija.stubFor(
        get(urlPathEqualTo(ONR_CAS_CALLBACK))
            .withQueryParam("ticket", equalTo("ST-456"))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", "/oppijanumerorekisteri-service/")
                    .withHeader("Set-Cookie", "JSESSIONID=onr-sessio; Path=/; HttpOnly")));
  }

  private void stubOnrRequiresAuthentication() {
    // Like the real oppijanumerorekisteri: an anonymous request is redirected to CAS login.
    virkailija.stubFor(
        get(urlPathEqualTo(ASIOINTIKIELI_PATH))
            .withCookie("JSESSIONID", absent())
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", virkailija.baseUrl() + "/cas/login")));
    virkailija.stubFor(
        get(urlPathEqualTo("/cas/login"))
            .willReturn(aResponse().withStatus(200).withBody("<html>CAS login</html>")));
  }

  @Test
  @UserKatselijaRaportoija
  void returnsAsiointikieliFromOppijanumerorekisteriWithCasAuthentication() throws Exception {
    stubOnrRequiresAuthentication();
    stubCasServiceTicketFlow();
    virkailija.stubFor(
        get(urlPathEqualTo(ASIOINTIKIELI_PATH))
            .withCookie("JSESSIONID", equalTo("onr-sessio"))
            .willReturn(okJson("{\"kieliKoodi\": \"sv\"}")));

    mvc.perform(MockMvcRequestBuilders.get("/v1/asiointikieli"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sv")));
  }

  @Test
  @UserKatselijaRaportoija
  void fallsBackToFinnishWhenCasAuthenticationFails() throws Exception {
    stubOnrRequiresAuthentication();
    virkailija.stubFor(post(urlPathEqualTo("/cas/v1/tickets")).willReturn(serverError()));

    mvc.perform(MockMvcRequestBuilders.get("/v1/asiointikieli"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("fi")));
  }

  @Test
  @UserKatselijaRaportoija
  void fallsBackToFinnishWhenOppijanumerorekisteriFails() throws Exception {
    stubCasServiceTicketFlow();
    virkailija.stubFor(
        get(urlPathEqualTo(ASIOINTIKIELI_PATH))
            .withCookie("JSESSIONID", equalTo("onr-sessio"))
            .willReturn(serverError()));

    mvc.perform(MockMvcRequestBuilders.get("/v1/asiointikieli"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("fi")));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/v1/asiointikieli"))
        .andExpect(status().is3xxRedirection());
  }
}
