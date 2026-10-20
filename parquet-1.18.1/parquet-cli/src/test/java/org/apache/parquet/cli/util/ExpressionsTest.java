/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Test;

public class ExpressionsTest {

  private static final Schema SCHEMA = new Schema.Parser()
      .parse("{\"type\": \"record\", \"name\": \"event\", \"fields\": ["
          + "{\"name\": \"id\", \"type\": \"int\"},"
          + "{\"name\": \"detail\", \"type\": [\"null\", {\"type\": \"record\", \"name\": \"detail\", \"fields\": ["
          + "  {\"name\": \"ip\", \"type\": [\"null\", \"string\"], \"default\": null},"
          + "  {\"name\": \"duration\", \"type\": \"long\"}]}], \"default\": null}]}");

  @Test
  public void testFilterColumnInOptionalRecord() {
    Schema projection = Expressions.filterSchema(SCHEMA, "detail.ip");

    Schema.Field detail = projection.getField("detail");
    assertThat(detail.schema().getType()).isEqualTo(Schema.Type.UNION);
    assertThat(detail.schema().getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
    assertThat(detail.schema().getTypes().get(1).getFields())
        .extracting(Schema.Field::name)
        .containsExactly("ip");
    assertThat(detail.hasDefaultValue()).isTrue();
  }

  @Test
  public void testSelectFromMissingOptionalRecord() {
    Schema projection = Expressions.filterSchema(SCHEMA, "detail.ip");
    Schema detailSchema = projection.getField("detail").schema().getTypes().get(1);

    GenericData.Record withDetail = new GenericData.Record(projection);
    GenericData.Record detail = new GenericData.Record(detailSchema);
    detail.put("ip", "10.0.0.1");
    withDetail.put("detail", detail);
    assertThat(Expressions.select(projection, withDetail, "detail.ip")).isEqualTo("10.0.0.1");

    GenericData.Record withoutDetail = new GenericData.Record(projection);
    assertThat(Expressions.select(projection, withoutDetail, "detail.ip")).isNull();
  }

  @Test
  public void testSelectSchema() {
    assertThat(Expressions.selectSchema(SCHEMA, "id")).isEqualTo(Schema.create(Schema.Type.INT));
    assertThat(Expressions.selectSchema(SCHEMA, "detail.duration")).isEqualTo(Schema.create(Schema.Type.LONG));
    assertThat(Expressions.selectSchema(SCHEMA, "detail.ip"))
        .isEqualTo(SCHEMA.getField("detail")
            .schema()
            .getTypes()
            .get(1)
            .getField("ip")
            .schema());
    assertThat(Expressions.selectSchema(SCHEMA, "missing")).isNull();
  }
}
