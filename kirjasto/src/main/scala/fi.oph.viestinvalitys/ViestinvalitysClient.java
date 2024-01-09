package fi.oph.viestinvalitys;

import fi.oph.viestinvalitys.vastaanotto.model.*;
import fi.vm.sade.javautils.nio.cas.impl.CasSessionFetcher;

import java.util.UUID;

public interface ViestinvalitysClient {

  public LuoLahetysSuccessResponse luoLahetys(Lahetys lahetys) throws ViestinvalitysClientException;

  public LuoLiiteSuccessResponse luoLiite(Liite liite) throws ViestinvalitysClientException;

  public LuoViestiSuccessResponse luoViesti(Viesti viesti) throws ViestinvalitysClientException;

  public static EndpointBuilder builder() {
    return new ViestinvalitysClientBuilderImpl();
  }

  interface EndpointBuilder {

    AuthenticationBuilder withEndpoint(String endpoint);
  }

  interface AuthenticationBuilder {
    PasswordBuilder withUsername(String username);

    CallerIdBuilder withSessionId(String sessionId);
  }

  interface PasswordBuilder {
    CasEndpointBuilder withPassword(String password);
  }

  interface CasEndpointBuilder {

    CallerIdBuilder withCasEndpoint(String casEndpoint);
  }

  interface CallerIdBuilder {
    ViestinValitysClientBuilder withCallerId(String callerId);
  }

  interface ViestinValitysClientBuilder {

    ViestinvalitysClient build();
  }
}
