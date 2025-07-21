package fi.oph.viestinvalitys.vastaanotto.model;

public class ViestinvalitysBuilder {
    public static Maskit.MaskitBuilder maskitBuilder() {
        return new MaskitBuilderImpl();
    }
    public static Metadatat.MetadatatBuilder metadatatBuilder() {
        return new MetadatatBuilderImpl();
    }

    public static Vastaanottajat.VastaanottajatBuilder vastaanottajatBuilder() {
        return new VastaanottajatBuilderImpl();
    }

    public static Kayttooikeusrajoitukset.KayttooikeusrajoituksetBuilder kayttooikeusrajoituksetBuilder() {
        return new KayttooikeusrajoituksetBuilderImpl();
    }

    public static Viesti.OtsikkoBuilder viestiBuilder() {
        return new ViestiBuilderImpl();
    }

    public static Lahetys.OtsikkoBuilder lahetysBuilder() {
        return new LahetysBuilderImpl();
    }

    public static Liite.TiedostoNimiBuilder liiteBuilder() {
        return new LiiteBuilderImpl();
    }


}
