package de.flaconi.nifi.processors;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.sql.Types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestConvertJSONToSQL {

    @Test
    public void testCreateSqlStringValueDateFormat() throws IOException {
      String payload = "{\"created_at\":\"Fri Aug 17 10:10:10 UTC 2018\"}";
      ObjectMapper mapper = new ObjectMapper();
      JsonNode node = mapper.readTree(payload);

      String value = ConvertJSONToSQL.createSqlStringValue(node.get("created_at"), 26, Types.TIMESTAMP);

      assertThat(value, is("2018-08-17 10:10:10.000"));
    }
}
