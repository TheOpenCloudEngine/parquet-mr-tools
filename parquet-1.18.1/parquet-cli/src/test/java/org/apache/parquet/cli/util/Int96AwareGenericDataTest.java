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
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.jupiter.api.Test;

public class Int96AwareGenericDataTest {

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
    MessageType schema = MessageTypeParser.parseMessageType(parquetSchema);
    return new AvroSchemaConverter(conf).convert(schema);
  }

  @Test
  public void testToInstant() {
    assertThat(Int96AwareGenericData.toInstant(int96(NANOS_OF_DAY, JULIAN_DAY)))
        .isEqualTo(Instant.parse("2024-01-01T09:30:00.123456789Z"));
    assertThat(Int96AwareGenericData.toInstant(int96(0, 2440588))).isEqualTo(Instant.EPOCH);
  }

  @Test
  public void testRecordToString() {
    Schema schema =
        convert("message m { required int32 id; required int96 ts; required fixed_len_byte_array(12) f; }");
    assertThat(Int96AwareGenericData.isInt96(schema.getField("ts").schema()))
        .isTrue();
    assertThat(Int96AwareGenericData.isInt96(schema.getField("f").schema())).isFalse();

    GenericData.Record record = new GenericData.Record(schema);
    record.put("id", 1);
    record.put("ts", new GenericData.Fixed(schema.getField("ts").schema(), int96(NANOS_OF_DAY, JULIAN_DAY)));
    record.put("f", new GenericData.Fixed(schema.getField("f").schema(), new byte[12]));

    assertThat(Int96AwareGenericData.get().toString(record))
        .isEqualTo("{\"id\": 1, \"ts\": \"2024-01-01T09:30:00.123456789Z\", "
            + "\"f\": [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]}");
  }

  @Test
  public void testNestedAndNullValues() {
    Schema schema = convert("message m { optional group g { optional int96 ts; } optional int96 missing; }");
    Schema groupSchema = schema.getField("g").schema().getTypes().get(1);
    Schema tsSchema = groupSchema.getField("ts").schema().getTypes().get(1);

    GenericData.Record group = new GenericData.Record(groupSchema);
    group.put("ts", new GenericData.Fixed(tsSchema, int96(NANOS_OF_DAY, JULIAN_DAY)));
    GenericData.Record record = new GenericData.Record(schema);
    record.put("g", group);

    assertThat(Int96AwareGenericData.get().toString(record))
        .isEqualTo("{\"g\": {\"ts\": \"2024-01-01T09:30:00.123456789Z\"}, \"missing\": null}");
    assertThat(Int96AwareGenericData.get().toString(null)).isEqualTo("null");
  }
}
