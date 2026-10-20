# Parquet Tools

Parquet 파일을 관리하는 커맨드 및 JAR 파일을 RPM으로 빌드하는 프로젝트입니다.

본 프로젝트의 소스코드로 RPM을 빌드하기 위해서는 리눅스가 필요하며 Ubuntu에서 RPM 파일을 생성하려면 `rpm`을 설치하기 바랍니다.

## Requirement

* JDK 17

## INT96 타임스탬프 읽기

INT96 컬럼을 FIXED(12바이트)로 읽으려면 다음과 같이 실행합니다.

```
JAVA_OPTS="-Dparquet.avro.readInt96AsFixed=true" parquet cat <file>
```
