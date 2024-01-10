package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.oph.viestinvalitys.vastaanotto.model.MetadatatBuilderImpl;

public interface Metadatat {

  interface MetadatatBuilder {

    MetadatatBuilder withMetadata(String avain, List<String> arvot);

    Map<String, List<String>> build();
  }

  static MetadatatBuilder builder() {
    return new MetadatatBuilderImpl();
  }
}
