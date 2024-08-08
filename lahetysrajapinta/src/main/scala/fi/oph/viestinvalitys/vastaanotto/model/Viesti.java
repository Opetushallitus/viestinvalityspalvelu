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

  Optional<Lahetys.Lahettaja> getLahettaja();

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

  Optional<Integer> getSailytysaika();

  Optional<Map<String, List<String>>> getMetadata();

  Optional<String> getLahetysTunniste();

  Optional<String> getLahettavaPalvelu();

  @JsonDeserialize(as = KayttooikeusImpl.class)
  @Schema(implementation = KayttooikeusImpl.class)
  public interface Kayttooikeus {

    Optional<String> getOrganisaatio();

    Optional<String> getOikeus();
  }
  Optional<List<Kayttooikeus>> getKayttooikeusRajoitukset();

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
    VastaanottajatBuilder withKielet(String ... kielet);
  }

  interface VastaanottajatBuilder {
    ViestiBuilder withVastaanottajat(List<Vastaanottaja> vastaanottajat);
  }

  interface ViestiBuilder {

    ViestiBuilder withMaskit(List<Maski> maskit);

    ViestiBuilder withReplyTo(String replyTo);

    ViestiBuilder withLiitteidenTunnisteet(List<UUID> liitteidenTunnisteet);

    ViestiBuilder withMetadatat(Map<String, List<String>> metadatat);

    ViestiBuilder withKayttooikeusRajoitukset(Kayttooikeus ... kayttooikeusRajoitukset);

    ViestiBuilder withIdempotencyKey(String idempotencyKey);

    ExistingLahetysBuilder withLahetysTunniste(String lahetysTunniste);

    PrioriteettiBuilder withLahettavaPalvelu(String nimi);
  }

  interface ExistingLahetysBuilder {

    Viesti build() throws BuilderException;
  }

  interface PrioriteettiBuilder {
    LahettajaBuilder withNormaaliPrioriteetti();
    LahettajaBuilder withKorkeaPrioriteetti();
  }

  interface LahettajaBuilder {

    SailysaikaBuilder withLahettaja(Optional<String> nimi, String sahkoposti);
  }

  interface SailysaikaBuilder {
    InlineLahetysBuilder withSailytysAika(Integer sailytysAika);
  }

  interface InlineLahetysBuilder {

    LahettajaBuilder withLahettavanVirkailijanOid(String oid);

    Viesti build() throws BuilderException;
  }

}
