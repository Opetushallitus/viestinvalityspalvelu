package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.Optional;
import java.util.List;

public interface Lahetys {

  static final int OTSIKKO_MAX_PITUUS = 255;

  Optional<String> getOtsikko();

  Optional<List<String>> getKayttooikeusRajoitukset();

  static OtsikkoBuilder builder() {
    return new LahetysBuilderImpl();
  }

  interface OtsikkoBuilder {
    ILahetysBuilder withOtsikko(String otsikko);
  }

  interface ILahetysBuilder {
    ILahetysBuilder withKayttooikeusRajoitukset(String ... kayttooikeusRajoitukset);
    Lahetys build() throws BuilderException;
  }
}
