package de.flaconi.nifi.processors;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import org.apache.commons.io.IOUtils;
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
import org.apache.nifi.expression.ExpressionLanguageScope;
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
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
  static final String SOURCE_ATTRIBUTE = "flowfile-attribute";
  static final String SOURCE_CONTENT = "flowfile-content";
  static final String LABEL_SEPARATOR = ",";

  static final PropertyDescriptor GAUGE_NAME = new PropertyDescriptor.Builder()
      .name("Metric Name")
      .description("The gauge metric name")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_HELP = new PropertyDescriptor.Builder()
      .name("Metric Help")
      .description("The gauge metric help")
      .required(true)
      .expressionLanguageSupported(ExpressionLanguageScope.NONE)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_VALUE = new PropertyDescriptor.Builder()
      .name("Metric Value")
      .description("The gauge metric value")
      .required(false)
      .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .build();

  static final PropertyDescriptor GAUGE_LABELS = new PropertyDescriptor.Builder()
      .name("Metric Labels")
      .description("The gauge metric labels, comma-separated labels. If it is set then the dynamic attributes should be added.")
      .required(false)
      .expressionLanguageSupported(ExpressionLanguageScope.NONE)
      .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
      .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:,]*")))
      .build();

  static final PropertyDescriptor GAUGE_LABEL_VALUES_SOURCE = new PropertyDescriptor.Builder()
      .name("Source of metric label values")
      .description("Indicates whether the metric label values are taken from the FlowFile content or a FlowFile dynamic attribute; " +
          "if The gauge metric labels is defined then the source is required.")
      .required(false)
      .allowableValues(SOURCE_ATTRIBUTE, SOURCE_CONTENT)
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
    descriptors.add(GAUGE_LABEL_VALUES_SOURCE);
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
        .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
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
    final String metricName = processContext.getProperty(GAUGE_NAME).evaluateAttributeExpressions(flowFile).getValue();
    final String metricHelp = processContext.getProperty(GAUGE_HELP).getValue();
    final String metricLabels = processContext.getProperty(GAUGE_LABELS).getValue();
    final String metricLabelValuesSource = processContext.getProperty(GAUGE_LABEL_VALUES_SOURCE).getValue();
    final Map<String, String> groupingKey = Collections.singletonMap("instance", instance);
    final PushGateway pushGateway = newPushGateway(host, port);
    final CollectorRegistry registry = new CollectorRegistry();

    try {
      checkMetricName(metricName);
      if (StringUtils.isEmpty(metricLabels)) {
        final Gauge gauge = registerGaugeMetric(registry, metricName, metricHelp);
        gauge.set(processContext.getProperty(GAUGE_VALUE).evaluateAttributeExpressions(flowFile).asDouble());
      } else {
        String[] labelNames = metricLabels.split(LABEL_SEPARATOR);
        final Gauge gauge = registerGaugeMetric(registry, metricName, metricHelp, metricLabels.split(LABEL_SEPARATOR));
        if (metricLabelValuesSource.equals(SOURCE_ATTRIBUTE)) {
          setMetricWithLabelsFromAttributes(gauge, processContext, flowFile, labelNames.length);
        } else {
          setMetricWithLabelsFromContent(gauge, processSession, flowFile, labelNames.length);
        }
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
      if (!validationContext.getProperty(GAUGE_LABEL_VALUES_SOURCE).isSet()) {
        reasons.add(new ValidationResult.Builder()
            .subject("Source of metric label values")
            .valid(false)
            .explanation("source of metric label values is not defined").build());
      }

      String labels = validationContext.getProperty(GAUGE_LABELS).getValue();
      String labelValuesSource = validationContext.getProperty(GAUGE_LABEL_VALUES_SOURCE).getValue();
      if (labelValuesSource != null && labelValuesSource.equals(SOURCE_ATTRIBUTE) && !StringUtils.isEmpty(labels)) {
        if (validationContext.getProperties().keySet().stream().noneMatch(PropertyDescriptor::isDynamic)) {
          reasons.add(new ValidationResult.Builder()
              .subject("Dynamic property value")
              .valid(false)
              .explanation("there is no dynamic property defined for metric label value").build());
        }

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

  private void setMetricWithLabelsFromAttributes(Gauge gauge, ProcessContext processContext, FlowFile flowFile, int labelNamesCount) throws NumberFormatException {
    processContext.getProperties().keySet().stream().filter(PropertyDescriptor::isDynamic)
        .forEach(propertyDescriptor -> {
          String value = processContext.getProperty(propertyDescriptor).evaluateAttributeExpressions(flowFile).getValue();
          setLabelValuesToGaugeMetric(gauge, value, labelNamesCount + 1);
        });
  }

  private void setMetricWithLabelsFromContent(Gauge gauge, ProcessSession processSession, FlowFile flowFile, int labelNamesCount) throws NumberFormatException, IOException {
    String content = getFlowContent(processSession, flowFile);
    if (StringUtils.isEmpty(content)) {
      throw new IllegalArgumentException("Flowfile content is empty.");
    }
    IOUtils
        .readLines(new StringReader(content))
        .forEach(item -> setLabelValuesToGaugeMetric(gauge, item, labelNamesCount + 1));
  }

  private String getFlowContent(ProcessSession processSession, FlowFile flowFile) {
    final AtomicReference<String> content = new AtomicReference<>();
    processSession.read(flowFile, in -> content.set(IOUtils.toString(in, Charset.forName("UTF-8"))));
    return content.get();
  }

  public PushGateway newPushGateway(String host, String port) {
    return new PushGateway(host + ":" + port);
  }
}
