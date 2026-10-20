#!/bin/sh

set -e

ROOT_OUTPUT="/build/rpm"
JAR_OUT="$ROOT_OUTPUT/SOURCES"
AVRO_OUT="$JAR_OUT/avro-tools"
PARQUET_VERSION="1.18.1"
THRIFT_VERSION="0.23.0"
AVRO_VERSION="1.12.2"

# Installing several JDKs can change the default java, so each build selects its JDK explicitly
JDK11_HOME="/usr/lib/jvm/java-11-openjdk"
JDK17_HOME="/usr/lib/jvm/java-17-openjdk"
JDK21_HOME="/usr/lib/jvm/java-21-openjdk"

# SOURCES also holds the launcher scripts, so only clear previously collected JARs
mkdir -p "$JAR_OUT" "$AVRO_OUT"
rm -f "$JAR_OUT"/*.jar "$AVRO_OUT"/*.jar

dnf config-manager --set-enabled crb
dnf install -y epel-release
dnf groupinstall -y 'Development Tools'
dnf install -y \
    cmake \
    boost-devel \
    openssl-devel \
    libevent-devel \
    zlib-devel \
    bison \
    flex \
    python3-devel \
    java-11-openjdk-devel \
    java-17-openjdk-devel \
    java-21-openjdk-devel \
    brotli-devel \
    snappy-devel \
    glib2-devel

cd /build

# Parquet requires this exact Thrift compiler version to generate parquet-format-structures
cd "thrift-$THRIFT_VERSION"
./configure --without-tests --without-tutorial --with-boost=/usr --with-boost-libdir=/usr/lib64
make -j4
make install

cd "../parquet-$PARQUET_VERSION"

echo ">>> 빌드 및 의존성 추출 시작..."

export JAVA_HOME="$JDK17_HOME"

# Parquet requires Maven 3.9+, so use the bundled Maven wrapper instead of the distribution's Maven
# Build parquet-cli and the modules it depends on, installing them so the next step resolves this build's artifacts
./mvnw clean install -DskipTests -pl parquet-cli -am

# Collect only parquet-cli's runtime classpath; the local profile switches Hadoop/log4j from provided to compile scope
./mvnw dependency:copy-dependencies -pl parquet-cli -Plocal -DoutputDirectory="$JAR_OUT" -DincludeScope=runtime
cp "parquet-cli/target/parquet-cli-$PARQUET_VERSION.jar" "$JAR_OUT"

# brotli-codec is an optional dependency of parquet-hadoop, so it is not inherited and must be copied explicitly
./mvnw -N dependency:copy -Dartifact=com.github.rdblue:brotli-codec:0.1.1 -DoutputDirectory="$JAR_OUT" \
    -DremoteRepositories=jitpack::default::https://jitpack.io

cd "../avro-$AVRO_VERSION"

echo ">>> avro-tools 빌드 시작..."

# Avro's build runs on JDK 21 and checks that JDK 11 and 17 toolchains exist
cat > toolchains.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain><type>jdk</type><provides><version>11</version></provides><configuration><jdkHome>$JDK11_HOME</jdkHome></configuration></toolchain>
  <toolchain><type>jdk</type><provides><version>17</version></provides><configuration><jdkHome>$JDK17_HOME</jdkHome></configuration></toolchain>
  <toolchain><type>jdk</type><provides><version>21</version></provides><configuration><jdkHome>$JDK21_HOME</jdkHome></configuration></toolchain>
</toolchains>
EOF

# Avro has no Maven wrapper of its own and needs Maven 3.9.6+, so reuse Parquet's
export JAVA_HOME="$JDK21_HOME"
"../parquet-$PARQUET_VERSION/mvnw" -t toolchains.xml clean package -DskipTests -pl lang/java/tools -am
cp "lang/java/tools/target/avro-tools-$AVRO_VERSION.jar" "$AVRO_OUT"
