package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.Optional;
import java.util.List;
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.Kayttooikeus;

public interface Kayttooikeusrajoitukset {
    interface KayttooikeusrajoituksetBuilder {

        KayttooikeusrajoituksetBuilder withKayttooikeus(String oikeus, String organisaatio);

        List<Kayttooikeus> build();
    }

    static KayttooikeusrajoituksetBuilder builder() {
        return new KayttooikeusrajoituksetBuilderImpl();
    }
}

