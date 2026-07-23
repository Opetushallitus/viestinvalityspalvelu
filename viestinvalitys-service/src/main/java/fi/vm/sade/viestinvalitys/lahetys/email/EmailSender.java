package fi.vm.sade.viestinvalitys.lahetys.email;

import java.io.ByteArrayOutputStream;
import java.util.List;

import fi.vm.sade.viestinvalitys.lahetys.config.Mode;
import fi.vm.sade.viestinvalitys.lahetys.model.Attachment;
import fi.vm.sade.viestinvalitys.lahetys.model.Vastaanottaja;
import fi.vm.sade.viestinvalitys.lahetys.model.Viesti;
import lombok.extern.slf4j.Slf4j;
import org.simplejavamail.api.email.ContentTransferEncoding;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.converter.EmailConverter;
import org.simplejavamail.email.EmailBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

@Slf4j
@Component
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class EmailSender {

  private final SesClient sesClient;

  @Value("${viestinvalitys.ses.configuration-set-name}")
  private String configurationSetName;

  @Value("${viestinvalitys.ses.from-email-address}")
  private String fromEmailAddress;

  @Value("${viestinvalitys.mode:PRODUCTION}")
  private Mode mode;

  public EmailSender(SesClient sesClient) {
    this.sesClient = sesClient;
  }

  public String send(Viesti viesti, Vastaanottaja vastaanottaja, List<Attachment> liitteet) {
    EmailPopulatingBuilder builder =
            EmailBuilder.startingBlank()
                    .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
                    .withSubject(viesti.otsikko());

    if (viesti.replyTo() != null) {
      builder = builder.withReplyTo(viesti.replyTo());
    }
    builder =
            switch (viesti.sisallonTyyppi()) {
              case TEXT -> builder.withPlainText(viesti.sisalto());
              case HTML -> builder.withHTMLText(viesti.sisalto());
            };
    for (Attachment a : liitteet) {
      builder = builder.withAttachment(a.nimi(), a.bytes(), a.contentType());
    }

    if (mode == Mode.PRODUCTION) {
      Email email =
              builder
                      .from(viesti.lahettaja().nimi(), viesti.lahettaja().sahkoposti())
                      .to(vastaanottaja.kontakti().nimi(), vastaanottaja.kontakti().sahkoposti())
                      .buildEmail();
      return sendRaw(email);
    }
    return sendTest(vastaanottaja, builder.from(viesti.lahettaja().nimi(), fromEmailAddress));
  }

  private String sendTest(Vastaanottaja vastaanottaja, EmailPopulatingBuilder builder) {
    log.info("Lähetetään viestiä testimoodissa");
    String localPart = vastaanottaja.kontakti().sahkoposti().split("@")[0];
    if (localPart.endsWith("+bounce")) {
      return sendRaw(builder.to("bounce@simulator.amazonses.com").buildEmail());
    } else if (localPart.endsWith("+complaint")) {
      return sendRaw(builder.to("complaint@simulator.amazonses.com").buildEmail());
    } else if (localPart.endsWith("+success")) {
      return sendRaw(builder.to("success@simulator.amazonses.com").buildEmail());
    }
    log.info(builder.to(vastaanottaja.kontakti().sahkoposti()).buildEmail().toString());
    return vastaanottaja.tunniste().toString();
  }

  private String sendRaw(Email email) {
    byte[] raw;
    try {
      var stream = new ByteArrayOutputStream();
      EmailConverter.emailToMimeMessage(email).writeTo(stream);
      raw = stream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("MIME serialization failed", e);
    }
    return sesClient
            .sendRawEmail(
                    SendRawEmailRequest.builder()
                            .configurationSetName(configurationSetName)
                            .rawMessage(RawMessage.builder().data(SdkBytes.fromByteArray(raw)).build())
                            .build())
            .messageId();
  }
}
