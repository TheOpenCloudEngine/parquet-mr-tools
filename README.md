# Parquet & Avro Tools

Parquet 및 Avro 파일을 관리하는 커맨드(`parquet`, `avro`)와 JAR 파일을 RPM으로 빌드하는 프로젝트입니다.

upstream `parquet-cli`는 INT96 타임스탬프 컬럼이 있는 파일을 읽지 못하므로, 이 프로젝트는 INT96을 기본으로 읽고 날짜/시간으로 보여 주도록 수정한 `parquet-cli`를 제공합니다.

RPM을 설치하면 다음 두 명령을 사용할 수 있습니다.

| 명령 | 설명 |
|---|---|
| `parquet` | Parquet 파일 조회/변환 (Parquet 1.18.1 `parquet-cli`, INT96 지원 수정 적용) |
| `avro` | Avro 파일 조회/변환 (Apache Avro 1.12.2 `avro-tools`, 예: `avro getschema`, `avro tojson`, `avro count`) |

`avro-tools`는 자체 Hadoop을 포함한 단일 JAR이므로, `parquet`의 클래스패스(`/opt/parquet-avro-tools/lib`)와 분리된 `/opt/parquet-avro-tools/avro`에 설치됩니다.

## 사용법

[`samples/`](samples/)의 샘플 파일로 실제 실행한 결과를 담은 매뉴얼입니다.

* [parquet 명령 사용법](parquet.md): 스키마/데이터/메타데이터 조회, INT96 타임스탬프, Bloom filter, Avro/CSV 변환, 컬럼 제거·마스킹·압축 변경
* [avro 명령 사용법](avro.md): 스키마/메타데이터 조회, JSON 변환, 레코드 추출·합치기, 압축 코덱 변경, 스키마 도구

본 프로젝트의 소스코드로 RPM을 빌드하기 위해서는 리눅스가 필요하며 Ubuntu에서 RPM 파일을 생성하려면 `rpm`을 설치하기 바랍니다.

## Requirement

* 실행: JDK 17 이상
* 빌드
  * JDK 17 (Parquet 빌드)
  * JDK 21, 그리고 Maven toolchain용 JDK 11/17 (Avro 빌드, `build.sh`가 `toolchains.xml`을 생성)
  * Maven 3.9 이상 (Parquet 소스에 포함된 Maven Wrapper `./mvnw`를 사용하면 별도 설치 불필요, Avro 빌드에도 사용)
  * Thrift 컴파일러 0.23.0 (`build.sh`가 `thrift-0.23.0/`을 빌드)

## 소스 구성

| 디렉터리 | 설명 |
|---|---|
| `parquet-1.18.1/` | 현재 빌드 대상. upstream 1.18.1 소스에 INT96 지원 수정을 적용 |
| `parquet-1.17.0/` | 이전 버전 소스 |
| `avro-1.12.2/` | Apache Avro 1.12.2 소스 (`avro-tools` 빌드용) |
| `thrift-0.23.0/`, `thrift-0.22.0/` | 각각 Parquet 1.18.1, 1.17.0 빌드에 필요한 Thrift 소스 |
| `rpm/` | RPM 스펙, 실행 스크립트, 런타임 JAR, 빌드된 RPM |
| `samples/` | 매뉴얼용 샘플 Parquet/Avro/CSV 파일과 생성 스크립트(`generate.sh`) |

## 버전별 주요 변경 사항

이 프로젝트는 Parquet 1.18.1과 Avro 1.12.2를 기반으로 합니다.

### Parquet

1.12부터 최신 버전까지의 주요 기능 변화를 요약하면 다음과 같습니다.
(출처: [CHANGES.md](parquet-1.18.1/CHANGES.md), [apache/parquet-java Releases](https://github.com/apache/parquet-java/releases))

| 버전 | 주요 변경 사항 |
|---|---|
| 1.12 | Bloom filter 지원, 컬럼 암호화(Encryption) 및 키 관리 도구, ZSTD를 JNI(zstd-jni)로 교체, CLI에 `prune`, `column-size` 명령 추가 |
| 1.13 | LZ4_RAW 코덱 지원, Bloom filter 병합 및 FPP 설정, 컬럼 인덱스/통계 truncation, CLI에 `scan`, `bloom-filter` 명령 추가, Hadoop 3.2 기반 |
| 1.14 | Hadoop 없이 Parquet 사용 가능, Vectored IO 구현, FLOAT16 타입, BYTE_STREAM_SPLIT의 INT32/INT64/FIXED_LEN_BYTE_ARRAY 지원, 전체 컬럼 암호화, CLI `rewrite --overwrite` |
| 1.15 | 배열 컬럼용 `contains` 필터, 컬럼별 통계 비활성화, Parquet joiner v2, ParquetRewriter 컬럼 이름 변경, `parquet-scala` 제거, CLI 압축 코덱 이름 대소문자 무시 |
| 1.15.1 / 1.15.2 | parquet-avro의 역직렬화 허용 패키지/클래스 제한 (보안 수정), DictionaryFilter의 null 값 처리 버그 수정 |
| 1.16 | Variant 타입 읽기/쓰기, Geometry/Geography 타입, Vectored IO 기본 활성화, Hadoop 2 지원 제거, Pig 연동 deprecated, 중첩 타입의 INT96 읽기 버그 수정, CLI `size-stats`, `version` 명령 추가 |
| 1.17 | **최소 Java 11**, 순수 Parquet 파일 `cat` 지원, 암호화된 파일의 CLI 명령 지원, CLI 파일 기반 설정 통합, CLI의 Protobuf 지원, INT32/INT64 decimal의 Avro 읽기, 잘못된 통계가 있어도 읽기 실패하지 않도록 개선 |
| 1.17.1 | Protobuf `UInt32Value` 읽기 버그 수정 |
| 1.18 | Pig 연동 제거, 컬럼별 압축 코덱 지정, 부동소수점 컬럼의 IEEE 754 total order 통계와 NaN 개수 기록(기본 적용), 리더에서 row group 인덱스 제공, JSON → Variant 변환, 알 수 없는(향후) logical type이 있는 파일도 읽기 가능, CLI `column-size` 크기순 정렬, LZ4_RAW 해제 및 딕셔너리 재사용 시 데이터 손상 버그 수정, Java 17 초과 버전 호환성 수정 |
| 1.18.1 | 힙 외부(direct) ByteBuffer 값 읽기 시 데이터 손상 버그 수정, 키 material에서 KMS URL을 읽는 옵션 추가 |

> 2026-10-20 기준 최신 정식 버전은 1.18.1이며, 1.19.0은 아직 RC 단계입니다.

### Avro

1.11부터 최신 버전까지 Java SDK와 `avro-tools` 관련 주요 변화를 요약하면 다음과 같습니다.
(출처: [apache/avro Releases](https://github.com/apache/avro/releases), 릴리스 노트가 없는 버전은 [태그 간 커밋 비교](https://github.com/apache/avro/compare/release-1.12.0...release-1.12.1))

| 버전 | 주요 변경 사항 |
|---|---|
| 1.11.0 | FIXED 타입에 doc 지원(스펙), Android에서 Avro core 사용 지원, Rust SDK 실험적 추가, Python 2 지원 중단 |
| 1.11.1 | Java 17 지원, 직렬화 오류 메시지에 union/배열/맵 경로 정보 추가, LogicalTypeFactory 자동 등록, 멀티스레드 GenericDatumReader 무한 루프 수정, IDL에 optional 필드 문법 추가, 재현 가능한 빌드 |
| 1.11.2 | ServiceLoader로 Conversion 검색, union 타입 conversion 상속, 스키마 뒤의 불필요한 내용에 SchemaParseException 발생, bytes 길이를 INT 범위로 제한 |
| 1.11.3 | 1.11.2의 알려진 문제 수정 |
| 1.11.4 | SpecificData의 신뢰 패키지(trusted packages) 지원 (보안 수정), Commons Compress CVE-2023-42503 수정, 데이터 파일 블록 읽기 오류 수정 |
| 1.11.5 | 기본 직렬화 허용 패키지 제거, `org.apache.avro.SERIALIZABLE_CLASSES` 속성 도입 (보안 수정) |
| 1.12.0 | **Java 11 이상 필요, Java 17 지원**, 모든 스키마 형식을 위한 새 스키마 파서, IDL을 `.avsc` 대체 형식으로 확장, reflect에서 `java.util.Optional` 지원, 데이터 파일 마지막 sync marker 처리 버그 수정, Commons Compress CVE 수정 |
| 1.12.1 | 빈 패키지 클래스가 SpecificDatumReader에서 신뢰되지 않도록 수정 (보안 수정), ASCII 문자열 직렬화 최적화, IDL에서 밑줄로 시작하는 이름 허용 |
| 1.12.2 | **임의 Java 클래스 생성 제한 (호환성 변경)**: `SpecificDatumReader`/`java-class`로 만들 클래스를 `org.apache.avro.SERIALIZABLE_CLASSES`/`SERIALIZABLE_PACKAGES`로 지정해야 함, `javaAnnotation` 값을 통한 코드 주입 수정 (보안 수정), bytes 기본값 오류 수정, `avro-tools`의 hashCode 필드 충돌 수정 |

> 2026-10-20 기준 Avro 최신 정식 버전은 1.12.2입니다.

## INT96 타임스탬프 읽기

INT96 컬럼은 별도 옵션 없이 읽히며, `cat`/`head` 출력에서 UTC 기준 ISO-8601 타임스탬프로 표시됩니다.

```
$ parquet cat events.parquet
{"id": 1, "ts": "2024-01-01T09:30:00.123456789Z", "ev": {"name": "login", "at": "2024-01-02T00:00:00Z"}}
```

upstream과 같이 INT96 컬럼을 읽지 않으려면 옵션을 끕니다.

```
JAVA_OPTS="-Dparquet.avro.readInt96AsFixed=false" parquet cat <file>
parquet -D parquet.avro.readInt96AsFixed=false cat <file>
```

`to-avro`, `convert` 등 파일을 쓰는 명령은 INT96 값을 12바이트 FIXED 값 그대로 기록합니다.
