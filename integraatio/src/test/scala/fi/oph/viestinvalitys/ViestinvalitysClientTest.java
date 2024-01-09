package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.*;
import io.netty.handler.codec.http.cookie.Cookie;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

class ViestinvalitysClientTest extends BaseIntegraatioTesti {

  static final String CALLER_ID = "1.2.246.562.10.00000000001.viestinvalityspalvelu";

  @Autowired
  Environment environment;

  private String getSessionCookie() throws Exception {
    String port = environment.getProperty("local.server.port");
    AsyncHttpClient asyncHttpClient = Dsl.asyncHttpClient();
    Response response = asyncHttpClient.executeRequest(new RequestBuilder()
        .setMethod("POST")
        .setUrl("http://localhost:" + port + "/login")
        .addFormParam("username", "user")
        .addFormParam("password", "password")
        .build()).get();
    Cookie sessionCookie = response.getCookies().get(0);
    return sessionCookie.value();
  }

  private ViestinvalitysClient getClient() throws Exception {
    String port = environment.getProperty("local.server.port");
    return ViestinvalitysClient.builder()
        .withEndpoint("http://localhost:" + port)
        .withSessionId(this.getSessionCookie())
        .withCallerId(CALLER_ID)
        .build();
  }

  @Test
  public void testLuoLahetys() throws Exception {
    LuoLahetysSuccessResponse response = this.getClient().luoLahetys(Lahetys.builder()
        .withOtsikko("otsikko")
        .withLahettavaPalvelu("palvelu")
        .build());
  }

  @Test
  public void testLuoLiite() throws Exception {
    LuoLiiteSuccessResponse response = this.getClient().luoLiite(Liite.builder()
        .withFileName("test")
        .withBytes(new byte[] {0})
        .withContentType("image/png")
        .build());
  }

  @Test
  public void testLuoViesti() throws Exception {
    LuoViestiSuccessResponse response = this.getClient().luoViesti(Viesti.builder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withVastaanottajat(b -> b.withVastaanottaja(Optional.empty(), "vallu.vastaanottaja@example.com"))
        .withNormaaliPrioriteetti()
        .withSailytysAika(10)
        .withLahettavaPalvelu("palvelu")
        .build());
  }

  @Test
  public void testLiitaLiite() throws Exception {
    LuoLiiteSuccessResponse liiteResponse = this.getClient().luoLiite(Liite.builder()
        .withFileName("test")
        .withBytes(new byte[] {0})
        .withContentType("image/png")
        .build());

    LuoViestiSuccessResponse viestiResponse = this.getClient().luoViesti(Viesti.builder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withVastaanottajat(b -> b.withVastaanottaja(Optional.empty(), "vallu.vastaanottaja@example.com"))
        .withNormaaliPrioriteetti()
        .withSailytysAika(10)
        .withLahettavaPalvelu("palvelu")
        .withLiitteidenTunnisteet(List.of(liiteResponse.getLiiteTunniste()))
        .build());
  }

}
