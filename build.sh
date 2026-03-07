#!/bin/sh

ROOT_OUTPUT="/build/rpm"
JAR_OUT="$ROOT_OUTPUT/SOURCES "

rm -rf "$ROOT_OUTPUT"
mkdir -p "$JAR_OUT"

dnf config-manager --set-enabled crb
dnf install -y epel-release

dnf install epel-release -y
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
    boost-devel \
    maven \
    glib2-devel

cd /build

cd thrift-0.22.0
./configure --without-tests --without-tutorial --with-boost=/usr --with-boost-libdir=/usr/lib64
make -j4
make install

cd ../parquet-1.17.0

echo ">>> 빌드 및 의존성 추출 시작..."

mvn clean package -DskipTests
mvn dependency:copy-dependencies -DoutputDirectory="$JAR_OUT" -DincludeScope=runtime

find . -mindepth 2 -maxdepth 3 -name "target" -type d | while read target_dir; do
    find "$target_dir" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "original-*.jar" ! -name "*-runtime.jar" ! -name "*-javadoc.jar" -exec cp {} "$JAR_OUT" \;
done

find . -mindepth 2 -maxdepth 3 -name "target" -type d | while read target_dir; do
    find "$target_dir" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "original-*.jar" ! -name "*-runtime.jar" ! -name "*-javadoc.jar" -exec cp {} ../rpm/SOURCES \;
done

