package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.List;

import fi.oph.viestinvalitys.vastaanotto.model.Viesti.Maski;

public interface Maskit {

  interface MaskitBuilder {

    MaskitBuilder withMaski(String salaisuus, String maski);

    List<Maski> build();
  }

  static MaskitBuilder builder() {
    return new MaskitBuilderImpl();
  }
}
