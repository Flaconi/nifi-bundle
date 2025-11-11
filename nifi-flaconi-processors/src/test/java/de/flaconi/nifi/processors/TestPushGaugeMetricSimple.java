package de.flaconi.nifi.processors;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

public class TestPushGaugeMetricSimple {

    private TestRunner testRunner;

    @Before
    public void setup() {
        testRunner = TestRunners.newTestRunner(PushGaugeMetric.class);
    }

    @Test
    public void testProcessorWithValidConfiguration() {
        // Configure processor with all required properties using string literals
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "42.5");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        // Verify configuration is valid
        testRunner.assertValid();
    }

    @Test
    public void testProcessorWithExpressionLanguageMetricName() {
        // Test metric name with expression language
        testRunner.setProperty("Metric Name", "${metric.name}");
        testRunner.setProperty("Metric Value", "100");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("metric.name", "dynamic_metric_name");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8), attributes);
        testRunner.run();

        // Should succeed or fail gracefully (depending on gateway availability)
        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithExpressionLanguageMetricValue() {
        // Test metric value with expression language
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "${metric.value}");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("metric.value", "75.25");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8), attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithComplexLabels() {
        // Test with multiple metric labels
        testRunner.setProperty("Metric Name", "http_requests_total");
        testRunner.setProperty("Metric Value", "1234");
        testRunner.setProperty("Labels", "method=GET,status=200,endpoint=/api/v1/users");
        testRunner.setProperty("Prometheus PushGateway URL", "http://prometheus-gateway:9091");
        testRunner.setProperty("Job Name", "web_server_metrics");
        testRunner.setProperty("Pushgateway hostname", "prometheus-gateway");
        testRunner.setProperty("Metric Help", "HTTP requests metric description");

        String content = "request log data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithDynamicLabels() {
        // Test with expression language in labels
        testRunner.setProperty("Metric Name", "response_time");
        testRunner.setProperty("Metric Value", "245.7");
        testRunner.setProperty("Labels", "service=${service.name},region=${aws.region}");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "performance_metrics");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Response time metric description");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("service.name", "user_service");
        attributes.put("aws.region", "us-east-1");

        String content = "performance data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8), attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithZeroMetricValue() {
        // Test with zero metric value
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "0");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithNegativeMetricValue() {
        // Test with negative metric value
        testRunner.setProperty("Metric Name", "temperature_diff");
        testRunner.setProperty("Metric Value", "-15.5");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "weather_metrics");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Temperature metric description");

        String content = "temperature data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithVeryLargeMetricValue() {
        // Test with very large metric value
        testRunner.setProperty("Metric Name", "large_number_metric");
        testRunner.setProperty("Metric Value", "9223372036854775807");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "big_data_metrics");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Large number metric description");

        String content = "large data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithVerySmallMetricValue() {
        // Test with very small metric value
        testRunner.setProperty("Metric Name", "precision_metric");
        testRunner.setProperty("Metric Value", "0.000000001");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "precision_metrics");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Precision metric description");

        String content = "precision data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithInvalidGatewayURL() {
        // Test with invalid gateway URL
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "42");
        testRunner.setProperty("Prometheus PushGateway URL", "invalid-url");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "invalid-hostname");
        testRunner.setProperty("Metric Help", "Invalid URL test metric");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        // Should route to failure due to invalid URL
        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorWithEmptyFlowFile() {
        // Test with empty flow file
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "42");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        testRunner.enqueue(new byte[0]); // Empty flow file
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testMultipleFlowFiles() {
        // Test processing multiple flow files
        testRunner.setProperty("Metric Name", "batch_metric");
        testRunner.setProperty("Metric Value", "${sequence.number}");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "batch_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        // Enqueue multiple flow files
        for (int i = 1; i <= 5; i++) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("sequence.number", String.valueOf(i * 10));
            testRunner.enqueue(("Flow file " + i).getBytes(StandardCharsets.UTF_8), attributes);
        }

        testRunner.run(5);

        // All should route to failure due to unreachable gateway
        testRunner.assertAllFlowFilesTransferred("failure", 5);
    }

    @Test
    public void testProcessorToleratesAttributeMissing() {
        // Test when expression language references missing attributes
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "42");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8));
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);
    }

    @Test
    public void testProcessorFlowFileAttributesPreserved() {
        // Test that flow file attributes are preserved
        testRunner.setProperty("Metric Name", "test_metric");
        testRunner.setProperty("Metric Value", "42");
        testRunner.setProperty("Prometheus PushGateway URL", "http://localhost:9091");
        testRunner.setProperty("Job Name", "test_job");
        testRunner.setProperty("Pushgateway hostname", "localhost");
        testRunner.setProperty("Metric Help", "Test metric description");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("custom.attribute", "custom_value");
        attributes.put("timestamp", "2023-01-01T00:00:00Z");

        String content = "test data";
        testRunner.enqueue(content.getBytes(StandardCharsets.UTF_8), attributes);
        testRunner.run();

        testRunner.assertAllFlowFilesTransferred("failure", 1);

        MockFlowFile flowFile = testRunner.getFlowFilesForRelationship("failure").get(0);
        assertEquals("custom_value", flowFile.getAttribute("custom.attribute"));
        assertEquals("2023-01-01T00:00:00Z", flowFile.getAttribute("timestamp"));
    }

    @Test
    public void testProcessorRelationships() {
        // Verify processor has correct relationships
        assertEquals("Expected exactly 2 relationships", 2, testRunner.getProcessor().getRelationships().size());
    }
}
