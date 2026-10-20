#!/bin/sh

set -e

ROOT_OUTPUT="/build/rpm"
JAR_OUT="$ROOT_OUTPUT/SOURCES"
PARQUET_VERSION="1.17.0"

# SOURCES also holds the launcher script, so only clear previously collected JARs
mkdir -p "$JAR_OUT"
rm -f "$JAR_OUT"/*.jar

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
    java-17-openjdk-devel \
    brotli-devel \
    snappy-devel \
    maven \
    glib2-devel

cd /build

cd thrift-0.22.0
./configure --without-tests --without-tutorial --with-boost=/usr --with-boost-libdir=/usr/lib64
make -j4
make install

cd ../parquet-1.17.0

echo ">>> 빌드 및 의존성 추출 시작..."

# Build parquet-cli and the modules it depends on, installing them so the next step resolves this build's artifacts
mvn clean install -DskipTests -pl parquet-cli -am

# Collect only parquet-cli's runtime classpath; the local profile switches Hadoop/log4j from provided to compile scope
mvn dependency:copy-dependencies -pl parquet-cli -Plocal -DoutputDirectory="$JAR_OUT" -DincludeScope=runtime
cp "parquet-cli/target/parquet-cli-$PARQUET_VERSION.jar" "$JAR_OUT"

# brotli-codec is an optional dependency of parquet-hadoop, so it is not inherited and must be copied explicitly
mvn -N dependency:copy -Dartifact=com.github.rdblue:brotli-codec:0.1.1 -DoutputDirectory="$JAR_OUT" \
    -DremoteRepositories=jitpack::default::https://jitpack.io
