package de.flaconi.nifi.processors;

import static de.flaconi.nifi.processors.PushGaugeMetric.LABEL_SEPARATOR;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

public abstract class PushMetricProcessor extends AbstractProcessor {

  static final PropertyDescriptor PUSHGATEWAY_HOSTNAME = new PropertyDescriptor.Builder()
      .name("Pushgateway hostname")
      .description("Hostname or ip address of the Prometheus Pushgateway")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();
  static final PropertyDescriptor PUSHGATEWAY_PORT = new PropertyDescriptor.Builder()
      .name("Pushgateway port")
      .description("Port number of the Prometheus Pushgateway")
      .defaultValue("9091")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.NONE)
      .addValidator(StandardValidators.PORT_VALIDATOR)
      .build();
  static final PropertyDescriptor INSTANCE = new PropertyDescriptor.Builder()
      .name("Instance name")
      .description(
          "The hostname of this NiFi instance to be included in the metrics sent to Prometheus")
      .defaultValue("${hostname(true)}")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();
  static final PropertyDescriptor JOB_NAME = new PropertyDescriptor.Builder()
      .name("Job Name")
      .description("The job name to be included in the metrics sent to Prometheus")
      .defaultValue("global")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.NONE)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();
  static final Relationship REL_SUCCESS = new Relationship.Builder()
      .name("success")
      .description("Successfully the metric is sent to Prometheus Pushgateway")
      .build();
  static final Relationship REL_FAILURE = new Relationship.Builder()
      .name("failure")
      .description("Failed to send the metric to Prometheus Pushgateway")
      .build();
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
  private static final Pattern METRIC_LABEL_NAME_RE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
  private static final Pattern RESERVED_METRIC_LABEL_NAME_RE = Pattern.compile("__.*");
  Set<Relationship> relationships;
  List<PropertyDescriptor> descriptors;

  static void checkMetricName(String name) throws IllegalArgumentException {
    if (!METRIC_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid metric name: " + name);
    }
  }

  protected static void checkMetricLabelName(String name) {
    if (!METRIC_LABEL_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid metric label name: " + name);
    }
    if (RESERVED_METRIC_LABEL_NAME_RE.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "Invalid metric label name, reserved for internal use: " + name);
    }
  }

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return this.descriptors;
  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships;
  }

  Gauge registerGaugeMetric(CollectorRegistry registry, String metricName, String metricHelp)
      throws NumberFormatException {
    return Gauge.build()
        .name(metricName)
        .help(metricHelp)
        .register(registry);
  }

  Gauge registerGaugeMetric(CollectorRegistry registry, String metricName, String metricHelp,
      String[] labelNames) throws NumberFormatException {
    return Gauge.build()
        .name(metricName)
        .help(metricHelp)
        .labelNames(labelNames)
        .register(registry);
  }

  void setLabelValuesToGaugeMetric(Gauge metric, String labelsLine, int maxItem)
      throws IllegalArgumentException {
    String[] items = StringUtils.split(labelsLine, LABEL_SEPARATOR);
    if (items.length != maxItem) {
      throw new IllegalArgumentException(
          "Metric label value line (" + labelsLine + ") should have " + maxItem
              + " item(s) comma separated.");
    }
    metric
        .labels(Arrays.copyOfRange(items, 0, items.length - 1))
        .set(Double.parseDouble(items[items.length - 1]));
  }
}
