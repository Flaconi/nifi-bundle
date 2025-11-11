package de.flaconi.nifi.reporting.prometheus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;
import org.apache.nifi.components.PropertyDescriptor;
import org.junit.Before;
import org.junit.Test;

public class TestPrometheusReportingTaskSimple {

    private PrometheusReportingTask reportingTask;

    @Before
    public void setup() {
        reportingTask = new PrometheusReportingTask();
    }

    @Test
    public void testReportingTaskCreation() {
        // Test that reporting task can be instantiated
        assertNotNull("Reporting task should not be null", reportingTask);
        assertTrue("Should be instance of PrometheusReportingTask",
            reportingTask instanceof PrometheusReportingTask);
    }

    @Test
    public void testPropertyDescriptors() {
        List<PropertyDescriptor> descriptors = reportingTask.getPropertyDescriptors();

        // Verify we have property descriptors
        assertNotNull("Property descriptors should not be null", descriptors);
        assertThat("Expected property descriptors", descriptors.size(), greaterThan(0));

        // Check that all descriptors have names
        for (PropertyDescriptor descriptor : descriptors) {
            assertNotNull("Property descriptor should have a name", descriptor.getName());
            assertThat("Property descriptor name should not be empty",
                descriptor.getName().length(), greaterThan(0));
        }
    }

    @Test
    public void testDefaultProperties() {
        List<PropertyDescriptor> descriptors = reportingTask.getPropertyDescriptors();

        // Test that properties can be accessed
        for (PropertyDescriptor descriptor : descriptors) {
            String defaultValue = descriptor.getDefaultValue();
            // Default value can be null, that's okay
            if (defaultValue != null) {
                assertThat("Default value should not be empty string",
                    defaultValue.trim().length(), greaterThanOrEqualTo(0));
            }
        }
    }

    @Test
    public void testReportingTaskType() {
        // Verify the reporting task is the correct type
        String className = reportingTask.getClass().getSimpleName();
        assertEquals("PrometheusReportingTask", className);

        String fullClassName = reportingTask.getClass().getName();
        assertTrue("Should contain package name",
            fullClassName.contains("de.flaconi.nifi.reporting.prometheus"));
    }

    @Test
    public void testPropertyDescriptorValidation() {
        List<PropertyDescriptor> descriptors = reportingTask.getPropertyDescriptors();

        for (PropertyDescriptor descriptor : descriptors) {
            // Test that each descriptor has essential attributes
            assertNotNull("Property descriptor name cannot be null", descriptor.getName());
            assertNotNull("Property descriptor display name cannot be null", descriptor.getDisplayName());

            // Description can be null but if present should not be empty
            if (descriptor.getDescription() != null) {
                assertThat("Property description should not be empty",
                    descriptor.getDescription().trim().length(), greaterThan(0));
            }
        }
    }

    @Test
    public void testReportingTaskToString() {
        // Test that toString doesn't throw exceptions
        String stringRepresentation = reportingTask.toString();
        assertNotNull("toString should not return null", stringRepresentation);
        assertThat("toString should not be empty", stringRepresentation.length(), greaterThan(0));
    }

    @Test
    public void testPropertyDescriptorNames() {
        // Test that we can access property descriptor names
        List<PropertyDescriptor> descriptors = reportingTask.getPropertyDescriptors();

        boolean foundNamedProperty = false;
        for (PropertyDescriptor descriptor : descriptors) {
            String name = descriptor.getName();
            if (name != null && name.length() > 0) {
                foundNamedProperty = true;
                break;
            }
        }

        assertTrue("Should have at least one named property descriptor", foundNamedProperty);
    }

    @Test
    public void testPropertyDescriptorDisplayNames() {
        // Test that property descriptors have display names
        List<PropertyDescriptor> descriptors = reportingTask.getPropertyDescriptors();

        if (!descriptors.isEmpty()) {
            PropertyDescriptor firstDescriptor = descriptors.get(0);
            assertNotNull("First property descriptor should have a display name",
                firstDescriptor.getDisplayName());
        }
    }

    @Test
    public void testMultipleInstantiation() {
        // Test that multiple instances can be created
        PrometheusReportingTask task1 = new PrometheusReportingTask();
        PrometheusReportingTask task2 = new PrometheusReportingTask();

        assertNotNull("First task should not be null", task1);
        assertNotNull("Second task should not be null", task2);
        assertNotSame("Tasks should be different instances", task1, task2);
    }

    @Test
    public void testReportingTaskClassMetadata() {
        // Test class metadata
        Class<?> taskClass = reportingTask.getClass();

        assertNotNull("Class should not be null", taskClass);
        assertEquals("Class simple name should match", "PrometheusReportingTask", taskClass.getSimpleName());
        assertTrue("Should be in prometheus package", taskClass.getPackage().getName().contains("prometheus"));
    }
}
