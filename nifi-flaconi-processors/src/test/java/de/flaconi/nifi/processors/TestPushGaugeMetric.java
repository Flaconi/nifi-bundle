package de.flaconi.nifi.processors;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class TestPushGaugeMetric {

  private TestRunner testRunner;
  private static PushGateway pushGateway;
  private static final String INSTANCE = "localhost";
  private static final String JOB_NAME = "job_name";
  private static final String GAUGE_NAME = "metric";
  private static final Double GAUGE_VALUE = 42.0;
  private static final String[] GAUGE_LABEL_NAMES = new String[]{"method", "appId"};
  private static final String[][] GAUGE_LABEL_VALUES = new String[][]{{"get", "1"}, {"post", "1"}};

  @Before
  public void before() {
    pushGateway = Mockito.mock(PushGateway.class);
    testRunner = TestRunners.newTestRunner(TestablePushGaugeMetric.class);
  }

  @After
  public void after() {
    Mockito.reset(pushGateway);
    testRunner.shutdown();
  }

  @Test
  public void testOnTriggerWithNoLabels() throws IOException {
    givenAProcessorWithValueAndSuccessConnection();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();

    testRunner.assertTransferCount(PushGaugeMetric.REL_SUCCESS, 1);
    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(CollectorRegistry.class);
    ArgumentCaptor<Map<String, String>> groupingKey = ArgumentCaptor.forClass((Class) Map.class);
    verify(pushGateway).pushAdd(collectorRegistry.capture(), eq(JOB_NAME), groupingKey.capture());
    assertThat(groupingKey.getValue(), hasEntry("instance", INSTANCE));
    assertThat(collectorRegistry.getValue().getSampleValue(GAUGE_NAME), is(GAUGE_VALUE));
  }

  @Test
  public void testOnTriggerWithLabels() throws IOException {
    givenAProcessorWithLabelsAndSuccessConnection();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();

    testRunner.assertTransferCount(PushGaugeMetric.REL_SUCCESS, 1);
    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(CollectorRegistry.class);
    verify(pushGateway).pushAdd(collectorRegistry.capture(), anyString(), anyMap());
    assertThat(
        collectorRegistry.getValue().getSampleValue(GAUGE_NAME, GAUGE_LABEL_NAMES, GAUGE_LABEL_VALUES[0]),
        is(GAUGE_VALUE));
    assertThat(
        collectorRegistry.getValue().getSampleValue(GAUGE_NAME, GAUGE_LABEL_NAMES, GAUGE_LABEL_VALUES[1]),
        is(GAUGE_VALUE));
  }

  @Test
  public void testOnTriggerWithConnectionFailure() throws IOException {
    givenAProcessorWithValueAndFailedConnection();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();

    testRunner.assertTransferCount(PushGaugeMetric.REL_FAILURE, 1);
  }

  @Test(expected = AssertionError.class)
  public void testOnTriggerWithIncorrectNumberOfLabels() {
    givenAProcessorWithIncorrectNumberOfLabels();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();
  }

  @Test(expected = AssertionError.class)
  public void testOnTriggerWithNoValueAndNoLabels() {
    givenAProcessorWithNoValue();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();
  }

  private void givenAProcessorWithNoValue() {
    testRunner.setProperty(PushGaugeMetric.PUSHGATEWAY_HOSTNAME, "localhost");
    testRunner.setProperty(PushGaugeMetric.PUSHGATEWAY_PORT, "42");
    testRunner.setProperty(PushGaugeMetric.INSTANCE, INSTANCE);
    testRunner.setProperty(PushGaugeMetric.JOB_NAME, JOB_NAME);
    testRunner.setProperty(PushGaugeMetric.GAUGE_NAME, GAUGE_NAME);
    testRunner.setProperty(PushGaugeMetric.GAUGE_HELP, "metric help");
  }

  private void givenAProcessorWithValueAndSuccessConnection() throws IOException {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_VALUE, GAUGE_VALUE.toString());
    givenAPushGateway();
  }

  private void givenAProcessorWithValueAndFailedConnection() throws IOException {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_VALUE, GAUGE_VALUE.toString());
    givenAPushGatewayWithFailure();
  }

  private void givenAProcessorWithLabelsAndSuccessConnection() throws IOException {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS, StringUtils.join(GAUGE_LABEL_NAMES, PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty("dynamic1",
        StringUtils.join(
            Stream.concat(Arrays.stream(GAUGE_LABEL_VALUES[0]), Stream.of(GAUGE_VALUE.toString())).toArray(String[]::new),
            PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty("dynamic2",
        StringUtils.join(
            Stream.concat(Arrays.stream(GAUGE_LABEL_VALUES[1]), Stream.of(GAUGE_VALUE.toString())).toArray(String[]::new),
            PushGaugeMetric.LABEL_SEPARATOR));
    givenAPushGateway();
  }

  private void givenAProcessorWithIncorrectNumberOfLabels() {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS, StringUtils.join(GAUGE_LABEL_NAMES, PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty("dynamic1",
        StringUtils.join(GAUGE_LABEL_VALUES[0], PushGaugeMetric.LABEL_SEPARATOR));
  }

  private void givenAFlowFile() {
    testRunner.enqueue("");
  }

  private void givenAPushGateway() throws IOException {
    doNothing()
        .when(pushGateway)
        .pushAdd(isA(CollectorRegistry.class), anyString(), anyMapOf(String.class, String.class));
  }

  private void givenAPushGatewayWithFailure() throws IOException {
    doThrow(IOException.class)
        .when(pushGateway)
        .pushAdd(isA(CollectorRegistry.class), anyString(), anyMapOf(String.class, String.class));
  }

  public static class TestablePushGaugeMetric extends PushGaugeMetric {
    @Override
    public PushGateway newPushGateway(String host, String port) {
      return pushGateway;
    }
  }

}
