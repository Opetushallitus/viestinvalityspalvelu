package fi.vm.sade.viestinvalitys.resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import org.junit.jupiter.api.Test;

class HealthControllerTest extends ViestinvalitysServiceApiTest {

  @Test
  void actuatorHealthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void healthcheckRequiresAuthentication() throws Exception {
    mvc.perform(get("/raportointi/v1/healthcheck")).andExpect(status().isUnauthorized());
  }

  @Test
  @UserKatselijaRaportoija
  void healthcheckReturnsOkForAuthenticatedUser() throws Exception {
    mvc.perform(get("/raportointi/v1/healthcheck"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }
}
