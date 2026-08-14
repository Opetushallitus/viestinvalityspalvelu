package fi.vm.sade.viestinvalitys.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Releases the fixed server port after the class so other DEFINED_PORT tests can bind it.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class CasLoginRedirectTest extends ViestinvalitysServiceApiTest {

  @LocalServerPort private int port;

  private String baseUrl;
  private String casCallback;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("cas.base", () -> wireMock.baseUrl() + "/cas");
  }

  @BeforeEach
  public void setup() {
    baseUrl = "http://localhost:" + port;
    casCallback = baseUrl + "/viestinvalityspalvelu/login/j_spring_cas_security_check";
    clearDatabase();
    wireMock.resetAll();
  }

  private HttpClient client() {
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  }

  private String loginWithTicketAfterVisiting(String initialPath) throws Exception {
    var client = client();

    var initialResponse =
        client.send(
            HttpRequest.newBuilder().uri(URI.create(baseUrl + initialPath)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(initialResponse.statusCode()).isEqualTo(302);
    assertThat(initialResponse.headers().firstValue("Location").orElseThrow())
        .startsWith(wireMock.baseUrl() + "/cas/login");
    String sessionCookie =
        initialResponse.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID="))
            .map(c -> c.split(";")[0])
            .findFirst()
            .orElse(null);

    var ticket = "ST-" + java.util.UUID.randomUUID();
    var encodedService = URLEncoder.encode(casCallback, StandardCharsets.UTF_8);
    wireMock.stubFor(
        get(urlEqualTo("/cas/p3/proxyValidate?ticket=" + ticket + "&service=" + encodedService))
            .willReturn(
                aResponse().withStatus(200).withBody(readResource("/cas-virkailija-auth-response.xml"))));

    var callbackRequest =
        HttpRequest.newBuilder().uri(URI.create(casCallback + "?ticket=" + ticket)).GET();
    if (sessionCookie != null) {
      callbackRequest.header("Cookie", sessionCookie);
    }
    var callbackResponse =
        client.send(callbackRequest.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(callbackResponse.statusCode()).isEqualTo(302);
    return callbackResponse.headers().firstValue("Location").orElseThrow();
  }

  @Test
  public void redirectsToOriginallyRequestedPageAfterCasLogin() throws Exception {
    String deepLink = "/viestinvalityspalvelu/lahetys/0198aaaa-0000-7000-8000-000000000000";

    String location = loginWithTicketAfterVisiting(deepLink);

    assertThat(location).endsWith(deepLink);
  }

  @Test
  public void redirectsToFrontPageWhenLoginStartedFromLoginPath() throws Exception {
    String location = loginWithTicketAfterVisiting("/viestinvalityspalvelu/login");

    assertThat(location).endsWith("/viestinvalityspalvelu/");
  }

  @Test
  public void redirectsToFrontPageWhenOriginalRequestWasAnApiCall() throws Exception {
    String location = loginWithTicketAfterVisiting("/viestinvalityspalvelu/v1/lahetykset/lista");

    assertThat(location).endsWith("/viestinvalityspalvelu/");
  }

  private String readResource(String path) {
    try (var inputStream = getClass().getResourceAsStream(path)) {
      if (inputStream == null) {
        throw new RuntimeException("Resource not found: " + path);
      }
      return new String(inputStream.readAllBytes());
    } catch (Exception e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }
}
