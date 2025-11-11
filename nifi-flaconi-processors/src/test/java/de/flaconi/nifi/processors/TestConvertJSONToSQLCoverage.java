package de.flaconi.nifi.processors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Types;
import org.junit.Test;

/**
 * Additional test coverage for ConvertJSONToSQL static methods
 */
public class TestConvertJSONToSQLCoverage {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testCreateSqlStringValueWithVariousTypes() throws IOException {
        // Test different SQL types and JSON values
        testSqlValue("42", Types.INTEGER, "42");
        testSqlValue("true", Types.BOOLEAN, "true");
        testSqlValue("false", Types.BOOLEAN, "false");
        testSqlValue("3.14", Types.DOUBLE, "3.14");
        testSqlValue("\"test\"", Types.VARCHAR, "test");
        testSqlValue("null", Types.VARCHAR, "null");
    }

    @Test
    public void testCreateSqlStringValueWithDifferentDataTypes() throws IOException {
        testSqlValue("123", Types.BIGINT, "123");
        testSqlValue("456", Types.SMALLINT, "456");
        testSqlValue("789", Types.TINYINT, "789");
        testSqlValue("1.5", Types.REAL, "1.5");
        testSqlValue("999.99", Types.DECIMAL, "999.99");
        testSqlValue("888.88", Types.NUMERIC, "888.88");
        testSqlValue("1", Types.BIT, "1");
    }

    @Test
    public void testCreateSqlStringValueWithStringTypes() throws IOException {
        testSqlValue("\"A\"", Types.CHAR, "A");
        testSqlValue("\"Long text\"", Types.LONGVARCHAR, "Long text");
        testSqlValue("\"2024-01-15\"", Types.DATE, "2024-01-15");
        testSqlValue("\"14:30:45\"", Types.TIME, "14:30:45");
    }

    @Test
    public void testCreateSqlStringValueWithArrayAndObject() throws IOException {
        testSqlValue("[1,2,3]", Types.ARRAY, "");
        testSqlValue("{\"key\":\"value\"}", Types.OTHER, "");
    }

    @Test
    public void testCreateSqlStringValueWithTimestamp() throws IOException {
        // Test the special date parsing logic
        String payload = "{\"created_at\":\"Fri Aug 17 10:10:10 UTC 2018\"}";
        JsonNode node = mapper.readTree(payload);
        String result = ConvertJSONToSQL.createSqlStringValue(node.get("created_at"), 26, Types.TIMESTAMP);
        assertThat(result, is("2018-08-17 10:10:10.000"));
    }

    @Test
    public void testCreateSqlStringValueWithDifferentDateFormats() throws IOException {
        // Test various date formats - some are converted, some return original string
        testDateConversion("Mon Jan 01 00:00:00 UTC 2024", "2024-01-01 00:00:00.000");
        testDateConversion("Wed Dec 25 15:30:45 UTC 2025", "Wed Dec 25 15:30:45 UTC 2025");
        testDateConversion("Sun Feb 29 12:00:00 UTC 2020", "Sun Feb 29 12:00:00 UTC 2020");
    }

    @Test
    public void testCreateSqlStringValueWithNullInput() throws IOException {
        JsonNode nullNode = mapper.readTree("null");
        String result = ConvertJSONToSQL.createSqlStringValue(nullNode, 100, Types.VARCHAR);
        assertThat(result, is("null"));
    }

    @Test
    public void testCreateSqlStringValueWithEmptyString() throws IOException {
        JsonNode emptyNode = mapper.readTree("\"\"");
        String result = ConvertJSONToSQL.createSqlStringValue(emptyNode, 100, Types.VARCHAR);
        assertThat(result, is(""));
    }

    @Test
    public void testCreateSqlStringValueWithZeroAndNegative() throws IOException {
        testSqlValue("0", Types.INTEGER, "0");
        testSqlValue("-123", Types.INTEGER, "-123");
        testSqlValue("-45.67", Types.DOUBLE, "-45.67");
    }

    @Test
    public void testCreateSqlStringValueWithLargeNumbers() throws IOException {
        testSqlValue("9223372036854775807", Types.BIGINT, "9223372036854775807");
        testSqlValue("1.7976931348623157E+308", Types.DOUBLE, "1.7976931348623157E308");
    }

    @Test
    public void testCreateSqlStringValueWithSpecialStringCharacters() throws IOException {
        // Test strings with special characters
        testSqlValue("\"text with spaces\"", Types.VARCHAR, "text with spaces");
        testSqlValue("\"text\\nwith\\nnewlines\"", Types.VARCHAR, "text\nwith\nnewlines");
        testSqlValue("\"text\\twith\\ttabs\"", Types.VARCHAR, "text\twith\ttabs");
        testSqlValue("\"text\\\"with\\\"quotes\"", Types.VARCHAR, "text\"with\"quotes");
    }

    @Test
    public void testCreateSqlStringValueWithUnicode() throws IOException {
        testSqlValue("\"Hello 世界\"", Types.VARCHAR, "Hello 世界");
        testSqlValue("\"Café\"", Types.VARCHAR, "Café");
        testSqlValue("\"🎉\"", Types.VARCHAR, "🎉");
    }

    @Test
    public void testCreateSqlStringValueWithComplexArrays() throws IOException {
        testSqlValue("[\"a\",\"b\",\"c\"]", Types.ARRAY, "");
        testSqlValue("[1,2.5,true,null]", Types.ARRAY, "");
        testSqlValue("[]", Types.ARRAY, "");
    }

    @Test
    public void testCreateSqlStringValueWithComplexObjects() throws IOException {
        testSqlValue("{}", Types.OTHER, "");
        testSqlValue("{\"a\":1,\"b\":true}", Types.OTHER, "");
        testSqlValue("{\"nested\":{\"value\":42}}", Types.OTHER, "");
    }

    @Test
    public void testProcessorConstantsExist() {
        // Test that important constants are defined
        assertNotNull(ConvertJSONToSQL.REL_ORIGINAL);
        assertNotNull(ConvertJSONToSQL.REL_SQL);
        assertNotNull(ConvertJSONToSQL.REL_FAILURE);
        assertNotNull(ConvertJSONToSQL.CONNECTION_POOL);
        assertNotNull(ConvertJSONToSQL.TABLE_NAME);
        assertNotNull(ConvertJSONToSQL.STATEMENT_TYPE);
    }

    // Helper methods
    private void testSqlValue(String jsonValue, int sqlType, String expectedResult) throws IOException {
        JsonNode node = mapper.readTree(jsonValue);
        String result = ConvertJSONToSQL.createSqlStringValue(node, 100, sqlType);
        assertThat(result, is(expectedResult));
    }

    private void testDateConversion(String dateString, String expectedResult) throws IOException {
        String payload = "{\"date\":\"" + dateString + "\"}";
        JsonNode node = mapper.readTree(payload);
        String result = ConvertJSONToSQL.createSqlStringValue(node.get("date"), 26, Types.TIMESTAMP);
        assertThat(result, is(expectedResult));
    }
}
