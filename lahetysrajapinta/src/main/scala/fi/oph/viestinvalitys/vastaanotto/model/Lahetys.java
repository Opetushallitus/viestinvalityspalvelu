package fi.oph.viestinvalitys.vastaanotto.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Optional;

public interface Lahetys {

  static final int OTSIKKO_MAX_PITUUS           = 255;
  static final int LAHETTAVAPALVELU_MAX_PITUUS  = 127;
  static final int LAHETTAJA_NIMI_MAX_PITUUS    = 64;
  static final int VIRKAILIJAN_OID_MAX_PITUUS   = 64;

  Optional<String> getOtsikko();

  Optional<String> getLahettavaPalvelu();

  Optional<String> getLahettavanVirkailijanOid();

  @JsonDeserialize(as = LahettajaImpl.class)
  @Schema(implementation = LahettajaImpl.class)
  public interface Lahettaja {

    Optional<String> getNimi();

    Optional<String> getSahkopostiOsoite();
  }

  Optional<Lahettaja> getLahettaja();

  Optional<String> getReplyTo();

  Optional<String> getPrioriteetti();

  Optional<List<String>> getKayttooikeusRajoitukset();

  static OtsikkoBuilder builder() {
    return new LahetysBuilderImpl();
  }

  interface OtsikkoBuilder {
    LahettavaPalveluBuilder withOtsikko(String otsikko);
  }

  interface LahettavaPalveluBuilder {
    LahettajaBuilder withLahettavaPalvelu(String nimi);
  }

  interface LahettajaBuilder {

    PrioriteettiBuilder withLahettaja(Optional<String> nimi, String sahkopostiOsoite);
  }

  interface PrioriteettiBuilder {

    LahetysBuilder withNormaaliPrioriteetti();

    LahetysBuilder withKorkeaPrioriteetti();
  }

  interface LahetysBuilder {

    LahetysBuilder withReplyTo(String replyTo);
    LahetysBuilder withLahettavanVirkailijanOid(String oid);
    LahetysBuilder withKayttooikeusRajoitukset(String ... kayttooikeusRajoitukset);
    Lahetys build() throws BuilderException;
  }
}
