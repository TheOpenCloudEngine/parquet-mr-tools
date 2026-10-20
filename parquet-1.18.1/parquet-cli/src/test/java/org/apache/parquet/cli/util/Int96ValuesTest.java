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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.jupiter.api.Test;

public class Int96ValuesTest {

  // 2024-01-01T09:30:00.123456789Z
  private static final int JULIAN_DAY = 2460311;
  private static final long NANOS_OF_DAY = 34200123456789L;

  private static byte[] int96(long nanosOfDay, int julianDay) {
    return ByteBuffer.allocate(12)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(nanosOfDay)
        .putInt(julianDay)
        .array();
  }

  private static Schema convert(String parquetSchema) {
    Configuration conf = new Configuration();
    conf.setBoolean(AvroReadSupport.READ_INT96_AS_FIXED, true);
    return new AvroSchemaConverter(conf).convert(MessageTypeParser.parseMessageType(parquetSchema));
  }

  @Test
  public void testIsInt96() {
    Schema schema = convert("message m { required int96 ts; required fixed_len_byte_array(12) f; }");
    assertThat(Int96Values.isInt96(schema.getField("ts").schema())).isTrue();
    assertThat(Int96Values.isInt96(schema.getField("f").schema())).isFalse();
  }

  @Test
  public void testToInstant() {
    assertThat(Int96Values.toInstant(int96(NANOS_OF_DAY, JULIAN_DAY)))
        .isEqualTo(Instant.parse("2024-01-01T09:30:00.123456789Z"));
    assertThat(Int96Values.toInstant(int96(0, 2440588))).isEqualTo(Instant.EPOCH);
    assertThat(Int96Values.toInstant(int96(86_399_999_999_999L, 2440587)))
        .isEqualTo(Instant.parse("1969-12-31T23:59:59.999999999Z"));
  }

  @Test
  public void testToEpochMicros() {
    assertThat(Int96Values.toEpochMicros(int96(NANOS_OF_DAY, JULIAN_DAY))).isEqualTo(1704101400123456L);
    assertThat(Int96Values.toEpochMicros(int96(86_399_999_999_999L, 2440587)))
        .isEqualTo(-1L);
  }

  @Test
  public void testToTimestampSchemaAndDatum() {
    Schema schema = convert("message m { required int32 id; optional int96 ts; "
        + "optional group detail { optional int96 at; optional binary ip (STRING); } "
        + "required group times (LIST) { repeated int96 array; } }");
    Schema converted = Int96Values.toTimestampSchema(schema);

    Schema timestampMicros = LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
    assertThat(converted.getField("id").schema())
        .isEqualTo(schema.getField("id").schema());
    assertThat(converted.getField("ts").schema())
        .isEqualTo(Schema.createUnion(Schema.create(Schema.Type.NULL), timestampMicros));
    assertThat(converted.getField("ts").defaultVal())
        .isEqualTo(schema.getField("ts").defaultVal());
    assertThat(converted
            .getField("detail")
            .schema()
            .getTypes()
            .get(1)
            .getField("at")
            .schema())
        .isEqualTo(Schema.createUnion(Schema.create(Schema.Type.NULL), timestampMicros));
    assertThat(converted.getField("times").schema().getElementType()).isEqualTo(timestampMicros);

    Schema detailSchema = schema.getField("detail").schema().getTypes().get(1);
    GenericData.Record detail = new GenericData.Record(detailSchema);
    detail.put(
        "at",
        new GenericData.Fixed(
            detailSchema.getField("at").schema().getTypes().get(1), int96(0, 2440589)));
    detail.put("ip", "10.0.0.1");
    GenericData.Record record = new GenericData.Record(schema);
    record.put("id", 1);
    record.put("ts", null);
    record.put("detail", detail);
    record.put(
        "times",
        Arrays.asList(new GenericData.Fixed(
            schema.getField("times").schema().getElementType(), int96(NANOS_OF_DAY, JULIAN_DAY))));

    GenericRecord result = (GenericRecord) Int96Values.toTimestampDatum(record, schema, converted);
    assertThat(GenericData.get().validate(converted, result)).isTrue();
    assertThat(result.get("id")).isEqualTo(1);
    assertThat(result.get("ts")).isNull();
    assertThat(((GenericRecord) result.get("detail")).get("at")).isEqualTo(86_400_000_000L);
    assertThat(((GenericRecord) result.get("detail")).get("ip")).isEqualTo("10.0.0.1");
    assertThat(result.get("times")).asList().containsExactly(1704101400123456L);
  }

  @Test
  public void testRecordSchemaReferencedTwice() {
    Schema inner = new Schema.Parser()
        .parse(
            "{\"type\": \"record\", \"name\": \"inner\", \"fields\": [{\"name\": \"v\", \"type\": \"int\"}]}");
    Schema outer = Schema.createRecord(
        "outer", null, null, false, Arrays.asList(new Schema.Field("a", inner), new Schema.Field("b", inner)));

    Schema converted = Int96Values.toTimestampSchema(outer);
    assertThat(converted.getField("a").schema())
        .isSameAs(converted.getField("b").schema());
    // toString fails if the same name is defined twice
    assertThat(new Schema.Parser().parse(converted.toString())).isEqualTo(outer);
  }
}
