package de.flaconi.nifi.reporting.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.nifi.annotation.configuration.DefaultSchedule;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.metrics.jvm.JmxJvmMetrics;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.AbstractReportingTask;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.reporting.util.metrics.MetricsService;
import org.apache.nifi.scheduling.SchedulingStrategy;

@Tags({"reporting", "prometheus", "metrics"})
@CapabilityDescription("Publishes metrics from NiFi to Prometheus Push Gateway")
@DefaultSchedule(strategy = SchedulingStrategy.TIMER_DRIVEN, period = "1 min")
public class PrometheusReportingTask extends AbstractReportingTask {

  static final PropertyDescriptor PUSHGATEWAY_HOSTNAME = new PropertyDescriptor.Builder()
      .name("Pushgateway hostname")
      .description("Hostname or ip address of the Prometheus Pushgateway")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
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
      .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
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

  static final PropertyDescriptor INCLUDE_JVM_METRICS = new PropertyDescriptor.Builder()
      .name("Include Jvm Metrics")
      .description("Includes the jvm metrics in the report.")
      .required(false)
      .defaultValue("true")
      .allowableValues("true", "false")
      .build();

  static final PropertyDescriptor INCLUDE_STATUS_METRICS = new PropertyDescriptor.Builder()
      .name("Include processor group Metrics")
      .description("Includes the processor group metrics in the report.")
      .required(false)
      .defaultValue("true")
      .allowableValues("true", "false")
      .build();

  static final PropertyDescriptor PROCESS_GROUP_ID = new PropertyDescriptor.Builder()
      .name("Process Group ID")
      .description(
          "If specified, the reporting task will send metrics about this process group only. If"
              + " not, the root process group is used and global metrics are sent.")
      .required(false)
      .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    final List<PropertyDescriptor> properties = new ArrayList<>(
        super.getSupportedPropertyDescriptors());
    properties.add(PUSHGATEWAY_HOSTNAME);
    properties.add(PUSHGATEWAY_PORT);
    properties.add(INSTANCE);
    properties.add(JOB_NAME);
    properties.add(INCLUDE_JVM_METRICS);
    properties.add(INCLUDE_STATUS_METRICS);
    properties.add(PROCESS_GROUP_ID);
    return properties;
  }

  @Override
  protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
    final List<ValidationResult> results = new ArrayList<>(super.customValidate(validationContext));

    final boolean includeJvmMetrics = validationContext.getProperty(INCLUDE_JVM_METRICS)
        .asBoolean();
    final boolean includeStatusMetrics = validationContext.getProperty(INCLUDE_STATUS_METRICS)
        .asBoolean();

    if (!includeJvmMetrics && !includeStatusMetrics) {
      results.add(new ValidationResult.Builder()
          .input("Metric report")
          .valid(false)
          .explanation(
              "No reporting enabled. Please enable at least one of jvm and processor group.")
          .build());
    }

    return results;
  }

  @Override
  public void onTrigger(ReportingContext context) {
    final String host = context.getProperty(PUSHGATEWAY_HOSTNAME).evaluateAttributeExpressions()
        .getValue();
    final String port = context.getProperty(PUSHGATEWAY_PORT).getValue();
    final String instance = context.getProperty(INSTANCE).evaluateAttributeExpressions().getValue();
    final String jobName = context.getProperty(JOB_NAME).getValue();
    final boolean includeJvmMetrics = context.getProperty(INCLUDE_JVM_METRICS).asBoolean();
    final boolean includeStatusMetrics = context.getProperty(INCLUDE_STATUS_METRICS).asBoolean();

    final MetricsService metricsService = newPushGateway();
    final Map<String, String> groupingKey = Collections.singletonMap("instance", instance);
    final PushGateway pushGateway = newPushGateway(host, port);
    final CollectorRegistry registry = new CollectorRegistry();

    Map<String, String> metrics = new HashMap<>();

    if (includeJvmMetrics) {
      metrics.putAll(metricsService.getMetrics(JmxJvmMetrics.getInstance()));
    }

    if (includeStatusMetrics) {
      final boolean processGroupIdSet = context.getProperty(PROCESS_GROUP_ID).isSet();
      final String processGroupId =
          processGroupIdSet ? context.getProperty(PROCESS_GROUP_ID).evaluateAttributeExpressions()
              .getValue() : null;
      final ProcessGroupStatus status =
          processGroupId == null ? context.getEventAccess().getControllerStatus()
              : context.getEventAccess().getGroupStatus(processGroupId);
      metrics.putAll(metricsService.getMetrics(status, processGroupIdSet));
    }

    try {
      metrics.forEach((key, value) ->
          Gauge.build()
              .name(Collector.sanitizeMetricName(key))
              .help(key)
              .register(registry).set(Double.valueOf(value))
      );
      pushGateway.pushAdd(registry, jobName, groupingKey);
    } catch (IOException ioException) {
      getLogger().error("Failed to push metrics into pushgateway", ioException);
    }
  }

  protected PushGateway newPushGateway(String host, String port) {
    return new PushGateway(host + ":" + port);
  }

  protected MetricsService newPushGateway() {
    return new MetricsService();
  }
}
