package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.*;
import fi.oph.viestinvalitys.vastaanotto.resource.VastaanottajaResponseImpl;
import io.netty.handler.codec.http.cookie.Cookie;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Optional;
import java.util.List;
import java.util.Iterator;

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
        .withVastaanottajat(Vastaanottajat.builder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja@example.com")
            .build())
        .withNormaaliPrioriteetti()
        .withSailytysAika(10)
        .withLahettavaPalvelu("palvelu")
        .withMetadatat(Metadatat.builder()
            .withMetadata("avain", List.of("arvo1", "arvo2"))
            .build())
        .withMaskit(Maskit.builder()
            .withMaski("salaisuus", "maskattu")
            .build())
        .build());
  }

  @Test
  public void testLiitaLiite() throws Exception {
    ViestinvalitysClient client = this.getClient();

    LuoLiiteSuccessResponse liiteResponse = client.luoLiite(Liite.builder()
        .withFileName("test")
        .withBytes(new byte[] {0})
        .withContentType("image/png")
        .build());

    LuoViestiSuccessResponse viestiResponse = client.luoViesti(Viesti.builder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withVastaanottajat(Vastaanottajat.builder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja@example.com")
            .build())
        .withNormaaliPrioriteetti()
        .withSailytysAika(10)
        .withLahettavaPalvelu("palvelu")
        .withLiitteidenTunnisteet(List.of(liiteResponse.getLiiteTunniste()))
        .build());
  }

  @Test
  public void testGetVastaanottajat() throws Exception {
    ViestinvalitysClient client = this.getClient();

    LuoViestiSuccessResponse viestiResponse = client.luoViesti(Viesti.builder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withVastaanottajat(Vastaanottajat.builder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja@example.com")
            .withVastaanottaja(Optional.empty(), "veera.vastaanottaja@example.com")
            .build())
        .withNormaaliPrioriteetti()
        .withSailytysAika(10)
        .withLahettavaPalvelu("palvelu")
        .build());

    Iterator<List<VastaanottajaResponse>> vastaanottajat = client.getVastaanottajat(viestiResponse.getLahetysTunniste(), Optional.of(1));

    List<VastaanottajaResponse> vastaanottajat1 = vastaanottajat.next();
    Assertions.assertEquals(1, vastaanottajat1.size());
    Assertions.assertEquals("vallu.vastaanottaja@example.com", vastaanottajat1.get(0).getSahkoposti());

    List<VastaanottajaResponse> vastaanottajat2 = vastaanottajat.next();
    Assertions.assertEquals(1, vastaanottajat2.size());
    Assertions.assertEquals("veera.vastaanottaja@example.com", vastaanottajat2.get(0).getSahkoposti());

    Assertions.assertFalse(vastaanottajat.hasNext());

  }

}
