package fi.oph.viestinvalitys.vastaanotto.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import fi.oph.viestinvalitys.vastaanotto.resource.VastaanottajaResponseImpl;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Optional;
import java.util.UUID;

@JsonDeserialize(as = VastaanottajaResponseImpl.class)
@Schema(implementation = VastaanottajaResponseImpl.class)
public interface VastaanottajaResponse {

  String getTunniste();

  Optional<String> getNimi();

  String getSahkoposti();

  UUID getViestiTunniste();

  String getTila();
}
