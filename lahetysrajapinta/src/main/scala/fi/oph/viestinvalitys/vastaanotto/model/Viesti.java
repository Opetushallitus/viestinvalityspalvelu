package fi.oph.viestinvalitys.vastaanotto.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface Viesti {

  Optional<String> getOtsikko();

  Optional<String> getSisalto();

  Optional<String> getSisallonTyyppi();

  Optional<List<String>> getKielet();

  @JsonDeserialize(as = MaskiImpl.class)
  @Schema(implementation = MaskiImpl.class)
  public interface Maski {

    Optional<String> getSalaisuus();

    Optional<String> getMaski();
  }

  Optional<List<Maski>> getMaskit();

  Optional<String> getLahettavanVirkailijanOid();

  @JsonDeserialize(as = LahettajaImpl.class)
  @Schema(implementation = LahettajaImpl.class)
  public interface Lahettaja {

    Optional<String> getNimi();

    Optional<String> getSahkopostiOsoite();
  }

  Optional<Lahettaja> getLahettaja();

  Optional<String> getReplyTo();

  @JsonDeserialize(as = VastaanottajaImpl.class)
  @Schema(implementation = VastaanottajaImpl.class)
  public interface Vastaanottaja {

    Optional<String> getNimi();

    Optional<String> getSahkopostiOsoite();
  }

  Optional<List<Vastaanottaja>> getVastaanottajat();

  Optional<List<String>> getLiitteidenTunnisteet();

  Optional<String> getPrioriteetti();

  Optional<Integer> getSailytysAika();

  Optional<Map<String, List<String>>> getMetadata();

  Optional<String> getLahetysTunniste();

  Optional<String> getLahettavaPalvelu();

  Optional<List<String>> getKayttooikeusRajoitukset();

  public static OtsikkoBuilder builder() {
    return new ViestiBuilderImpl();
  }

  interface OtsikkoBuilder {
    SisaltoBuilder withOtsikko(String otsikko);
  }

  interface SisaltoBuilder {
    KieletBuilder withTextSisalto(String sisalto);

    KieletBuilder withHtmlSisalto(String sisalto);
  }

  interface KieletBuilder {
    LahettajaBuilder withKielet(String ... kielet);
  }

  interface LahettajaBuilder {
    VastaanottajatBuilder withLahettaja(Optional<String> nimi, String sahkoposti);
  }

  interface VastaanottajatBuilder {
    interface VastaanottajaBuilder {
      void withVastaanottaja(Optional<String> nimi, String sahkoposti);
    }

    interface TakesVastaanottajaBuilder {

      void withVastaanottajaBuilder(VastaanottajaBuilder builder);
    }

    PrioriteettiBuilder withVastaanottajat(TakesVastaanottajaBuilder builder);
  }

  interface PrioriteettiBuilder {
    SailysaikaBuilder withNormaaliPrioriteetti();
    SailysaikaBuilder withKorkeaPrioriteetti();
  }

  interface SailysaikaBuilder {
    LahetysBuilder withSailytysAika(Integer sailytysAika);
  }

  interface LahetysBuilder {

    ViestiBuilder withLahetysTunniste(String lahetysTunniste);

    ViestiBuilderEiLahetysta withLahettavaPalvelu(String nimi);
  }

  interface ViestiBuilder {

    interface MaskiBuilder {
      void withMaski(String salaisuus, String maski);
    }

    interface TakesMaskiBuilder {
      void withMaskiBuilder(MaskiBuilder maskiBuilder);
    }

    ViestiBuilder withMaskit(TakesMaskiBuilder takesMaskiBuilder);

    ViestiBuilder withLahettavanVirkailijanOid(String oid);

    ViestiBuilder withReplyTo(String replyTo);

    ViestiBuilder withLiitteidenTunnisteet(List<UUID> liitteidenTunnisteet);

    interface MetadataBuilder {
      void withMetadata(String key, List<String> values);
    }

    interface TakesMetadataBuilder {
      void withMetadataBuilder(MetadataBuilder builder);
    }

    ViestiBuilder withMetadata(TakesMetadataBuilder builder);

    Viesti build() throws BuilderException;
  }

  interface ViestiBuilderEiLahetysta extends ViestiBuilder {

    ViestiBuilderEiLahetysta withKayttooikeusRajoitukset(String ... kayttooikeusRajoitukset);
  }
}