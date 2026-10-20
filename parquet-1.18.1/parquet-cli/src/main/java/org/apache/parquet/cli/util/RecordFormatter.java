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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;

/**
 * Formats Avro data like {@link GenericData#toString(Object)}, but prints date and time values as ISO-8601 strings.
 * <p>
 * Handles the Avro date, time and timestamp logical types and INT96 timestamps read as fixed(12) values.
 * Timestamps are printed in UTC unless a time zone is given; local timestamps, dates and times are not shifted.
 */
public class RecordFormatter {

  /** Configuration key for the time zone used to print timestamps, for example Asia/Seoul */
  public static final String TIMEZONE = "parquet.cli.timezone";

  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final long MICROS_PER_SECOND = 1_000_000L;
  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final long NANOS_PER_MILLI = 1_000_000L;
  private static final long NANOS_PER_MICRO = 1_000L;

  // Like Instant#toString, seconds are always printed and fractions use 3, 6 or 9 digits
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final ZoneId zone;

  /**
   * @param zone the time zone to print timestamps in, or null to print them in UTC with a Z suffix
   */
  public RecordFormatter(ZoneId zone) {
    this.zone = zone;
  }

  public static RecordFormatter fromTimezone(String timezone) {
    return new RecordFormatter(timezone == null || timezone.isEmpty() ? null : ZoneId.of(timezone));
  }

  public String format(Object datum, Schema schema) {
    StringBuilder buffer = new StringBuilder();
    append(buffer, datum, schema);
    return buffer.toString();
  }

  private void append(StringBuilder buffer, Object datum, Schema schema) {
    if (datum == null || schema == null) {
      buffer.append(GenericData.get().toString(datum));
      return;
    }
    switch (schema.getType()) {
      case UNION:
        int branch = GenericData.get().resolveUnion(schema, datum);
        append(buffer, datum, schema.getTypes().get(branch));
        return;
      case RECORD:
        if (datum instanceof GenericRecord) {
          appendRecord(buffer, (GenericRecord) datum);
          return;
        }
        break;
      case ARRAY:
        if (datum instanceof Collection) {
          appendArray(buffer, (Collection<?>) datum, schema.getElementType());
          return;
        }
        break;
      case MAP:
        if (datum instanceof Map) {
          appendMap(buffer, (Map<?, ?>) datum, schema.getValueType());
          return;
        }
        break;
      case FIXED:
        if (datum instanceof GenericFixed && Int96Values.isInt96(schema)) {
          appendQuoted(buffer, formatInstant(Int96Values.toInstant(((GenericFixed) datum).bytes())));
          return;
        }
        break;
      case INT:
      case LONG:
        String formatted = formatLogical(schema, datum);
        if (formatted != null) {
          appendQuoted(buffer, formatted);
          return;
        }
        break;
      default:
        break;
    }
    buffer.append(GenericData.get().toString(datum));
  }

  private void appendRecord(StringBuilder buffer, GenericRecord record) {
    buffer.append("{");
    List<Schema.Field> fields = record.getSchema().getFields();
    for (int i = 0; i < fields.size(); i++) {
      Schema.Field field = fields.get(i);
      if (i > 0) {
        buffer.append(", ");
      }
      buffer.append(GenericData.get().toString(field.name())).append(": ");
      append(buffer, record.get(field.pos()), field.schema());
    }
    buffer.append("}");
  }

  private void appendArray(StringBuilder buffer, Collection<?> elements, Schema elementSchema) {
    buffer.append("[");
    int count = 0;
    for (Object element : elements) {
      if (count++ > 0) {
        buffer.append(", ");
      }
      append(buffer, element, elementSchema);
    }
    buffer.append("]");
  }

  private void appendMap(StringBuilder buffer, Map<?, ?> map, Schema valueSchema) {
    buffer.append("{");
    int count = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (count++ > 0) {
        buffer.append(", ");
      }
      buffer.append(GenericData.get().toString(entry.getKey())).append(": ");
      append(buffer, entry.getValue(), valueSchema);
    }
    buffer.append("}");
  }

  private static void appendQuoted(StringBuilder buffer, String value) {
    buffer.append('"').append(value).append('"');
  }

  private String formatLogical(Schema schema, Object datum) {
    if (!(datum instanceof Number)) {
      return null;
    }
    try {
      return formatLogical(logicalTypeName(schema), ((Number) datum).longValue());
    } catch (DateTimeException | ArithmeticException e) {
      // Out of range values are printed as plain numbers
      return null;
    }
  }

  private String formatLogical(String logicalType, long value) {
    switch (logicalType) {
      case "date":
        return LocalDate.ofEpochDay(value).toString();
      case "time-millis":
        return formatTime(LocalTime.ofNanoOfDay(Math.multiplyExact(value, NANOS_PER_MILLI)));
      case "time-micros":
        return formatTime(LocalTime.ofNanoOfDay(Math.multiplyExact(value, NANOS_PER_MICRO)));
      case "timestamp-millis":
        return formatInstant(toInstant(value, MILLIS_PER_SECOND));
      case "timestamp-micros":
        return formatInstant(toInstant(value, MICROS_PER_SECOND));
      case "timestamp-nanos":
        return formatInstant(toInstant(value, NANOS_PER_SECOND));
      case "local-timestamp-millis":
        return formatLocal(toInstant(value, MILLIS_PER_SECOND));
      case "local-timestamp-micros":
        return formatLocal(toInstant(value, MICROS_PER_SECOND));
      case "local-timestamp-nanos":
        return formatLocal(toInstant(value, NANOS_PER_SECOND));
      default:
        return null;
    }
  }

  // Logical types unknown to this Avro version (for example timestamp-nanos) are only kept as a schema property
  private static String logicalTypeName(Schema schema) {
    LogicalType logicalType = schema.getLogicalType();
    if (logicalType != null) {
      return logicalType.getName();
    }
    String name = schema.getProp(LogicalType.LOGICAL_TYPE_PROP);
    return name == null ? "" : name;
  }

  private static Instant toInstant(long value, long unitsPerSecond) {
    long nanosPerUnit = NANOS_PER_SECOND / unitsPerSecond;
    return Instant.ofEpochSecond(
        Math.floorDiv(value, unitsPerSecond), Math.floorMod(value, unitsPerSecond) * nanosPerUnit);
  }

  private String formatInstant(Instant instant) {
    if (zone == null) {
      return instant.toString();
    }
    ZonedDateTime dateTime = instant.atZone(zone);
    return dateTime.format(DATE_TIME)
        + fraction(dateTime.getNano())
        + dateTime.getOffset().getId();
  }

  private static String formatLocal(Instant instant) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    return dateTime.format(DATE_TIME) + fraction(dateTime.getNano());
  }

  private static String formatTime(LocalTime time) {
    return time.format(TIME) + fraction(time.getNano());
  }

  private static String fraction(int nano) {
    if (nano == 0) {
      return "";
    } else if (nano % NANOS_PER_MILLI == 0) {
      return String.format(".%03d", nano / NANOS_PER_MILLI);
    } else if (nano % NANOS_PER_MICRO == 0) {
      return String.format(".%06d", nano / NANOS_PER_MICRO);
    }
    return String.format(".%09d", nano);
  }
}
