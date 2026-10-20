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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;

/**
 * Helpers for INT96 timestamps read with {@link org.apache.parquet.avro.AvroReadSupport#READ_INT96_AS_FIXED},
 * which turns INT96 columns into Avro fixed(12) values.
 */
public final class Int96Values {

  // Doc that AvroSchemaConverter sets on the fixed(12) schema it creates for INT96 columns
  private static final String INT96_DOC = "INT96 represented as byte[12]";
  private static final long JULIAN_DAY_OF_EPOCH = 2440588L;
  private static final long SECONDS_PER_DAY = 86400L;
  private static final long MICROS_PER_SECOND = 1_000_000L;
  private static final long NANOS_PER_MICRO = 1_000L;

  private Int96Values() {}

  public static boolean isInt96(Schema schema) {
    return schema.getType() == Schema.Type.FIXED
        && schema.getFixedSize() == 12
        && INT96_DOC.equals(schema.getDoc());
  }

  /**
   * Decodes an INT96 timestamp: 8 bytes of nanoseconds within the day followed by 4 bytes of Julian day,
   * both little-endian.
   */
  public static Instant toInstant(byte[] int96) {
    ByteBuffer buffer = ByteBuffer.wrap(int96).order(ByteOrder.LITTLE_ENDIAN);
    long nanosOfDay = buffer.getLong();
    long julianDay = buffer.getInt();
    return Instant.ofEpochSecond((julianDay - JULIAN_DAY_OF_EPOCH) * SECONDS_PER_DAY, nanosOfDay);
  }

  /**
   * Converts an INT96 timestamp to microseconds since the epoch, dropping sub-microsecond digits.
   */
  public static long toEpochMicros(byte[] int96) {
    Instant instant = toInstant(int96);
    return Math.addExact(
        Math.multiplyExact(instant.getEpochSecond(), MICROS_PER_SECOND), instant.getNano() / NANOS_PER_MICRO);
  }

  /**
   * Returns a copy of the schema where every INT96 fixed(12) schema is replaced by a timestamp-micros long.
   */
  public static Schema toTimestampSchema(Schema schema) {
    return toTimestampSchema(schema, new IdentityHashMap<>());
  }

  // Converted records are cached so a record schema referenced twice is not redefined under the same name
  private static Schema toTimestampSchema(Schema schema, Map<Schema, Schema> converted) {
    if (isInt96(schema)) {
      return LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
    }
    switch (schema.getType()) {
      case RECORD:
        Schema record = converted.get(schema);
        if (record == null) {
          record = Schema.createRecord(
              schema.getName(), schema.getDoc(), schema.getNamespace(), schema.isError());
          schema.getObjectProps().forEach(record::addProp);
          converted.put(schema, record);
          List<Schema.Field> fields = new ArrayList<>();
          for (Schema.Field field : schema.getFields()) {
            fields.add(new Schema.Field(field, toTimestampSchema(field.schema(), converted)));
          }
          record.setFields(fields);
        }
        return record;
      case UNION:
        List<Schema> types = new ArrayList<>();
        for (Schema type : schema.getTypes()) {
          types.add(toTimestampSchema(type, converted));
        }
        return Schema.createUnion(types);
      case ARRAY:
        return Schema.createArray(toTimestampSchema(schema.getElementType(), converted));
      case MAP:
        return Schema.createMap(toTimestampSchema(schema.getValueType(), converted));
      default:
        return schema;
    }
  }

  /**
   * Converts a datum read with {@code schema} to the matching datum for {@link #toTimestampSchema(Schema)}.
   */
  public static Object toTimestampDatum(Object datum, Schema schema, Schema timestampSchema) {
    if (datum == null) {
      return null;
    }
    if (isInt96(schema)) {
      return toEpochMicros(((GenericFixed) datum).bytes());
    }
    switch (schema.getType()) {
      case RECORD:
        GenericRecord source = (GenericRecord) datum;
        GenericData.Record converted = new GenericData.Record(timestampSchema);
        for (Schema.Field field : schema.getFields()) {
          Schema.Field target = timestampSchema.getField(field.name());
          converted.put(
              target.pos(), toTimestampDatum(source.get(field.pos()), field.schema(), target.schema()));
        }
        return converted;
      case UNION:
        int branch = GenericData.get().resolveUnion(schema, datum);
        return toTimestampDatum(
            datum,
            schema.getTypes().get(branch),
            timestampSchema.getTypes().get(branch));
      case ARRAY:
        List<Object> elements = new ArrayList<>();
        for (Object element : (List<?>) datum) {
          elements.add(toTimestampDatum(element, schema.getElementType(), timestampSchema.getElementType()));
        }
        return new GenericData.Array<>(timestampSchema, elements);
      case MAP:
        Map<Object, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) datum).entrySet()) {
          values.put(
              entry.getKey(),
              toTimestampDatum(entry.getValue(), schema.getValueType(), timestampSchema.getValueType()));
        }
        return values;
      default:
        return datum;
    }
  }
}
