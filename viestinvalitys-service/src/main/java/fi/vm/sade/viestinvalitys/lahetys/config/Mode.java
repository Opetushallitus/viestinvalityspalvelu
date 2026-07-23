package fi.vm.sade.viestinvalitys.lahetys.config;

import fi.vm.sade.viestinvalitys.lahetys.email.EmailSender;

/**
 * Run mode, mirroring the Scala {@code ConfigurationUtil.getMode()}. In non-PRODUCTION mode the
 * sender routes to the SES simulator / logs instead of sending real mail (see {@link EmailSender}).
 */
public enum Mode {
  LOCAL,
  TEST,
  PRODUCTION
}
