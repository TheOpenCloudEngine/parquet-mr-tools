#!/bin/sh
#
# Regenerates the sample Avro/Parquet files from users.avsc, users.json and Int96Sample.java.
# Requires the parquet-mr-tools RPM (parquet, avro commands) and JDK 17+.

set -e

cd "$(dirname "$0")"

PARQUET_LIB="${PARQUET_LIB:-/opt/parquet-mr-tools/lib}"

rm -f users.avro users.parquet users_from_csv.parquet events_int96.parquet

# Avro file from JSON (Avro JSON encoding) with a schema
avro fromjson --schema-file users.avsc --codec snappy users.json > users.avro

# Parquet files from the Avro file and from CSV
parquet convert users.avro -o users.parquet
parquet convert-csv users.csv -o users_from_csv.parquet

# Parquet file with INT96 timestamps
java -cp "$PARQUET_LIB/*" Int96Sample.java events_int96.parquet

# Remove the CRC files written by the Hadoop local file system
rm -f .*.crc
