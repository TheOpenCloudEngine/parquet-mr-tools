import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

/**
 * Writes events_int96.parquet, a sample with INT96 timestamps like those written by Hive, Impala and older Spark.
 * The event_type column also gets a bloom filter, which the parquet CLI cannot write itself.
 *
 * <pre>
 * java -cp "/opt/parquet-mr-tools/lib/*" Int96Sample.java events_int96.parquet
 * </pre>
 */
public class Int96Sample {

  private static final long JULIAN_DAY_OF_EPOCH = 2440588L;
  private static final long NANOS_PER_DAY = 86_400_000_000_000L;

  // INT96 timestamp: 8 bytes of nanoseconds within the day followed by 4 bytes of Julian day, both little-endian
  static Binary int96(String timestamp) {
    Instant instant = Instant.parse(timestamp);
    long nanos = Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    long epochDay = Math.floorDiv(nanos, NANOS_PER_DAY);
    long nanosOfDay = Math.floorMod(nanos, NANOS_PER_DAY);
    ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putLong(nanosOfDay).putInt((int) (epochDay + JULIAN_DAY_OF_EPOCH));
    return Binary.fromConstantByteArray(buffer.array());
  }

  public static void main(String[] args) throws Exception {
    MessageType schema = MessageTypeParser.parseMessageType("message event {"
        + "  required int32 event_id;"
        + "  required int32 user_id;"
        + "  required binary event_type (STRING);"
        + "  required int96 event_time;"
        + "  optional int96 processed_at;"
        + "  optional group detail {"
        + "    optional binary ip (STRING);"
        + "    optional int64 duration_ms;"
        + "  }"
        + "}");
    Object[][] rows = {
      {1, 1, "login", "2026-10-01T09:12:30.123456789Z", "2026-10-01T09:12:31Z", "10.0.0.11", 120L},
      {2, 4, "purchase", "2026-10-15T21:05:42.500Z", "2026-10-15T21:05:43.250Z", "10.0.0.24", 3400L},
      {3, 6, "login", "2026-10-19T12:00:00Z", null, "10.0.0.36", 95L},
      {4, 2, "logout", "2026-09-28T18:45:00Z", "2026-09-28T18:45:02Z", null, null},
      {5, 7, "view", "2026-10-18T08:20:10.000001Z", "2026-10-18T08:20:11Z", "10.0.0.47", 15800L},
    };

    SimpleGroupFactory factory = new SimpleGroupFactory(schema);
    try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new Path(args[0]))
        .withType(schema)
        .withCompressionCodec(CompressionCodecName.SNAPPY)
        // Bloom filters are skipped for fully dictionary-encoded columns, so disable the dictionary for this column
        .withDictionaryEncoding("event_type", false)
        .withBloomFilterEnabled("event_type", true)
        .build()) {
      for (Object[] row : rows) {
        Group event = factory.newGroup()
            .append("event_id", (Integer) row[0])
            .append("user_id", (Integer) row[1])
            .append("event_type", (String) row[2])
            .append("event_time", int96((String) row[3]));
        if (row[4] != null) {
          event.append("processed_at", int96((String) row[4]));
        }
        if (row[5] != null) {
          Group detail = event.addGroup("detail");
          detail.append("ip", (String) row[5]);
          detail.append("duration_ms", (Long) row[6]);
        }
        writer.write(event);
      }
    }
  }
}
