package fi.oph.viestinvalitys.raportointi.integration

import fi.vm.sade.javautils.nio.cas.CasConfig

object ScalaCasConfig {

  def apply(username: String,
            password: String,
            casUrl: String,
            serviceUrl: String,
            csrf: String,
            callerId: String,
            serviceUrlSuffix: String,
            jSessionName: String): CasConfig = {

    new CasConfig.CasConfigBuilder(
      username,
      password,
      casUrl,
      serviceUrl,
      csrf,
      callerId,
      serviceUrlSuffix
    ).setJsessionName(jSessionName).build()
  }
}
