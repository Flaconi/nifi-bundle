package de.flaconi.nifi.processors;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.List;
import java.util.Set;

public abstract class PushMetricProcessor extends AbstractProcessor {

  static final PropertyDescriptor PUSHGATEWAY_HOSTNAME = new PropertyDescriptor.Builder()
      .name("Pushgateway hostname")
      .description("Hostname or ip address of the Prometheus Pushgateway")
      .required(true)
      .expressionLanguageSupported(true)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();

  static final PropertyDescriptor PUSHGATEWAY_PORT = new PropertyDescriptor.Builder()
      .name("Pushgateway port")
      .description("Port number of the Prometheus Pushgateway")
      .defaultValue("9091")
      .required(true)
      .expressionLanguageSupported(false)
      .addValidator(StandardValidators.PORT_VALIDATOR)
      .build();

  static final PropertyDescriptor INSTANCE = new PropertyDescriptor.Builder()
      .name("Instance name")
      .description("The hostname of this NiFi instance to be included in the metrics sent to Prometheus")
      .defaultValue("${hostname(true)}")
      .required(true)
      .expressionLanguageSupported(true)
      .addValidator(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
      .build();

  static final PropertyDescriptor JOB_NAME = new PropertyDescriptor.Builder()
      .name("Job Name")
      .description("The job name to be included in the metrics sent to Prometheus")
      .defaultValue("global")
      .required(true)
      .expressionLanguageSupported(false)
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

  Set<Relationship> relationships;
  List<PropertyDescriptor> descriptors;

  @Override
  protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return this.descriptors;
  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships;
  }

}
