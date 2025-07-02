package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.*;
import io.netty.handler.codec.http.cookie.Cookie;
import org.asynchttpclient.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Optional;
import java.util.List;
import java.util.Iterator;

/**
 * Testataan kirjaston toiminnallisuus
 */
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
        .addFormParam("username", "1.2.3.4.0")
        .addFormParam("password", "password")
        .build()).get();
    Cookie sessionCookie = response.getCookies().get(0);
    return sessionCookie.value();
  }

  private ViestinvalitysClient getClient() throws Exception {
    String port = environment.getProperty("local.server.port");
    return ClientBuilder.viestinvalitysClientBuilder()
        .withEndpoint("http://localhost:" + port)
        .withSessionId(this.getSessionCookie())
        .withCallerId(CALLER_ID)
        .build();
  }
  
  private ViestinvalitysClient getClientWithHttpClient(AsyncHttpClient httpClient) throws Exception {
    String port = environment.getProperty("local.server.port");
    return ClientBuilder.viestinvalitysClientBuilder()
        .withEndpoint("http://localhost:" + port)
        .withSessionId(this.getSessionCookie())
        .withCallerId(CALLER_ID)
        .buildWithHttpClient(httpClient);
  }

  @Test
  public void testLuoLahetysCustomHttpClientilla () throws Exception {
    DefaultAsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
            .setMaxConnections(100)
            .setMaxConnectionsPerHost(20)
            .build();

    DefaultAsyncHttpClient httpClient = new DefaultAsyncHttpClient(config);
    ViestinvalitysClient client = this.getClientWithHttpClient(httpClient);

    LuoLahetysSuccessResponse response = client.luoLahetys(
         ViestinvalitysBuilder.lahetysBuilder()
        .withOtsikko("otsikko")
        .withLahettavaPalvelu("palvelu")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withNormaaliPrioriteetti()
        .withSailytysaika(1)
        .build());
  }
  
  @Test
  public void testLuoLahetys() throws Exception {
    LuoLahetysSuccessResponse response = this.getClient().luoLahetys(
         ViestinvalitysBuilder.lahetysBuilder()
        .withOtsikko("otsikko")
        .withLahettavaPalvelu("palvelu")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withNormaaliPrioriteetti()
        .withSailytysaika(1)
        .build());
  }

  @Test
  public void testLuoLiite() throws Exception {
    LuoLiiteSuccessResponse response = this.getClient().luoLiite(ViestinvalitysBuilder.liiteBuilder()
        .withFileName("test.png")
        .withBytes(getClass().getResourceAsStream("/screenshot.png").readAllBytes())
        .withContentType("image/png")
        .build());
  }

  @Test
  public void testLuoViesti() throws Exception {
    LuoViestiSuccessResponse response = this.getClient().luoViesti(ViestinvalitysBuilder.viestiBuilder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja+success@example.com")
            .build())
        .withMetadatat(ViestinvalitysBuilder.metadatatBuilder()
            .withMetadata("avain", List.of("arvo1", "arvo2"))
            .build())
        .withMaskit(ViestinvalitysBuilder.maskitBuilder()
            .withMaski("salaisuus", "maskattu")
            .build())
        .withKayttooikeusRajoitukset(ViestinvalitysBuilder.kayttooikeusrajoituksetBuilder()
             .withKayttooikeus("APP_HAKEMUS_CRUD", "1.2.246.562.10.240484683010")
             .build())
        .withLahettavaPalvelu("palvelu")
        .withNormaaliPrioriteetti()
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withSailytysAika(10)
        .build());
  }

  @Test
  public void testLiitaLiite() throws Exception {
    ViestinvalitysClient client = this.getClient();

    LuoLiiteSuccessResponse liiteResponse = client.luoLiite(ViestinvalitysBuilder.liiteBuilder()
        .withFileName("test.png")
        .withBytes(getClass().getResourceAsStream("/screenshot.png").readAllBytes())
        .withContentType("image/png")
        .build());

    LuoViestiSuccessResponse viestiResponse = client.luoViesti(ViestinvalitysBuilder.viestiBuilder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja+success@example.com")
            .build())
        .withLiitteidenTunnisteet(List.of(liiteResponse.getLiiteTunniste()))
        .withLahettavaPalvelu("palvelu")
        .withNormaaliPrioriteetti()
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withSailytysAika(10)
        .build());
  }

  @Test
  public void testGetVastaanottajat() throws Exception {
    ViestinvalitysClient client = this.getClient();

    LuoViestiSuccessResponse viestiResponse = client.luoViesti(ViestinvalitysBuilder.viestiBuilder()
        .withOtsikko("otsikko")
        .withTextSisalto("sisältö")
        .withKielet("fi")
        .withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder()
            .withVastaanottaja(Optional.empty(), "vallu.vastaanottaja+success@example.com")
            .withVastaanottaja(Optional.empty(), "veera.vastaanottaja+success@example.com")
            .build())
        .withLahettavaPalvelu("palvelu")
        .withNormaaliPrioriteetti()
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withSailytysAika(10)
        .build());

    Iterator<List<VastaanottajaResponse>> vastaanottajat = client.getVastaanottajat(viestiResponse.getLahetysTunniste(), Optional.of(1));

    List<VastaanottajaResponse> vastaanottajat1 = vastaanottajat.next();
    Assertions.assertEquals(1, vastaanottajat1.size());
    Assertions.assertEquals("vallu.vastaanottaja+success@example.com", vastaanottajat1.get(0).getSahkoposti());

    List<VastaanottajaResponse> vastaanottajat2 = vastaanottajat.next();
    Assertions.assertEquals(1, vastaanottajat2.size());
    Assertions.assertEquals("veera.vastaanottaja+success@example.com", vastaanottajat2.get(0).getSahkoposti());

    Assertions.assertFalse(vastaanottajat.hasNext());
  }

}
