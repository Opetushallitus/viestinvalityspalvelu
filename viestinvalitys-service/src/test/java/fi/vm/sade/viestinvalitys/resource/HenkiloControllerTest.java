package fi.vm.sade.viestinvalitys.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
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
  static void overrideVirkailijaUrl(DynamicPropertyRegistry registry) {
    registry.add("host.virkailija", virkailija::baseUrl);
  }

  private static final String ASIOINTIKIELI_PATH =
      "/oppijanumerorekisteri-service/henkilo/" + TEST_KAYTTAJA_OID + "/asiointiKieli";

  @Test
  @UserKatselijaRaportoija
  void returnsAsiointikieliFromOppijanumerorekisteri() throws Exception {
    virkailija.stubFor(
        get(urlPathEqualTo(ASIOINTIKIELI_PATH)).willReturn(okJson("{\"kieliKoodi\": \"sv\"}")));

    mvc.perform(MockMvcRequestBuilders.get("/raportointi/v1/asiointikieli"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("sv")));
  }

  @Test
  @UserKatselijaRaportoija
  void fallsBackToFinnishWhenOppijanumerorekisteriFails() throws Exception {
    virkailija.stubFor(get(urlPathEqualTo(ASIOINTIKIELI_PATH)).willReturn(serverError()));

    mvc.perform(MockMvcRequestBuilders.get("/raportointi/v1/asiointikieli"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("fi")));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/raportointi/v1/asiointikieli"))
        .andExpect(status().isUnauthorized());
  }
}
