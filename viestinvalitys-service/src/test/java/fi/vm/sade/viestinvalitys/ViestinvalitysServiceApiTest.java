package fi.vm.sade.viestinvalitys;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class ViestinvalitysServiceApiTest {

  protected static final String OPH_ORGANISAATIO_OID = "1.2.246.562.10.00000000001";

  protected static final String TEST_KAYTTAJA_OID = "1.2.246.562.24.00000000001";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected JdbcTemplate jdbcTemplate;

  protected void clearDatabase() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE "
            + String.join(
                ", ",
                "metadata",
                "metadata_avaimet",
                "vastaanottaja_siirtymat",
                "vastaanottajat",
                "viestit_liitteet",
                "viestit_kayttooikeudet",
                "maskit",
                "lahetykset_kayttooikeudet",
                "viestit",
                "lahetykset",
                "kayttooikeudet",
                "liitteet")
            + " RESTART IDENTITY CASCADE");
  }

  protected <T> T getJson(Class<T> responseClass, String urlTemplate, Object... uriVars) throws Exception {
    var response =
        mvc.perform(get(urlTemplate, uriVars)).andExpect(status().isOk()).andReturn().getResponse();
    return objectMapper.readValue(response.getContentAsString(), responseClass);
  }

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @WithMockUser(
      username = TEST_KAYTTAJA_OID,
      authorities = {"APP_VIESTINVALITYS_KATSELU_" + OPH_ORGANISAATIO_OID})
  public @interface UserKatselijaRaportoija {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @WithMockUser(
      username = TEST_KAYTTAJA_OID,
      authorities = {"APP_VIESTINVALITYS_LAHETYS_" + OPH_ORGANISAATIO_OID})
  public @interface UserLahettaja {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @WithMockUser(
      username = TEST_KAYTTAJA_OID,
      authorities = {
        "APP_VIESTINVALITYS_OPH_PAAKAYTTAJA",
        "APP_VIESTINVALITYS_OPH_PAAKAYTTAJA_" + OPH_ORGANISAATIO_OID
      })
  public @interface UserPaakayttaja {}
}
