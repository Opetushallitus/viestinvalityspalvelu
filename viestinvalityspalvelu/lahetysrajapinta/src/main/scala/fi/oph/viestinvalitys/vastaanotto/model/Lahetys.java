package fi.oph.viestinvalitys.vastaanotto.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Optional;

public interface Lahetys {
  
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

  Optional<Integer> getSailytysaika();


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

    SailytysaikaBuilder withNormaaliPrioriteetti();

    SailytysaikaBuilder withKorkeaPrioriteetti();
  }

  interface SailytysaikaBuilder {

    LahetysBuilder withSailytysaika(int sailytysaika);
  }

  interface LahetysBuilder {

    LahetysBuilder withReplyTo(String replyTo);
    LahetysBuilder withLahettavanVirkailijanOid(String oid);
    Lahetys build() throws BuilderException;
  }
}
