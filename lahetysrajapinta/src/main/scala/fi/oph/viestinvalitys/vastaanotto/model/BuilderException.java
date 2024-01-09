package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.Set;

class BuilderException extends Exception {

  private Set<String> virheet;

  BuilderException(Set<String> virheet) {
    this.virheet = virheet;
  }

  public Set<String> getVirheet() {
    return this.virheet;
  }
}

