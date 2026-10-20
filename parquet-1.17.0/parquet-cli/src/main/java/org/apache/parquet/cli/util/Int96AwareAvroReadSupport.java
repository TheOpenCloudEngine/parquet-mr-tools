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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericData;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.conf.ParquetConfiguration;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;

/**
 * An {@link AvroReadSupport} that can read INT96 columns with a requested projection.
 * <p>
 * With {@link AvroReadSupport#READ_INT96_AS_FIXED} enabled, INT96 columns become Avro fixed(12) fields. Converting
 * the Avro projection back to Parquet turns them into fixed_len_byte_array(12), which is incompatible with the
 * file schema, so this restores the INT96 type from the file schema for those columns.
 */
public class Int96AwareAvroReadSupport<T> extends AvroReadSupport<T> {

  public Int96AwareAvroReadSupport(GenericData model) {
    super(model);
  }

  @Override
  public ReadContext init(
      ParquetConfiguration configuration, Map<String, String> keyValueMetaData, MessageType fileSchema) {
    ReadContext context = super.init(configuration, keyValueMetaData, fileSchema);
    if (!configuration.getBoolean(READ_INT96_AS_FIXED, READ_INT96_AS_FIXED_DEFAULT)) {
      return context;
    }
    MessageType requested = context.getRequestedSchema();
    return new ReadContext(
        new MessageType(requested.getName(), restoreInt96Fields(requested, fileSchema)),
        context.getReadSupportMetadata());
  }

  private static List<Type> restoreInt96Fields(GroupType requested, GroupType file) {
    List<Type> fields = new ArrayList<>();
    for (Type field : requested.getFields()) {
      fields.add(file.containsField(field.getName()) ? restoreInt96(field, file.getType(field.getName())) : field);
    }
    return fields;
  }

  private static Type restoreInt96(Type requested, Type file) {
    if (requested.isPrimitive() && file.isPrimitive()) {
      return file.asPrimitiveType().getPrimitiveTypeName() == PrimitiveTypeName.INT96 ? file : requested;
    }
    if (!requested.isPrimitive() && !file.isPrimitive()) {
      GroupType group = requested.asGroupType();
      return group.withNewFields(restoreInt96Fields(group, file.asGroupType()));
    }
    return requested;
  }
}
