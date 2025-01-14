package fi.oph.viestinvalitys.vastaanotto.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lähetettävä viesti.
 *
 * Instansseja voi luoda {@link ViestinvalitysBuilder#viestiBuilder} -metodilla.
 */
public interface Viesti {

  static final int    OTSIKKO_MAX_PITUUS                    = 255;

  /**
   * Viestin sisällön maksimipituus, tätä rajoittaa SES-palvelun hyväksymä viestin maksimikoko, jonka alle halutaan
   * jäädä reilusti jotta mahdolliset liitteetkin mahtuvat mukaan.
   */
  static final int    SISALTO_MAX_PITUUS                    = 6291456; // =6*1024*1024

  /**
   * Viestin maksimikoko liitteineen.
   */
  static final String VIESTI_MAX_SIZE_MB_STR                = "8";
  static final int    VIESTI_MAX_SIZE                          = Integer.parseInt(VIESTI_MAX_SIZE_MB_STR) * 1024 * 1024;

  /**
   * Viestin lähettäjän ja yksittäisten vastaanottajien nimien maksimipituus.
   */
  static final int    VIESTI_NIMI_MAX_PITUUS                = 128;
  static final int    VIESTI_OSOITE_MAX_PITUUS              = 512;

  static final int    VIESTI_SALAISUUS_MIN_PITUUS           = 8;
  static final int    VIESTI_SALAISUUS_MAX_PITUUS           = 1024;
  static final int    VIESTI_MASKI_MIN_PITUUS               = 8;
  static final int    VIESTI_MASKI_MAX_PITUUS               = 1024;
  static final int    VIESTI_MASKIT_MAX_MAARA               = 32;

  static final int    VIESTI_METADATA_AVAIMET_MAX_MAARA     = 1024;
  static final String VIESTI_METADATA_SALLITUT_MERKIT       = "a-z, A-Z, 0-9 ja -_.";
  static final String VIESTI_METADATA_AVAIN_MAX_PITUUS_STR  = "64";
  static final String VIESTI_METADATA_ARVO_MAX_PITUUS_STR   = "64";
  static final String VIESTI_METADATA_ARVOT_MAX_MAARA_STR   = "1024";
  static final int    VIESTI_METADATA_AVAIN_MAX_PITUUS      = Integer.parseInt(VIESTI_METADATA_AVAIN_MAX_PITUUS_STR);
  static final int    VIESTI_METADATA_ARVO_MAX_PITUUS       = Integer.parseInt(VIESTI_METADATA_ARVO_MAX_PITUUS_STR);
  static final int    VIESTI_METADATA_ARVOT_MAX_MAARA       = Integer.parseInt(VIESTI_METADATA_ARVOT_MAX_MAARA_STR);

  /**
   * Yhden viestin maksimi vastaanottajamäärä. Jos haluat lähettää viestin tätä suuremmalle vastaanottajajoukolle, viesti
   * pitää palastella useammaksi viestiksi. <strong>HUOMAA</strong> että jos käytät palastelua yhdessä idempotency-avain
   * -toiminnalisuuden kanssa, idempotency-avain pitää generoida jokaiselle viestille erikseen.
   */
  static final int    VIESTI_VASTAANOTTAJAT_MAX_MAARA       = 512;
  static final int    VIESTI_LIITTEET_MAX_MAARA             = 128;

  static final int    VIESTI_ORGANISAATIO_MAX_PITUUS        = 64;
  static final int    VIESTI_OIKEUS_MAX_PITUUS              = 64;
  static final int    VIESTI_KAYTTOOIKEUS_MAX_MAARA         = 128;

  static final int    VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS     = 64;
  static final String VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS_STR = "64";
  static final String VIESTI_IDEMPOTENCY_KEY_SALLITUT_MERKIT= "a-z, A-Z, 0-9 ja -_.";

  static final String VIESTI_SISALTOTYYPPI_TEXT             = "text";
  static final String VIESTI_SISALTOTYYPPI_HTML             = "html";

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

    Optional<String> getOikeus();

    Optional<String> getOrganisaatio();

  }
  Optional<List<Kayttooikeus>> getKayttooikeusRajoitukset();

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

    ViestiBuilder withKayttooikeusRajoitukset(List<Kayttooikeus> kayttooikeusRajoitukset);

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
