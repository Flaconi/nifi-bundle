package de.flaconi.nifi.processors;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.AttributeExpression;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@InputRequirement(InputRequirement.Requirement.INPUT_ALLOWED)
@Tags({"Prometheus", "Pushgateway", "Push", "Gauge"})
@CapabilityDescription("Pushes a gauge type metric to the Prometheus Pushgateway. If 'Metric Labels' is NOT given "
    + "then 'Metric Value' is taken as metric value otherwise any dynamic attribute defined is used to build the metric value.")
@DynamicProperty(name = "Key identifier like sequential numbers",
    value = "Comma separated labels with metric value at the end. e.g. 'get,${application_id},${http_request_total_get}' " +
        "or {$http_method},${http_request_total}'",
    description = "Specifies labels and value to be sent to the Prometheus Pushgateway",
    supportsExpressionLanguage = true)
public class PushGaugeMetric extends PushMetricProcessor {

  private static final Logger logger = LoggerFactory.getLogger(PushGaugeMetric.class);
  private static final Pattern METRIC_LABEL_NAME_RE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
  private static final Pattern RESERVED_METRIC_LABEL_NAME_RE = Pattern.compile("__.*");
  static final String LABEL_SEPARATOR = ",";

  static final PropertyDescriptor GAUGE_NAME = new PropertyDescriptor.Builder()
      .name("Metric Name")
      .description("The gauge metric name")
      .required(true)
      .expressionLanguageSupported(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_HELP = new PropertyDescriptor.Builder()
      .name("Metric Help")
      .description("The gauge metric help")
      .required(true)
      .expressionLanguageSupported(false)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_VALUE = new PropertyDescriptor.Builder()
      .name("Metric Value")
      .description("The gauge metric value")
      .required(false)
      .expressionLanguageSupported(true)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_LABELS = new PropertyDescriptor.Builder()
      .name("Metric Labels")
      .description("The gauge metric labels, comma-separated labels. If it is set then the dynamic attributes should be added.")
      .required(false)
      .expressionLanguageSupported(false)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:,]*")))
      .build();

  @Override
  protected void init(ProcessorInitializationContext context) {
    super.init(context);

    final List<PropertyDescriptor> descriptors = new ArrayList<>();
    descriptors.add(PUSHGATEWAY_HOSTNAME);
    descriptors.add(PUSHGATEWAY_PORT);
    descriptors.add(INSTANCE);
    descriptors.add(JOB_NAME);
    descriptors.add(GAUGE_NAME);
    descriptors.add(GAUGE_HELP);
    descriptors.add(GAUGE_VALUE);
    descriptors.add(GAUGE_LABELS);
    this.descriptors = Collections.unmodifiableList(descriptors);

    final Set<Relationship> relationships = new HashSet<>();
    relationships.add(REL_SUCCESS);
    relationships.add(REL_FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return this.descriptors;
  }

  @Override
  protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(String propertyDescriptorName) {
    return new PropertyDescriptor.Builder()
        .name(propertyDescriptorName)
        .description("Key is an identifier like sequential numbers, value is comma separated labels " +
            "with metric value at the end. e.g. 'get,${application_id},${http_request_total_get}")
        .required(true)
        .dynamic(true)
        .expressionLanguageSupported(true)
        .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
        .addValidator(Validator.VALID)
        .build();
  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships;
  }

  @Override
  public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
    final FlowFile flowFile = processSession.get();
    if (flowFile == null) {
      return;
    }

    final String host = processContext.getProperty(PUSHGATEWAY_HOSTNAME).evaluateAttributeExpressions(flowFile).getValue();
    final String port = processContext.getProperty(PUSHGATEWAY_PORT).getValue();
    final String instance = processContext.getProperty(INSTANCE).evaluateAttributeExpressions(flowFile).getValue();
    final String jobName = processContext.getProperty(JOB_NAME).getValue();
    final String metricName = processContext.getProperty(GAUGE_NAME).getValue();
    final String metricHelp = processContext.getProperty(GAUGE_HELP).getValue();
    final String metricLabels = processContext.getProperty(GAUGE_LABELS).getValue();
    final Map<String, String> groupingKey = Collections.singletonMap("instance", instance);
    final PushGateway pushGateway = newPushGateway(host, port);
    final CollectorRegistry registry = new CollectorRegistry();

    try {
      if (StringUtils.isEmpty(metricLabels)) {
        registerMetric(processContext, flowFile, registry, metricName, metricHelp);
      } else {
        registerMetricWithLabels(processContext, flowFile, registry, metricName, metricHelp, metricLabels);
      }

      pushGateway.pushAdd(registry, jobName, groupingKey);
      processSession.transfer(flowFile, REL_SUCCESS);
    } catch (IOException ioException) {
      logger.error(String.format("Failed to push the metric \"%s\" into pushgateway due to \"%s\"", metricName, ioException),
          ioException);
      processSession.transfer(flowFile, REL_FAILURE);
      processContext.yield();
    }
  }

  @Override
  protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
    final List<ValidationResult> reasons = new ArrayList<>(super.customValidate(validationContext));

    if (validationContext.getProperty(GAUGE_LABELS).isSet()) {
      if (validationContext.getProperties().keySet().stream().noneMatch(PropertyDescriptor::isDynamic)) {
        reasons.add(new ValidationResult.Builder()
            .subject("Dynamic property value")
            .valid(false)
            .explanation("there is no dynamic property defined for metric label value").build());
      }

      String labels = validationContext.getProperty(GAUGE_LABELS).getValue();
      if (!StringUtils.isEmpty(labels)) {
        int labelCount = labels.split(LABEL_SEPARATOR).length;

        boolean isDynamicPropertyValid = validationContext.getProperties().keySet().stream()
            .filter(PropertyDescriptor::isDynamic)
            .noneMatch(propertyDescriptor ->
                validationContext
                    .getProperty(propertyDescriptor)
                    .getValue()
                    .split(LABEL_SEPARATOR).length != labelCount + 1);
        if (!isDynamicPropertyValid) {
          reasons.add(new ValidationResult.Builder()
              .subject("Dynamic property value")
              .valid(false)
              .explanation("the dynamic property values should contain " + (labelCount + 1)
                  + " items (including metric label value at the end) since there is/are " + labelCount + " label(s) defined.").build());

        }
      }
    }

    return reasons;
  }

  private void registerMetric(ProcessContext processContext, FlowFile flowFile, CollectorRegistry registry,
                              String metricName, String metricHelp) throws NumberFormatException {
    Gauge.build()
        .name(metricName)
        .help(metricHelp)
        .register(registry)
        .set(processContext.getProperty(GAUGE_VALUE).evaluateAttributeExpressions(flowFile).asDouble());
  }

  private void registerMetricWithLabels(ProcessContext processContext, FlowFile flowFile, CollectorRegistry registry,
                                        String metricName, String metricHelp, String metricLabels) throws NumberFormatException {
    final Gauge metric = Gauge.build()
        .name(metricName)
        .help(metricHelp)
        .labelNames(metricLabels.split(LABEL_SEPARATOR))
        .register(registry);

    processContext.getProperties().keySet().stream().filter(PropertyDescriptor::isDynamic)
        .forEach(propertyDescriptor -> {
          String value = processContext.getProperty(propertyDescriptor).evaluateAttributeExpressions(flowFile).getValue();
          String[] labels = StringUtils.split(value, LABEL_SEPARATOR);
          metric
              .labels(Arrays.copyOfRange(labels, 0, labels.length - 1))
              .set(Double.parseDouble(labels[labels.length - 1]));
        });// last item is the value
  }

  public PushGateway newPushGateway(String host, String port) {
    return new PushGateway(host + ":" + port);
  }

}
