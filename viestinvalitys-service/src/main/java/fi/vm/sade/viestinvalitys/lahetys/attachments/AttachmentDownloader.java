package fi.vm.sade.viestinvalitys.lahetys.attachments;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class AttachmentDownloader {

  private final S3Client s3Client;

  @Value("${attachments.bucket.name}")
  private String bucketName;

  public byte[] download(UUID liiteTunniste) {
    return s3Client
            .getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucketName).key(liiteTunniste.toString()).build())
            .asByteArray();
  }
}
