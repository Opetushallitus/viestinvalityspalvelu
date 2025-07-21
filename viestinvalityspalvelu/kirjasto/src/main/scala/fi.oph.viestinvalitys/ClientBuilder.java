package fi.oph.viestinvalitys;

public class ClientBuilder {
    public static ViestinvalitysClient.EndpointBuilder viestinvalitysClientBuilder() {
        return new ViestinvalitysClientBuilderImpl();
    }
}
