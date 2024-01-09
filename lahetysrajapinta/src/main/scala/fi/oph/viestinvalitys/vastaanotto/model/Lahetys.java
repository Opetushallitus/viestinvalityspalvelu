package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.List;
import java.util.Optional;

public interface Lahetys {

  static final int OTSIKKO_MAX_PITUUS           = 255;
  static final int LAHETTAVAPALVELU_MAX_PITUUS  = 127;

  Optional<String> getOtsikko();

  Optional<String> getLahettavaPalvelu();

  Optional<List<String>> getKayttooikeusRajoitukset();

  static OtsikkoBuilder builder() {
    return new LahetysBuilderImpl();
  }

  interface OtsikkoBuilder {
    LahettavaPalveluBuilder withOtsikko(String otsikko);
  }

  interface LahettavaPalveluBuilder {
    LahetysBuilder withLahettavaPalvelu(String nimi);
  }

  interface LahetysBuilder {
    LahetysBuilder withKayttooikeusRajoitukset(String ... kayttooikeusRajoitukset);
    Lahetys build() throws BuilderException;
  }
}
