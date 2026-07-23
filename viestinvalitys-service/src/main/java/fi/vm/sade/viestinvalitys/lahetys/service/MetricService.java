package fi.vm.sade.viestinvalitys.lahetys.service;

import java.time.Instant;
import java.util.List;

import fi.vm.sade.viestinvalitys.lahetys.model.Prioriteetti;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class MetricService {

  private final CloudWatchClient cloudWatchClient;

  @Value("${viestinvalitys.metrics.namespace}")
  private String namespace;

  public void recordLahetykset(List<Prioriteetti> prioriteetit) {
    if (prioriteetit.isEmpty()) {
      return;
    }
    List<MetricDatum> datums =
            prioriteetit.stream()
                    .map(
                            p ->
                                    MetricDatum.builder()
                                            .metricName("LahetyksienMaara")
                                            .value(1.0)
                                            .storageResolution(1)
                                            .dimensions(Dimension.builder().name("Prioriteetti").value(p.name()).build())
                                            .timestamp(Instant.now())
                                            .unit(StandardUnit.COUNT)
                                            .build())
                    .toList();
    cloudWatchClient.putMetricData(
            PutMetricDataRequest.builder().namespace(namespace).metricData(datums).build());
  }
}
