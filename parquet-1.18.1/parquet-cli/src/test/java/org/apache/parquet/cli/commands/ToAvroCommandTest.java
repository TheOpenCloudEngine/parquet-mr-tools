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

package org.apache.parquet.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beust.jcommander.JCommander;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileAlreadyExistsException;
import org.apache.parquet.avro.AvroReadSupport;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ToAvroCommandTest extends AvroFileTest {
  @TempDir
  private Path tempDir;

  @Test
  public void testToAvroCommandFromParquet() throws IOException {
    File avroFile = toAvro(parquetFile());
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandFromJson() throws IOException {
    final File jsonInputFile = tempDir.resolve("sample.json").toFile();
    Files.createFile(jsonInputFile.toPath());
    final File avroOutputFile = tempDir.resolve("sample.avro").toFile();
    Files.createFile(avroOutputFile.toPath());

    // Write the json to the file, so we can read it again.
    final String inputJson = "{\"id\": 1, \"name\": \"Alice\"}\n" + "{\"id\": 2, \"name\": \"Bob\"}\n"
        + "{\"id\": 3, \"name\": \"Carol\"}\n"
        + "{\"id\": 4, \"name\": \"Dave\"}";

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonInputFile))) {
      writer.write(inputJson);
    }

    ToAvroCommand cmd = new ToAvroCommand(null);

    JCommander.newBuilder()
        .addObject(cmd)
        .build()
        .parse("--overwrite", jsonInputFile.getAbsolutePath(), "--output", avroOutputFile.getAbsolutePath());

    assertThat(cmd.run()).isZero();
  }

  @Test
  public void testToAvroCommandWithGzipCompression() throws IOException {
    File avroFile = toAvro(parquetFile(), "GZIP");
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandWithSnappyCompression() throws IOException {
    File avroFile = toAvro(parquetFile(), "SNAPPY");
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandWithZstdCompression() throws IOException {
    File avroFile = toAvro(parquetFile(), "ZSTD");
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandWithBzip2Compression() throws IOException {
    File avroFile = toAvro(parquetFile(), "bzip2");
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandWithXzCompression() throws IOException {
    File avroFile = toAvro(parquetFile(), "xz");
    assertThat(avroFile).exists();
  }

  @Test
  public void testToAvroCommandWithInvalidCompression() throws IOException {
    File parquetFile = parquetFile();
    assertThatThrownBy(() -> toAvro(parquetFile, "FOO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Codec incompatible with Avro: FOO");
  }

  @Test
  public void testToAvroCommandOverwriteExistentFile() throws IOException {
    File outputFile = new File(getTempFolder(), getClass().getSimpleName() + ".avro");
    FileUtils.touch(outputFile);
    assertThat(outputFile.length()).isZero();
    File avroFile = toAvro(parquetFile(), outputFile, true);
    assertThat(avroFile.length()).isPositive();
  }

  @Test
  public void testToAvroCommandOverwriteExistentFileWithoutOverwriteOption() throws IOException {
    File outputFile = new File(getTempFolder(), getClass().getSimpleName() + ".avro");
    FileUtils.touch(outputFile);
    File parquetFile = parquetFile();
    assertThatThrownBy(() -> toAvro(parquetFile, outputFile, false))
        .isInstanceOf(FileAlreadyExistsException.class)
        .hasMessageContaining("File already exists");
  }

  @Test
  public void testToAvroCommandWithInt96AsTimestamp() throws IOException {
    File parquet = new File(getTempFolder(), "int96.parquet");
    MessageType schema = MessageTypeParser.parseMessageType("message m { required int32 id; optional int96 ts; }");
    // 2024-01-01T09:30:00.123456789Z
    byte[] int96 = ByteBuffer.allocate(12)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(34200123456789L)
        .putInt(2460311)
        .array();
    SimpleGroupFactory factory = new SimpleGroupFactory(schema);
    try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(
            new org.apache.hadoop.fs.Path(parquet.getAbsolutePath()))
        .withType(schema)
        .build()) {
      writer.write(factory.newGroup().append("id", 1).append("ts", Binary.fromConstantByteArray(int96)));
      writer.write(factory.newGroup().append("id", 2));
    }

    File avro = new File(getTempFolder(), "int96.avro");
    ToAvroCommand command = new ToAvroCommand(createLogger());
    command.targets = Arrays.asList(parquet.getAbsolutePath());
    command.outputPath = avro.getAbsolutePath();
    command.int96AsTimestamp = true;
    Configuration conf = new Configuration();
    conf.setBoolean(AvroReadSupport.READ_INT96_AS_FIXED, true);
    command.setConf(conf);
    assertThat(command.run()).isZero();

    try (DataFileReader<GenericRecord> reader = new DataFileReader<>(avro, new GenericDatumReader<>())) {
      Schema ts = reader.getSchema().getField("ts").schema().getTypes().get(1);
      assertThat(ts.getType()).isEqualTo(Schema.Type.LONG);
      assertThat(ts.getLogicalType()).isEqualTo(LogicalTypes.timestampMicros());
      // digits below microseconds are dropped
      assertThat(reader.next().get("ts")).isEqualTo(1704101400123456L);
      assertThat(reader.next().get("ts")).isNull();
    }
  }
}
