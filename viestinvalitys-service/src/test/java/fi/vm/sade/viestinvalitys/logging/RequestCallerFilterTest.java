package fi.vm.sade.viestinvalitys.logging;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Cookie;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class RequestCallerFilterTest extends ViestinvalitysServiceApiTest {

  @LocalServerPort
  private int port;

  private String baseUrl;
  private String casCallback;
  private static final String CALLER_HENKILO_OID = "1.2.246.562.24.99999999999";

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

  @Test
  public void logsCallerHenkiloOidWhenCallerAuthenticatedWithCasVirkailija(CapturedOutput output)
      throws Exception {
    var cookie = getCookie("/viestinvalityspalvelu");
    var ticket = "ST-30-JVB-gESc2Yc3S-zV25JOHbVEeBo-ip-10-0-55-20";
    var encodedService = URLEncoder.encode(casCallback, StandardCharsets.UTF_8);

    // CAS login: redirect back to the service's CAS callback with a ticket.
    wireMock.stubFor(
        get(urlEqualTo("/cas/login?service=" + encodedService))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", casCallback + "?ticket=" + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    wireMock.stubFor(
        post(urlEqualTo("/cas/login?service=" + encodedService))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", casCallback + "?ticket=" + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));

    // Ticket validation: return a virkailija assertion with oidHenkilo + roles.
    wireMock.stubFor(
        get(urlEqualTo(
                "/cas/p3/proxyValidate?ticket=" + ticket + "&service=" + encodedService))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(readResource("/cas-virkailija-auth-response.xml"))));

    var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    var loginRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(wireMock.url("/cas/login?service=" + encodedService)))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    var loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
    var cookies = loginResponse.headers().allValues("Set-Cookie").get(0);

    var apiRequest =
        HttpRequest.newBuilder()
            .header("Cookie", cookies)
            .uri(URI.create(baseUrl + "/viestinvalityspalvelu/v1/asiointikieli"))
            .GET()
            .build();
    client.send(apiRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(output).contains("\"callerHenkiloOid\": \"" + CALLER_HENKILO_OID + "\"");
  }

  private Cookie getCookie(String path) {
    var tgc = "TGC=asd";
    return new Cookie(
        path, tgc, "SameSite=none", "SameSite=None", "Secure", "HttpOnly", "Path=" + path);
  }

  private byte[] readBytes(String path) {
    try (var inputStream = getClass().getResourceAsStream(path)) {
      if (inputStream == null) {
        throw new RuntimeException("Resource not found: " + path);
      }
      return inputStream.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }

  private String readResource(String path) {
    return new String(readBytes(path));
  }
}
