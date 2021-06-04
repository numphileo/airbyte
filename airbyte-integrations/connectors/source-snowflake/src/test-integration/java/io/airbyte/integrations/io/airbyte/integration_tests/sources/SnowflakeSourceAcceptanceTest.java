/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.integrations.io.airbyte.integration_tests.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import io.airbyte.commons.io.IOs;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.resources.MoreResources;
import io.airbyte.db.Databases;
import io.airbyte.db.jdbc.JdbcDatabase;
import io.airbyte.integrations.source.snowflake.SnowflakeSource;
import io.airbyte.integrations.standardtest.source.SourceAcceptanceTest;
import io.airbyte.protocol.models.CatalogHelpers;
import io.airbyte.protocol.models.ConfiguredAirbyteCatalog;
import io.airbyte.protocol.models.ConfiguredAirbyteStream;
import io.airbyte.protocol.models.ConnectorSpecification;
import io.airbyte.protocol.models.DestinationSyncMode;
import io.airbyte.protocol.models.Field;
import io.airbyte.protocol.models.Field.JsonSchemaPrimitive;
import io.airbyte.protocol.models.SyncMode;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SnowflakeSourceAcceptanceTest extends SourceAcceptanceTest {

  private static final String SCHEMA_NAME = "SOURCE_INTEGRATION_TEST";
  private static final String STREAM_NAME1 = "ID_AND_NAME1";
  private static final String STREAM_NAME2 = "ID_AND_NAME2";

  // config which refers to the schema that the test is being run in.
  private JsonNode config;
  private JdbcDatabase database;

  @Override
  protected String getImageName() {
    return "airbyte/source-snowflake:dev";
  }

  @Override
  protected ConnectorSpecification getSpec() throws Exception {
    return Jsons.deserialize(MoreResources.readResource("spec.json"), ConnectorSpecification.class);
  }

  @Override
  protected JsonNode getConfig() {
    return config;
  }

  JsonNode getStaticConfig() {
    return Jsons
        .deserialize(IOs.readFile(Path.of("secrets/config.json")));
  }

  @Override
  protected ConfiguredAirbyteCatalog getConfiguredCatalog() throws Exception {
    return new ConfiguredAirbyteCatalog().withStreams(Lists.newArrayList(
        new ConfiguredAirbyteStream()
            .withSyncMode(SyncMode.INCREMENTAL)
            .withCursorField(Lists.newArrayList("ID"))
            .withDestinationSyncMode(DestinationSyncMode.APPEND)
            .withStream(CatalogHelpers.createAirbyteStream(
                String.format("%s.%s", SCHEMA_NAME, STREAM_NAME1),
                Field.of("ID", JsonSchemaPrimitive.NUMBER),
                Field.of("NAME", JsonSchemaPrimitive.STRING))
                .withSupportedSyncModes(
                    Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))),
        new ConfiguredAirbyteStream()
            .withSyncMode(SyncMode.FULL_REFRESH)
            .withDestinationSyncMode(DestinationSyncMode.OVERWRITE)
            .withStream(CatalogHelpers.createAirbyteStream(
                String.format("%s.%s", SCHEMA_NAME, STREAM_NAME2),
                Field.of("ID", JsonSchemaPrimitive.NUMBER),
                Field.of("NAME", JsonSchemaPrimitive.STRING))
                .withSupportedSyncModes(
                    Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL)))));
  }

  @Override
  protected JsonNode getState() throws Exception {
    return Jsons.jsonNode(new HashMap<>());
  }

  @Override
  protected List<String> getRegexTests() throws Exception {
    return Collections.emptyList();
  }

  // for each test we create a new schema in the database. run the test in there and then remove it.
  @Override
  protected void setup(TestDestinationEnv testEnv) throws Exception {
    config = Jsons.clone(getStaticConfig());
    database = Databases.createJdbcDatabase(
        config.get("username").asText(),
        config.get("password").asText(),
        String.format("jdbc:snowflake://%s/",
            config.get("host").asText()),
        SnowflakeSource.DRIVER_CLASS,
        String.format("role=%s;warehouse=%s;database=%s",
            config.get("role").asText(),
            config.get("warehouse").asText(),
            config.get("database").asText()));

    final String createSchemaQuery = String.format("CREATE SCHEMA %s", SCHEMA_NAME);
    final String createTableQuery1 = String
        .format("CREATE OR REPLACE TABLE %s.%s (ID INTEGER, NAME VARCHAR(200))", SCHEMA_NAME,
            STREAM_NAME1);
    final String createTableQuery2 = String
        .format("CREATE OR REPLACE TABLE %s.%s (ID INTEGER, NAME VARCHAR(200))", SCHEMA_NAME,
            STREAM_NAME2);
    final String insertIntoTableQuery1 = String
        .format("INSERT INTO %s.%s (ID, NAME) VALUES (1,'picard'),  (2, 'crusher'), (3, 'vash')",
            SCHEMA_NAME, STREAM_NAME1);
    final String insertIntoTableQuery2 = String
        .format("INSERT INTO %s.%s (ID, NAME) VALUES (1,'picard'),  (2, 'crusher'), (3, 'vash')",
            SCHEMA_NAME, STREAM_NAME2);

    database.execute(createSchemaQuery);
    database.execute(createTableQuery1);
    database.execute(createTableQuery2);
    database.execute(insertIntoTableQuery1);
    database.execute(insertIntoTableQuery2);
  }

  @Override
  protected void tearDown(TestDestinationEnv testEnv) throws Exception {
    final String dropSchemaQuery = String
        .format("DROP SCHEMA IF EXISTS %s", SCHEMA_NAME);
    database.execute(dropSchemaQuery);
    database.close();
  }

}
