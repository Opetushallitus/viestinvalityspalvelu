package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.Optional;
import java.util.List;

public interface Vastaanottajat {

  interface VastaanottajatBuilder {

    VastaanottajatBuilder withVastaanottaja(Optional<String> nimi, String sahkopostiOsoite);

    List<Viesti.Vastaanottaja> build();
  }

  static VastaanottajatBuilder builder() {
    return new VastaanottajatBuilderImpl();
  }
}
