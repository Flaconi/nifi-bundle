package de.flaconi.nifi.processors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestPushMetricProcessorSimple {

  @Test
  public void testCheckMetricNameValid() {
    // Test valid metric names - should not throw exceptions
    PushMetricProcessor.checkMetricName("valid_metric");
    PushMetricProcessor.checkMetricName("valid_metric_123");
    PushMetricProcessor.checkMetricName("_valid_metric");
    PushMetricProcessor.checkMetricName("valid:metric");
    PushMetricProcessor.checkMetricName("Valid_Metric");
    PushMetricProcessor.checkMetricName("a");
    PushMetricProcessor.checkMetricName("_");
    PushMetricProcessor.checkMetricName("metric_name_with_numbers_123");
    PushMetricProcessor.checkMetricName("http:request_duration_seconds");
    PushMetricProcessor.checkMetricName("process:cpu_seconds_total");

    assertTrue("Valid metric names should pass validation", true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameInvalidStartsWithNumber() {
    PushMetricProcessor.checkMetricName("123invalid");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameInvalidContainsSpace() {
    PushMetricProcessor.checkMetricName("invalid metric");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameInvalidContainsDash() {
    PushMetricProcessor.checkMetricName("invalid-metric");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameInvalidContainsDot() {
    PushMetricProcessor.checkMetricName("invalid.metric");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameEmpty() {
    PushMetricProcessor.checkMetricName("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameWithSpecialChars() {
    PushMetricProcessor.checkMetricName("metric@name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameWithPound() {
    PushMetricProcessor.checkMetricName("metric#name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameWithPercent() {
    PushMetricProcessor.checkMetricName("metric%name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameWithSlash() {
    PushMetricProcessor.checkMetricName("metric/name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricNameWithParenthesis() {
    PushMetricProcessor.checkMetricName("metric(name)");
  }

  @Test
  public void testCheckMetricLabelNameValid() {
    // Test valid label names - should not throw exceptions
    PushMetricProcessor.checkMetricLabelName("valid_label");
    PushMetricProcessor.checkMetricLabelName("valid_label_123");
    PushMetricProcessor.checkMetricLabelName("_valid_label");
    PushMetricProcessor.checkMetricLabelName("Valid_Label");
    PushMetricProcessor.checkMetricLabelName("a");
    PushMetricProcessor.checkMetricLabelName("_");
    PushMetricProcessor.checkMetricLabelName("label_with_numbers_456");
    PushMetricProcessor.checkMetricLabelName("labelname123");

    assertTrue("Valid label names should pass validation", true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameInvalidStartsWithNumber() {
    PushMetricProcessor.checkMetricLabelName("123invalid");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameInvalidContainsColon() {
    PushMetricProcessor.checkMetricLabelName("invalid:label");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameReserved() {
    PushMetricProcessor.checkMetricLabelName("__reserved");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameReservedWithSuffix() {
    PushMetricProcessor.checkMetricLabelName("__reserved_label");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameInvalidContainsSpace() {
    PushMetricProcessor.checkMetricLabelName("invalid label");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameInvalidContainsDash() {
    PushMetricProcessor.checkMetricLabelName("invalid-label");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameEmpty() {
    PushMetricProcessor.checkMetricLabelName("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameWithSpecialChars() {
    PushMetricProcessor.checkMetricLabelName("label@name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameWithDot() {
    PushMetricProcessor.checkMetricLabelName("label.name");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameReservedDoubleUnderscore() {
    PushMetricProcessor.checkMetricLabelName("__");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckMetricLabelNameReservedLong() {
    PushMetricProcessor.checkMetricLabelName("__prometheus_internal_label");
  }

  @Test
  public void testMetricNameRegexPatterns() {
    // Test edge cases for metric name validation
    PushMetricProcessor.checkMetricName("a1");
    PushMetricProcessor.checkMetricName("_1");
    PushMetricProcessor.checkMetricName("A_b_C_1_2_3");
    PushMetricProcessor.checkMetricName("metric_name_with_many_underscores___");
    PushMetricProcessor.checkMetricName("UPPERCASE_METRIC");

    assertTrue("Edge case metric names should be valid", true);
  }

  @Test
  public void testMetricLabelNameRegexPatterns() {
    // Test edge cases for label name validation
    PushMetricProcessor.checkMetricLabelName("a1");
    PushMetricProcessor.checkMetricLabelName("_1");
    PushMetricProcessor.checkMetricLabelName("A_b_C_1_2_3");
    PushMetricProcessor.checkMetricLabelName("label_name_with_many_underscores___");
    PushMetricProcessor.checkMetricLabelName("UPPERCASE_LABEL");

    assertTrue("Edge case label names should be valid", true);
  }

  @Test
  public void testStaticPropertyDescriptors() {
    // Test that static property descriptor fields are accessible
    assertNotNull("PUSHGATEWAY_HOSTNAME should not be null",
        PushMetricProcessor.PUSHGATEWAY_HOSTNAME);
    assertNotNull("PUSHGATEWAY_PORT should not be null",
        PushMetricProcessor.PUSHGATEWAY_PORT);
    assertNotNull("INSTANCE should not be null",
        PushMetricProcessor.INSTANCE);
    assertNotNull("JOB_NAME should not be null",
        PushMetricProcessor.JOB_NAME);

    // Test property names
    assertEquals("Pushgateway hostname",
        PushMetricProcessor.PUSHGATEWAY_HOSTNAME.getName());
    assertEquals("Pushgateway port",
        PushMetricProcessor.PUSHGATEWAY_PORT.getName());
    assertEquals("Instance name",
        PushMetricProcessor.INSTANCE.getName());
    assertEquals("Job Name",
        PushMetricProcessor.JOB_NAME.getName());
  }

  @Test
  public void testStaticRelationships() {
    // Test static relationship fields
    assertNotNull("REL_SUCCESS should not be null",
        PushMetricProcessor.REL_SUCCESS);
    assertNotNull("REL_FAILURE should not be null",
        PushMetricProcessor.REL_FAILURE);

    assertEquals("success", PushMetricProcessor.REL_SUCCESS.getName());
    assertEquals("failure", PushMetricProcessor.REL_FAILURE.getName());

    assertNotNull("REL_SUCCESS description",
        PushMetricProcessor.REL_SUCCESS.getDescription());
    assertNotNull("REL_FAILURE description",
        PushMetricProcessor.REL_FAILURE.getDescription());
  }

  @Test
  public void testPropertyDescriptorDetails() {
    // Test default values
    assertEquals("Default port should be 9091", "9091",
        PushMetricProcessor.PUSHGATEWAY_PORT.getDefaultValue());
    assertEquals("Default job name should be global", "global",
        PushMetricProcessor.JOB_NAME.getDefaultValue());
    assertEquals("Default instance should use hostname expression", "${hostname(true)}",
        PushMetricProcessor.INSTANCE.getDefaultValue());

    // Test required fields
    assertTrue("Pushgateway hostname should be required",
        PushMetricProcessor.PUSHGATEWAY_HOSTNAME.isRequired());
    assertTrue("Pushgateway port should be required",
        PushMetricProcessor.PUSHGATEWAY_PORT.isRequired());
    assertTrue("Instance should be required",
        PushMetricProcessor.INSTANCE.isRequired());
    assertTrue("Job name should be required",
        PushMetricProcessor.JOB_NAME.isRequired());

    // Test descriptions are not null
    assertNotNull("PUSHGATEWAY_HOSTNAME description",
        PushMetricProcessor.PUSHGATEWAY_HOSTNAME.getDescription());
    assertNotNull("PUSHGATEWAY_PORT description",
        PushMetricProcessor.PUSHGATEWAY_PORT.getDescription());
    assertNotNull("INSTANCE description",
        PushMetricProcessor.INSTANCE.getDescription());
    assertNotNull("JOB_NAME description",
        PushMetricProcessor.JOB_NAME.getDescription());
  }
}
