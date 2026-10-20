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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.jupiter.api.Test;

public class RecordFormatterTest {

  private static final RecordFormatter UTC = new RecordFormatter(null);

  // 2024-01-01T09:30:00.123456789Z
  private static final long EPOCH_DAY = 19723L;
  private static final long NANOS_OF_DAY = 34200123456789L;
  private static final long EPOCH_SECOND = EPOCH_DAY * 86400L + NANOS_OF_DAY / 1_000_000_000L;

  private static Schema parse(String json) {
    return new Schema.Parser().parse(json);
  }

  private static Schema logical(String type, String logicalType) {
    return parse("{\"type\": \"" + type + "\", \"logicalType\": \"" + logicalType + "\"}");
  }

  private static byte[] int96(long nanosOfDay, long epochDay) {
    return ByteBuffer.allocate(12)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(nanosOfDay)
        .putInt((int) (epochDay + 2440588L))
        .array();
  }

  @Test
  public void testPlainRecordMatchesAvroToString() {
    Schema schema = parse("{\"type\": \"record\", \"name\": \"r\", \"fields\": ["
        + "{\"name\": \"id\", \"type\": \"int\"},"
        + "{\"name\": \"name\", \"type\": [\"null\", \"string\"]},"
        + "{\"name\": \"score\", \"type\": \"double\"},"
        + "{\"name\": \"tags\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},"
        + "{\"name\": \"attrs\", \"type\": {\"type\": \"map\", \"values\": \"long\"}}]}");
    Map<Utf8, Long> attrs = new LinkedHashMap<>();
    attrs.put(new Utf8("a"), 1L);
    GenericData.Record record = new GenericData.Record(schema);
    record.put("id", 1);
    record.put("name", new Utf8("Alice \"K\""));
    record.put("score", 91.5);
    record.put("tags", Arrays.asList(new Utf8("x"), new Utf8("y")));
    record.put("attrs", attrs);

    assertThat(UTC.format(record, schema)).isEqualTo(GenericData.get().toString(record));

    record.put("name", null);
    assertThat(UTC.format(record, schema)).isEqualTo(GenericData.get().toString(record));
  }

  @Test
  public void testDateAndTimeTypes() {
    assertThat(UTC.format((int) EPOCH_DAY, logical("int", "date"))).isEqualTo("\"2024-01-01\"");
    assertThat(UTC.format(34200123, logical("int", "time-millis"))).isEqualTo("\"09:30:00.123\"");
    assertThat(UTC.format(34200123456L, logical("long", "time-micros"))).isEqualTo("\"09:30:00.123456\"");
    assertThat(UTC.format(0, logical("int", "time-millis"))).isEqualTo("\"00:00:00\"");
    assertThat(UTC.format(500, logical("int", "time-millis"))).isEqualTo("\"00:00:00.500\"");
    assertThat(UTC.format(500L, logical("long", "local-timestamp-millis")))
        .isEqualTo("\"1970-01-01T00:00:00.500\"");
  }

  @Test
  public void testTimestampTypes() {
    long millis = EPOCH_SECOND * 1000L + 123L;
    long micros = EPOCH_SECOND * 1_000_000L + 123456L;
    long nanos = EPOCH_SECOND * 1_000_000_000L + 123456789L;
    assertThat(UTC.format(millis, logical("long", "timestamp-millis"))).isEqualTo("\"2024-01-01T09:30:00.123Z\"");
    assertThat(UTC.format(micros, logical("long", "timestamp-micros")))
        .isEqualTo("\"2024-01-01T09:30:00.123456Z\"");
    assertThat(UTC.format(nanos, logical("long", "timestamp-nanos")))
        .isEqualTo("\"2024-01-01T09:30:00.123456789Z\"");
    assertThat(UTC.format(millis, logical("long", "local-timestamp-millis")))
        .isEqualTo("\"2024-01-01T09:30:00.123\"");
    assertThat(UTC.format(micros, logical("long", "local-timestamp-micros")))
        .isEqualTo("\"2024-01-01T09:30:00.123456\"");
    assertThat(UTC.format(-1L, logical("long", "timestamp-millis"))).isEqualTo("\"1969-12-31T23:59:59.999Z\"");
  }

  @Test
  public void testTimezone() {
    RecordFormatter seoul = new RecordFormatter(ZoneId.of("Asia/Seoul"));
    long millis = EPOCH_SECOND * 1000L;
    assertThat(seoul.format(millis, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T18:30:00+09:00\"");
    // fractions use 3, 6 or 9 digits like the UTC format
    assertThat(seoul.format(millis + 500, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T18:30:00.500+09:00\"");
    assertThat(UTC.format(millis + 500, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T09:30:00.500Z\"");
    assertThat(new RecordFormatter(ZoneId.of("UTC")).format(millis, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T09:30:00Z\"");
    // local timestamps and dates are not shifted
    assertThat(seoul.format(millis, logical("long", "local-timestamp-millis")))
        .isEqualTo("\"2024-01-01T09:30:00\"");
    assertThat(seoul.format((int) EPOCH_DAY, logical("int", "date"))).isEqualTo("\"2024-01-01\"");

    assertThat(RecordFormatter.fromTimezone("Asia/Seoul").format(millis, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T18:30:00+09:00\"");
    assertThat(RecordFormatter.fromTimezone(null).format(millis, logical("long", "timestamp-millis")))
        .isEqualTo("\"2024-01-01T09:30:00Z\"");
  }

  @Test
  public void testInt96InNestedTypes() {
    Configuration conf = new Configuration();
    conf.setBoolean(AvroReadSupport.READ_INT96_AS_FIXED, true);
    Schema schema = new AvroSchemaConverter(conf)
        .convert(MessageTypeParser.parseMessageType(
            "message m { optional int96 ts; required group times (LIST) { repeated int96 array; } }"));
    Schema tsSchema = schema.getField("ts").schema().getTypes().get(1);
    Schema timesSchema = schema.getField("times").schema();

    GenericData.Record record = new GenericData.Record(schema);
    record.put("ts", new GenericData.Fixed(tsSchema, int96(NANOS_OF_DAY, EPOCH_DAY)));
    record.put(
        "times",
        Arrays.asList(
            new GenericData.Fixed(timesSchema.getElementType(), int96(0, EPOCH_DAY)),
            new GenericData.Fixed(timesSchema.getElementType(), int96(0, 0))));

    assertThat(UTC.format(record, schema))
        .isEqualTo("{\"ts\": \"2024-01-01T09:30:00.123456789Z\", "
            + "\"times\": [\"2024-01-01T00:00:00Z\", \"1970-01-01T00:00:00Z\"]}");
    assertThat(new RecordFormatter(ZoneId.of("Asia/Seoul")).format(record.get("ts"), tsSchema))
        .isEqualTo("\"2024-01-01T18:30:00.123456789+09:00\"");
  }

  @Test
  public void testOutOfRangeValuesArePrintedAsNumbers() {
    assertThat(UTC.format(-1, logical("int", "time-millis"))).isEqualTo("-1");
    // 24:00:00 is outside the range of a time of day
    assertThat(UTC.format(86_400_000_000L, logical("long", "time-micros"))).isEqualTo("86400000000");
  }

  @Test
  public void testNullValues() {
    assertThat(UTC.format(null, logical("int", "date"))).isEqualTo("null");
    assertThat(UTC.format(null, null)).isEqualTo("null");
    assertThat(UTC.format(1, null)).isEqualTo("1");
  }
}
