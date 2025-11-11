package de.flaconi.nifi.reporting.prometheus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.metrics.jvm.JvmMetrics;
import org.apache.nifi.reporting.EventAccess;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.reporting.ReportingInitializationContext;
import org.apache.nifi.reporting.util.metrics.MetricsService;
import org.apache.nifi.util.MockPropertyValue;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TestPrometheusReportingTask {

  private final static String JVM_METRIC_NAME = "jvm_heap_used";
  private final static Double JVM_METRIC_VALUE = 42.0;
  private final static String STATUS_METRIC_NAME = "ActiveThreads";
  private final static Double STATUS_METRIC_VALUE = 10.0;
  private TestablePrometheusRepoprtingTask reportingTask;
  private ValidationContext validationContext;
  private ReportingContext reportingContext;
  private ReportingInitializationContext initializationContext;
  private MetricsService metricService;
  private PushGateway pushGateway;

  @Test
  public void testCustomValidate() {
    givenAReportingTaskWithDisabledReports();

    Collection<ValidationResult> result = reportingTask.customValidate(validationContext);

    assertThat(result.size(), is(1));
  }

  @Test
  public void testOnTrigger() throws InitializationException, IOException {
    givenAReportingTask();

    reportingTask.initialize(initializationContext);
    reportingTask.onTrigger(reportingContext);

    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(
        CollectorRegistry.class);
    verify(pushGateway).pushAdd(collectorRegistry.capture(), anyString(), anyMap());
    assertThat(collectorRegistry.getValue().getSampleValue(JVM_METRIC_NAME), is(JVM_METRIC_VALUE));
    assertThat(collectorRegistry.getValue().getSampleValue(STATUS_METRIC_NAME),
        is(STATUS_METRIC_VALUE));
  }

  @Test
  public void testOnTriggerWithOnlyJvmEnabled() throws InitializationException, IOException {
    givenAReportingTaskWithJvmEnabled();

    reportingTask.initialize(initializationContext);
    reportingTask.onTrigger(reportingContext);

    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(
        CollectorRegistry.class);
    verify(pushGateway).pushAdd(collectorRegistry.capture(), anyString(), anyMap());
    assertThat(collectorRegistry.getValue().getSampleValue(JVM_METRIC_NAME), is(JVM_METRIC_VALUE));
  }

  @Test
  public void testGetSupportedPropertyDescriptors() throws IOException {
    givenAReportingTask();

    List<PropertyDescriptor> properties = reportingTask.getSupportedPropertyDescriptors();

    assertThat(properties, containsInAnyOrder(
        PrometheusReportingTask.PUSHGATEWAY_HOSTNAME,
        PrometheusReportingTask.PUSHGATEWAY_PORT,
        PrometheusReportingTask.INSTANCE,
        PrometheusReportingTask.JOB_NAME,
        PrometheusReportingTask.INCLUDE_JVM_METRICS,
        PrometheusReportingTask.INCLUDE_STATUS_METRICS,
        PrometheusReportingTask.PROCESS_GROUP_ID
    ));
  }

  private void givenAReportingTaskWithDisabledReports() {
    reportingTask = new TestablePrometheusRepoprtingTask();
    validationContext = mock(ValidationContext.class);
    when(validationContext.getProperty(PrometheusReportingTask.INCLUDE_JVM_METRICS))
        .thenReturn(new MockPropertyValue("false"));
    when(validationContext.getProperty(PrometheusReportingTask.INCLUDE_STATUS_METRICS))
        .thenReturn(new MockPropertyValue("false"));
  }

  private void givenAReportingTask() throws IOException {
    givenAReportingTask(true);
  }

  private void givenAReportingTaskWithJvmEnabled() throws IOException {
    givenAReportingTask(false);
  }

  private void givenAReportingTask(boolean includeStatus) throws IOException {
    reportingTask = new TestablePrometheusRepoprtingTask();

    reportingContext = mock(ReportingContext.class);
    when(reportingContext.getProperty(PrometheusReportingTask.PUSHGATEWAY_HOSTNAME))
        .thenReturn(new MockPropertyValue("host"));
    when(reportingContext.getProperty(PrometheusReportingTask.PUSHGATEWAY_PORT))
        .thenReturn(new MockPropertyValue("9090"));
    when(reportingContext.getProperty(PrometheusReportingTask.INSTANCE))
        .thenReturn(new MockPropertyValue("instance"));
    when(reportingContext.getProperty(PrometheusReportingTask.JOB_NAME))
        .thenReturn(new MockPropertyValue("job"));
    when(reportingContext.getProperty(PrometheusReportingTask.INCLUDE_JVM_METRICS))
        .thenReturn(new MockPropertyValue("true"));
    when(reportingContext.getProperty(PrometheusReportingTask.INCLUDE_STATUS_METRICS))
        .thenReturn(new MockPropertyValue(Boolean.toString(includeStatus)));
    when(reportingContext.getProperty(PrometheusReportingTask.PROCESS_GROUP_ID))
        .thenReturn(new MockPropertyValue(null));

    initializationContext = mock(ReportingInitializationContext.class);
    when(initializationContext.getIdentifier()).thenReturn(UUID.randomUUID().toString());
    when(initializationContext.getLogger()).thenReturn(mock(ComponentLog.class));
    when(initializationContext.getName()).thenReturn("reporting task");

    metricService = mock(MetricsService.class);
    when(metricService.getMetrics(any(JvmMetrics.class)))
        .thenReturn(Collections.singletonMap(JVM_METRIC_NAME, JVM_METRIC_VALUE.toString()));
    if (includeStatus) {
      ProcessGroupStatus status = new ProcessGroupStatus();
      status.setActiveThreadCount(10);

      final EventAccess eventAccess = mock(EventAccess.class);
      when(reportingContext.getEventAccess()).thenReturn(eventAccess);
      when(eventAccess.getControllerStatus()).thenReturn(status);
      when(metricService.getMetrics(any(ProcessGroupStatus.class), anyBoolean()))
          .thenReturn(Collections.singletonMap(STATUS_METRIC_NAME, STATUS_METRIC_VALUE.toString()));
    }

    pushGateway = mock(PushGateway.class);
    doNothing()
        .when(pushGateway)
        .pushAdd(isA(CollectorRegistry.class), anyString(), anyMap());
  }

  private class TestablePrometheusRepoprtingTask extends PrometheusReportingTask {

    @Override
    protected MetricsService newPushGateway() {
      return metricService;
    }

    @Override
    public PushGateway newPushGateway(String host, String port) {
      return pushGateway;
    }
  }
}
