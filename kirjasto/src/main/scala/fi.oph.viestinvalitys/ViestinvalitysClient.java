package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.*;
import fi.vm.sade.javautils.nio.cas.impl.CasSessionFetcher;

import java.util.UUID;

public interface ViestinvalitysClient {

  public LuoLahetysSuccessResponse luoLahetys(Lahetys lahetys) throws ViestinvalitysClientException;

  public LuoLiiteSuccessResponse luoLiite(Liite liite) throws ViestinvalitysClientException;

  public LuoViestiSuccessResponse luoViesti(Viesti viesti) throws ViestinvalitysClientException;

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
