package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys;
import fi.oph.viestinvalitys.vastaanotto.model.Viesti;
import fi.vm.sade.javautils.nio.cas.impl.CasSessionFetcher;

import java.util.UUID;

public interface ViestinvalitysClient {

  public UUID luoLahetys(Lahetys lahetys) throws Exception;

  //public UUID luoLiite()

  public UUID luoViesti(Viesti viesti) throws Exception;

  public static AuthenticationBuilder builder() {
    return new ViestinvalitysClientBuilderImpl();
  }

  interface AuthenticationBuilder {
    PasswordBuilder withUsername(String username);

    CallerIdBuilder withSessionId(String sessionId);
  }

  interface PasswordBuilder {
    CallerIdBuilder withPassword(String password);
  }

  interface CallerIdBuilder {
    ViestinValitysClientBuilder withCallerId(String callerId);
  }

  interface ViestinValitysClientBuilder {

    ViestinValitysClientBuilder withEndpoint(String endpoint);
    ViestinvalitysClient build();
  }
}
