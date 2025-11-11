package de.flaconi.nifi.processors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Types;
import org.junit.Test;

public class TestConvertJSONToSQL {

  @Test
  public void testCreateSqlStringValueDateFormat() throws IOException {
    String payload = "{\"created_at\":\"Fri Aug 17 10:10:10 UTC 2018\"}";
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(payload);

    String value = ConvertJSONToSQL.createSqlStringValue(node.get("created_at"), 26,
        Types.TIMESTAMP);

    assertThat(value, is("2018-08-17 10:10:10.000"));
  }
}
