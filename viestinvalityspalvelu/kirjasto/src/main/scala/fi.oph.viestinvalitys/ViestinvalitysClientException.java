package fi.oph.viestinvalitys;

import java.util.Set;

public class ViestinvalitysClientException extends Exception {

  private Set<String> virheet;
  private int status;

  public ViestinvalitysClientException(Set<String> virheet, int status) {
    this.virheet = virheet;
    this.status = status;
  }

  public Set<String> getVirheet() {
    return this.virheet;
  }

  public int getStatus() {
    return this.status;
  }

}
