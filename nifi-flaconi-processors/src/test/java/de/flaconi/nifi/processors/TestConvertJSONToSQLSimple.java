package de.flaconi.nifi.processors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

public class TestConvertJSONToSQLSimple {

  private TestRunner testRunner;

  @Before
  public void setup() {
    testRunner = TestRunners.newTestRunner(ConvertJSONToSQL.class);
  }

  @Test
  public void testProcessorInstantiation() {
    // Test that processor can be instantiated
    assertNotNull(testRunner.getProcessor());
    assertTrue(testRunner.getProcessor() instanceof ConvertJSONToSQL);
  }

  @Test
  public void testProcessorHasRequiredRelationships() {
    // Test that processor has the expected relationships
    assertEquals("Expected exactly 3 relationships", 3,
        testRunner.getProcessor().getRelationships().size());
  }

  @Test
  public void testProcessorHasPropertyDescriptors() {
    // Test that processor has property descriptors
    List<org.apache.nifi.components.PropertyDescriptor> descriptors = testRunner.getProcessor()
        .getPropertyDescriptors();
    assertNotNull("Property descriptors should not be null", descriptors);
    assertTrue("Should have multiple property descriptors", descriptors.size() > 5);
  }

  @Test
  public void testTableNameProperty() {
    // Test setting table name property
    testRunner.setProperty("Table Name", "test_table");
    assertEquals("test_table", testRunner.getProcessContext().getProperty("Table Name").getValue());
  }

  @Test
  public void testStatementTypeProperty() {
    // Test setting statement type property
    testRunner.setProperty("Statement Type", "INSERT");
    assertEquals("INSERT", testRunner.getProcessContext().getProperty("Statement Type").getValue());
  }

  @Test
  public void testTableNameWithExpressionLanguage() {
    // Test table name with expression language
    testRunner.setValidateExpressionUsage(false);
    testRunner.setProperty("Table Name", "${table.name}");
    assertEquals("${table.name}",
        testRunner.getProcessContext().getProperty("Table Name").getValue());
  }

  @Test
  public void testStatementTypeUpdate() {
    // Test UPDATE statement type
    testRunner.setProperty("Statement Type", "UPDATE");
    assertEquals("UPDATE", testRunner.getProcessContext().getProperty("Statement Type").getValue());
  }

  @Test
  public void testStatementTypeDelete() {
    // Test DELETE statement type
    testRunner.setProperty("Statement Type", "DELETE");
    assertEquals("DELETE", testRunner.getProcessContext().getProperty("Statement Type").getValue());
  }

  @Test
  public void testUpdateKeysProperty() {
    // Test setting update keys property
    testRunner.setProperty("Update Keys", "id,name");
    assertEquals("id,name", testRunner.getProcessContext().getProperty("Update Keys").getValue());
  }

  @Test
  public void testMultiplePropertyConfiguration() {
    // Test setting multiple properties together
    testRunner.setProperty("Table Name", "users");
    testRunner.setProperty("Statement Type", "INSERT");
    testRunner.setProperty("Update Keys", "user_id");

    assertEquals("users", testRunner.getProcessContext().getProperty("Table Name").getValue());
    assertEquals("INSERT", testRunner.getProcessContext().getProperty("Statement Type").getValue());
    assertEquals("user_id", testRunner.getProcessContext().getProperty("Update Keys").getValue());
  }

  @Test
  public void testProcessorToString() {
    // Test that processor toString doesn't throw exceptions
    String processorString = testRunner.getProcessor().toString();
    assertNotNull("toString should not return null", processorString);
    assertTrue("toString should contain class name",
        processorString.contains("ConvertJSONToSQL") || processorString.contains("Processor"));
  }

  @Test
  public void testProcessorIdentifier() {
    // Test processor class identification
    String className = testRunner.getProcessor().getClass().getSimpleName();
    assertEquals("ConvertJSONToSQL", className);
  }

  @Test
  public void testPropertyDescriptorNames() {
    // Test that property descriptors have valid names
    List<org.apache.nifi.components.PropertyDescriptor> descriptors = testRunner.getProcessor()
        .getPropertyDescriptors();

    for (org.apache.nifi.components.PropertyDescriptor descriptor : descriptors) {
      assertNotNull("Property name should not be null", descriptor.getName());
      assertTrue("Property name should not be empty", descriptor.getName().length() > 0);
      assertNotNull("Display name should not be null", descriptor.getDisplayName());
    }
  }

  @Test
  public void testExpressionLanguageSupport() {
    // Test that properties support expression language where expected
    testRunner.setValidateExpressionUsage(false);
    testRunner.setProperty("Table Name", "${env.table}");
    testRunner.setProperty("Statement Type", "${sql.operation}");

    // Should not throw exceptions when setting EL values
    assertEquals("${env.table}",
        testRunner.getProcessContext().getProperty("Table Name").getValue());
    assertEquals("${sql.operation}",
        testRunner.getProcessContext().getProperty("Statement Type").getValue());
  }

  @Test
  public void testBasicProcessorConfiguration() {
    // Test basic processor configuration without running
    testRunner.setProperty("Table Name", "test_table");
    testRunner.setProperty("Statement Type", "INSERT");

    // Verify processor accepts configuration
    assertNotNull(testRunner.getProcessContext());
    assertNotNull(testRunner.getProcessContext().getProperty("Table Name"));
    assertNotNull(testRunner.getProcessContext().getProperty("Statement Type"));
  }
}
