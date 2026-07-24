package fi.vm.sade.viestinvalitys.lahetys.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

class MetricServiceTest {

  @Test
  void recordVastaanototEmitsVastaanottojenMaaraWithPrioriteettiDimension() {
    CloudWatchClient cloudWatchClient = mock(CloudWatchClient.class);
    MetricService metricService = new MetricService(cloudWatchClient);
    ReflectionTestUtils.setField(metricService, "namespace", "test-viestinvalitys");

    metricService.recordVastaanotot("KORKEA", 3);

    ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
    verify(cloudWatchClient).putMetricData(captor.capture());

    PutMetricDataRequest request = captor.getValue();
    assertEquals("test-viestinvalitys", request.namespace());
    assertEquals(1, request.metricData().size());

    MetricDatum datum = request.metricData().get(0);
    assertEquals("VastaanottojenMaara", datum.metricName());
    assertEquals(3.0, datum.value());
    assertEquals(StandardUnit.COUNT, datum.unit());
    assertEquals(1, datum.dimensions().size());
    assertEquals("Prioriteetti", datum.dimensions().get(0).name());
    assertEquals("KORKEA", datum.dimensions().get(0).value());
  }
}
