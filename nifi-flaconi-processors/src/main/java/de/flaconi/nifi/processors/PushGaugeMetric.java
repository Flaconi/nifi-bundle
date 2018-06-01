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

@InputRequirement(InputRequirement.Requirement.INPUT_ALLOWED)
@Tags({"Prometheus", "Pushgateway", "Push", "Gauge"})
@CapabilityDescription("Pushes a gauge type metric to the Prometheus Pushgateway. If 'Metric Labels' is NOT given "
    + "then 'Metric Value' is taken as metric value otherwise any dynamic attribute defined is used to build the metric value.")
@DynamicProperty(name = "Gauge metric index",
    value = "Comma separated labels with metric value at the end. e.g. 'get,${application_id},${http_request_total_get}' " +
        "or {$http_method},${http_request_total}'",
    description = "Specifies labels and value to be sent to the Prometheus Pushgateway",
    supportsExpressionLanguage = true)
public class PushGaugeMetric extends PushMetricProcessor {

  private static final Logger logger = LoggerFactory.getLogger(PushGaugeMetric.class);

  private static final PropertyDescriptor GAUGE_NAME = new PropertyDescriptor.Builder()
      .name("Metric Name")
      .description("The gauge metric name")
      .required(true)
      .expressionLanguageSupported(false)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();

  private static final PropertyDescriptor GAUGE_HELP = new PropertyDescriptor.Builder()
      .name("Metric Help")
      .description("The gauge metric help")
      .required(false)
      .expressionLanguageSupported(false)
      .addValidator(Validator.VALID)
      .build();

  private static final PropertyDescriptor GAUGE_VALUE = new PropertyDescriptor.Builder()
      .name("Metric Value")
      .description("The gauge metric value")
      .required(false)
      .expressionLanguageSupported(true)
      .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.NUMBER))
      .build();

  private static final PropertyDescriptor GAUGE_LABELS = new PropertyDescriptor.Builder()
      .name("Metric Labels")
      .description("The gauge metric labels, comma-separated labels. If it is set then the dynamic attributes should be added.")
      .required(false)
      .expressionLanguageSupported(false)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
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

    final String host = processContext.getProperty(PUSHGATEWAY_HOSTNAME).evaluateAttributeExpressions().getValue();
    final String port = processContext.getProperty(PUSHGATEWAY_PORT).evaluateAttributeExpressions().getValue();
    final String instance = processContext.getProperty(INSTANCE).evaluateAttributeExpressions().getValue();
    final String jobName = processContext.getProperty(JOB_NAME).evaluateAttributeExpressions().getValue();
    final String metricLabels = processContext.getProperty(GAUGE_LABELS).evaluateAttributeExpressions().getValue();
    final Map<String, String> groupingKey = Collections.singletonMap("instance", instance);
    final PushGateway pushGateway = new PushGateway(host + ":" + port);
    final CollectorRegistry registry = new CollectorRegistry();

    try {
      if (StringUtils.isEmpty(metricLabels)) {
        registerMetric(processContext, registry);
      } else {
        registerMetricWithLabels(processContext, flowFile, registry, metricLabels);
      }

      pushGateway.pushAdd(registry, jobName, groupingKey);
      processSession.transfer(flowFile, REL_SUCCESS);
    } catch (IOException ioException) {
      processSession.transfer(flowFile, REL_FAILURE);
      throw new ProcessException(ioException);
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
        int labelCount = labels.split(",").length;
        boolean isDynamicPropertyValid = validationContext.getProperties().keySet().stream()
            .filter(PropertyDescriptor::isDynamic)
            .noneMatch(propertyDescriptor ->
                // labelCount + 1 (value)
                validationContext
                    .getProperty(propertyDescriptor)
                    .evaluateAttributeExpressions()
                    .getValue()
                    .split(",").length != labelCount + 1);
        if (!isDynamicPropertyValid) {
          reasons.add(new ValidationResult.Builder()
              .subject("Dynamic property value")
              .valid(false)
              .explanation("the dynamic property values should contain " + (labelCount + 1)
                  + " items (including metric label value at the end) since there are "+ labelCount + " label(s) defined.").build());

        }
      }
    }

    return reasons;
  }

  private void registerMetric(ProcessContext processContext, CollectorRegistry registry) throws NumberFormatException {
    Gauge.build()
        .name(processContext.getProperty(GAUGE_NAME).evaluateAttributeExpressions().getValue())
        .help(processContext.getProperty(GAUGE_HELP).evaluateAttributeExpressions().getValue())
        .register(registry)
        .set(processContext.getProperty(GAUGE_VALUE).evaluateAttributeExpressions().asDouble());
  }

  private void registerMetricWithLabels(ProcessContext processContext, FlowFile flowFile, CollectorRegistry registry, String metricLabels) throws NumberFormatException {
    final Gauge metric = Gauge.build()
        .name(processContext.getProperty(GAUGE_NAME).evaluateAttributeExpressions().getValue())
        .help(processContext.getProperty(GAUGE_HELP).evaluateAttributeExpressions().getValue())
        .labelNames(metricLabels.split(","))
        .register(registry);

    processContext.getProperties().keySet().stream().filter(PropertyDescriptor::isDynamic)
        .forEach(propertyDescriptor ->
            metric
                .labels(propertyDescriptor.getName().split(","))
                .set(processContext.getProperty(propertyDescriptor).evaluateAttributeExpressions(flowFile).asDouble()));
  }

}
