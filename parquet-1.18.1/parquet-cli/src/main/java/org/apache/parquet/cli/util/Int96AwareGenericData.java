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
import java.util.IdentityHashMap;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;

/**
 * A {@link GenericData} whose {@link #toString(Object)} prints INT96 values as UTC timestamps.
 * <p>
 * With {@link org.apache.parquet.avro.AvroReadSupport#READ_INT96_AS_FIXED} enabled, INT96 columns are read as
 * fixed(12) values, which would otherwise be printed as raw byte arrays.
 */
public class Int96AwareGenericData extends GenericData {

  // Doc that AvroSchemaConverter sets on the fixed(12) schema it creates for INT96 columns
  private static final String INT96_DOC = "INT96 represented as byte[12]";
  private static final long JULIAN_DAY_OF_EPOCH = 2440588L;
  private static final long SECONDS_PER_DAY = 86400L;

  private static final Int96AwareGenericData INSTANCE = new Int96AwareGenericData();

  public static Int96AwareGenericData get() {
    return INSTANCE;
  }

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

  @Override
  protected void toString(Object datum, StringBuilder buffer, IdentityHashMap<Object, Object> seenObjects) {
    if (datum instanceof GenericFixed && isInt96(((GenericFixed) datum).getSchema())) {
      buffer.append('"').append(toInstant(((GenericFixed) datum).bytes())).append('"');
    } else {
      super.toString(datum, buffer, seenObjects);
    }
  }
}
