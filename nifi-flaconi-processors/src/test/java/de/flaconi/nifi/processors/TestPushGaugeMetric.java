package de.flaconi.nifi.processors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("unchecked")
public class TestPushGaugeMetric {

  private static final String INSTANCE = "localhost";
  private static final String JOB_NAME = "job_name";
  private static final String GAUGE_NAME = "metric";
  private static final String GAUGE_NAME_INVALID = StringUtils.repeat(" ", 5);
  private static final String GAUGE_HELP = "help";
  private static final String GAUGE_HELP_INVALID = StringUtils.repeat(" ", 5);
  private static final Double GAUGE_VALUE = 42.0;
  private static final String GAUGE_VALUE_INVALID = StringUtils.repeat(" ", 5);
  private static final String[] GAUGE_LABEL_NAMES = new String[]{"method", "appId"};
  private static final String GAUGE_LABEL_NAMES_INVALID = "  ";
  private static final String[][] GAUGE_LABEL_VALUES = new String[][]{{"get", "1"}, {"post", "1"}};
  private static PushGateway pushGateway;
  private TestRunner testRunner;

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
    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(
        CollectorRegistry.class);
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
    ArgumentCaptor<CollectorRegistry> collectorRegistry = ArgumentCaptor.forClass(
        CollectorRegistry.class);
    verify(pushGateway).pushAdd(collectorRegistry.capture(), anyString(), anyMap());
    assertThat(
        collectorRegistry.getValue()
            .getSampleValue(GAUGE_NAME, GAUGE_LABEL_NAMES, GAUGE_LABEL_VALUES[0]),
        is(GAUGE_VALUE));
    assertThat(
        collectorRegistry.getValue()
            .getSampleValue(GAUGE_NAME, GAUGE_LABEL_NAMES, GAUGE_LABEL_VALUES[1]),
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

    testRunner.run();
  }

  @Test(expected = AssertionError.class)
  public void testOnTriggerWithNoValueAndNoLabels() {
    givenAProcessorWithNoValue();
    givenAFlowFile();

    testRunner.run();
  }

  @Test
  public void testOnTriggerWithInvalidData() {
    givenAProcessorWithInvalidData();
    givenAFlowFile();

    AssertionError exception = assertThrows(AssertionError.class, () -> testRunner.run());

    String message = exception.getMessage();
    assertThat(message, containsString("Processor has 5 validation failures"));
    assertThat(message, containsString("Pushgateway hostname"));
    assertThat(message, containsString("Metric Name"));
    assertThat(message, containsString("Metric Help"));
    assertThat(message, containsString("Metric Value"));
    //assertThat(message, containsString("Metric Labels"));
  }

  @Test
  public void testOnTriggerWithFlowFileContent() throws IOException {
    givenAProcessorWithLabelsSourceFlowContent();
    givenAFlowFileWithContent();

    testRunner.setValidateExpressionUsage(false);
    testRunner.run();
  }

  @Test
  public void testOnTriggerWithEmptyFlowFileContent() throws IOException {
    givenAProcessorWithLabelsSourceFlowContent();
    givenAFlowFile();

    testRunner.setValidateExpressionUsage(false);

    AssertionError exception = assertThrows(AssertionError.class, () -> testRunner.run());
    assertThat(exception.getMessage(), containsString("Flowfile content is empty"));
  }

  @Test
  public void testOnTriggerWithInvalidFlowFileContent() throws IOException {
    givenAProcessorWithLabelsSourceFlowContent();
    givenAFlowFileWithInvalidContent();

    testRunner.setValidateExpressionUsage(false);

    AssertionError exception = assertThrows(AssertionError.class, () -> testRunner.run());
    assertThat(exception.getCause(), instanceOf(IllegalArgumentException.class));
  }

  private void givenAProcessorWithNoValue() {
    testRunner.setProperty(PushGaugeMetric.PUSHGATEWAY_HOSTNAME, "localhost");
    testRunner.setProperty(PushGaugeMetric.PUSHGATEWAY_PORT, "42");
    testRunner.setProperty(PushGaugeMetric.INSTANCE, INSTANCE);
    testRunner.setProperty(PushGaugeMetric.JOB_NAME, JOB_NAME);
    testRunner.setProperty(PushGaugeMetric.GAUGE_NAME, GAUGE_NAME);
    testRunner.setProperty(PushGaugeMetric.GAUGE_HELP, GAUGE_HELP);
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
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS,
        StringUtils.join(GAUGE_LABEL_NAMES, PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABEL_VALUES_SOURCE,
        PushGaugeMetric.SOURCE_ATTRIBUTE);
    testRunner.setProperty("dynamic1",
        StringUtils.join(
            Stream.concat(Arrays.stream(GAUGE_LABEL_VALUES[0]), Stream.of(GAUGE_VALUE.toString()))
                .toArray(String[]::new),
            PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty("dynamic2",
        StringUtils.join(
            Stream.concat(Arrays.stream(GAUGE_LABEL_VALUES[1]), Stream.of(GAUGE_VALUE.toString()))
                .toArray(String[]::new),
            PushGaugeMetric.LABEL_SEPARATOR));
    givenAPushGateway();
  }

  private void givenAProcessorWithLabelsSourceFlowContent() throws IOException {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS,
        StringUtils.join(GAUGE_LABEL_NAMES, PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABEL_VALUES_SOURCE,
        PushGaugeMetric.SOURCE_CONTENT);
    givenAPushGateway();
  }

  private void givenAProcessorWithIncorrectNumberOfLabels() {
    givenAProcessorWithNoValue();
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS,
        StringUtils.join(GAUGE_LABEL_NAMES, PushGaugeMetric.LABEL_SEPARATOR));
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABEL_VALUES_SOURCE,
        PushGaugeMetric.SOURCE_ATTRIBUTE);
    testRunner.setProperty("dynamic1",
        StringUtils.join(GAUGE_LABEL_VALUES[0], PushGaugeMetric.LABEL_SEPARATOR));
  }

  private void givenAProcessorWithInvalidData() {
    testRunner.setProperty(PushGaugeMetric.GAUGE_NAME, GAUGE_NAME_INVALID);
    testRunner.setProperty(PushGaugeMetric.GAUGE_HELP, GAUGE_HELP_INVALID);
    testRunner.setProperty(PushGaugeMetric.GAUGE_VALUE, GAUGE_VALUE_INVALID);
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABELS, GAUGE_LABEL_NAMES_INVALID);
    testRunner.setProperty(PushGaugeMetric.GAUGE_LABEL_VALUES_SOURCE,
        PushGaugeMetric.SOURCE_ATTRIBUTE);
  }

  private void givenAFlowFile() {
    testRunner.enqueue("");
  }


  private void givenAFlowFileWithContent() {
    testRunner.enqueue(
        Arrays.stream(GAUGE_LABEL_VALUES)
            .map(i ->
                StringUtils.join(
                    Stream.concat(
                        Arrays.stream(i),
                        Stream.of(GAUGE_VALUE.toString())).toArray(String[]::new), ","))
            .collect(Collectors.joining("\n"))
    );
  }

  private void givenAFlowFileWithInvalidContent() {
    testRunner.enqueue(
        Arrays.stream(GAUGE_LABEL_VALUES)
            .map(i -> StringUtils.join(i, ","))
            .collect(Collectors.joining("\n"))
    );
  }

  private void givenAPushGateway() throws IOException {
    doNothing()
        .when(pushGateway)
        .pushAdd(any(CollectorRegistry.class), anyString(), anyMap());
  }

  private void givenAPushGatewayWithFailure() throws IOException {
    doThrow(IOException.class)
        .when(pushGateway)
        .pushAdd(any(CollectorRegistry.class), anyString(), anyMap());
  }

  public static class TestablePushGaugeMetric extends PushGaugeMetric {

    @Override
    public PushGateway newPushGateway(String host, String port) {
      return pushGateway;
    }
  }

}
