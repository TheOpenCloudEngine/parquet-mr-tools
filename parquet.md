# parquet 명령 사용법

`parquet`는 Parquet 파일의 스키마, 데이터, 메타데이터를 조회하고 Avro/CSV와 서로 변환하는 명령입니다.
Apache Parquet 1.18.1의 `parquet-cli`에 INT96 타임스탬프 지원을 추가한 버전입니다.

이 문서의 모든 예시는 저장소의 [`samples/`](samples/) 파일로 실제로 실행한 결과입니다(JDK 17, `parquet-mr-tools-1.18.1-1` RPM).
명령은 저장소 루트에서 실행했고, 결과 파일은 `out/` 디렉터리에 썼습니다.

```
mkdir -p out
```

> Hadoop이 출력하는 `WARN util.NativeCodeLoader: Unable to load native-hadoop library ...` 경고는 stderr로 출력되며 결과와 무관하므로 예시에서 생략했습니다.

## 목차

- [샘플 파일](#샘플-파일)
- [공통 옵션](#공통-옵션)
- [명령 목록](#명령-목록)
- [스키마 조회](#스키마-조회)
- [데이터 조회](#데이터-조회)
- [INT96 타임스탬프](#int96-타임스탬프)
- [메타데이터 조회](#메타데이터-조회)
- [Bloom filter 조회](#bloom-filter-조회)
- [Avro/CSV에서 Parquet으로 변환](#avrocsv에서-parquet으로-변환)
- [Parquet에서 Avro로 변환](#parquet에서-avro로-변환)
- [Parquet 파일 재작성](#parquet-파일-재작성)
- [주의 사항](#주의-사항)

## 샘플 파일

| 파일 | 내용 |
|---|---|
| `samples/users.avsc` | 사용자 레코드의 Avro 스키마 (int, string, nullable string, double, boolean, date, timestamp-millis, array, 중첩 record) |
| `samples/users.json` | 사용자 10건 (Avro JSON 인코딩) |
| `samples/users.csv` | 같은 사용자 10건의 CSV (중첩 필드 제외) |
| `samples/users.avro` | `users.json`으로 만든 Avro 파일 (snappy) |
| `samples/users.parquet` | `users.avro`를 `parquet convert`로 변환한 Parquet 파일 |
| `samples/users_from_csv.parquet` | `users.csv`를 `parquet convert-csv`로 변환한 Parquet 파일 |
| `samples/events_int96.parquet` | INT96 타임스탬프 컬럼과 `event_type` Bloom filter가 있는 이벤트 5건 |
| `samples/Int96Sample.java` | `events_int96.parquet` 생성 코드 |
| `samples/generate.sh` | 샘플 파일 재생성 스크립트 |

샘플 파일은 다음과 같이 다시 만들 수 있습니다.

```
samples/generate.sh
```

## 공통 옵션

명령 앞에 쓰는 옵션입니다.

| 옵션 | 설명 |
|---|---|
| `-v`, `--verbose`, `--debug` | 오류 발생 시 상세 정보(스택 트레이스) 출력 |
| `--conf`, `--property` `key=value` | 설정 값 지정 (여러 번 사용 가능) |
| `--config-file` `<path>` | 설정 파일 지정 (properties 또는 Hadoop XML) |
| `-D` `key=value` | Hadoop 방식의 설정 값 지정 |

JVM 옵션은 환경 변수 `JAVA_OPTS`로 전달합니다.

```
JAVA_OPTS="-Xmx2g" parquet cat big.parquet
```

명령별 도움말은 `parquet help <명령>`으로 확인합니다.

```
$ parquet help convert
Usage: parquet [general options] convert [command options]

  Description:

    Create a Parquet file from a data file

  Command options:

    --overwrite
	Overwrite the output file if it exists
    --compression-codec
	A compression codec name.
    --page-size
	Target page size
    -s, --schema
	The file containing the Avro schema.
    --dictionary-size
	Max dictionary page size
    --row-group-size
	Target row group size
  * -o, --output
	Output file path
    -c, --column, --columns
	List of columns

  * = required
...
```

## 명령 목록

| 명령 | 설명 |
|---|---|
| `version` | CLI 버전 출력 |
| `schema` | Avro 스키마(기본) 또는 Parquet 스키마(`--parquet`) 출력 |
| `cat`, `head` | 레코드 출력 (`head`는 기본 10건) |
| `meta` | 파일 메타데이터, 스키마, row group별 컬럼 통계 출력 |
| `footer` | 파일 footer를 JSON으로 출력 |
| `pages` | 컬럼별 페이지 정보 출력 |
| `dictionary` | 컬럼의 딕셔너리 값 출력 |
| `column-size` | 컬럼별 크기와 비율 출력 |
| `column-index` | 컬럼 인덱스와 오프셋 인덱스 출력 |
| `size-stats` | 컬럼별 크기 통계(size statistics) 출력 |
| `check-stats` | 손상된 통계(PARQUET-251) 검사 |
| `scan` | 전체 레코드를 읽어 개수와 소요 시간 출력 |
| `bloom-filter` | Bloom filter로 값의 존재 여부 확인 |
| `convert` | Avro 등 데이터 파일을 Parquet으로 변환 |
| `csv-schema` | CSV에서 Avro 스키마 추론 |
| `convert-csv` | CSV를 Parquet으로 변환 |
| `to-avro` | Parquet 등 데이터 파일을 Avro로 변환 |
| `rewrite` | 컬럼 제거, 마스킹, 압축 변경, 파일 병합 |
| `prune`, `trans-compression`, `masking` | `rewrite`로 대체 예정 (deprecated) |

```
$ parquet version
parquet-mr version 1.18.1 (build f682a808de9b3d4d50bf6953a670640683dbd4b1)
```

## 스키마 조회

`schema`는 기본적으로 Avro 스키마를 출력합니다.

```
$ parquet schema samples/users.parquet
{
  "type" : "record",
  "name" : "User",
  "namespace" : "com.example",
  "doc" : "Sample user records",
  "fields" : [ {
    "name" : "id",
    "type" : "int"
  }, {
    "name" : "name",
    "type" : "string"
  }, {
    "name" : "email",
    "type" : [ "null", "string" ],
    "default" : null
  }, {
    "name" : "age",
    "type" : "int"
  }, {
    "name" : "score",
    "type" : "double"
  }, {
    "name" : "active",
    "type" : "boolean"
  }, {
    "name" : "signup_date",
    "type" : {
      "type" : "int",
      "logicalType" : "date"
    }
  }, {
    "name" : "last_login",
    "type" : {
      "type" : "long",
      "logicalType" : "timestamp-millis"
    }
  }, {
    "name" : "tags",
    "type" : {
      "type" : "array",
      "items" : "string"
    }
  }, {
    "name" : "address",
    "type" : {
      "type" : "record",
      "name" : "Address",
      "fields" : [ {
        "name" : "city",
        "type" : "string"
      }, {
        "name" : "zip",
        "type" : "string"
      } ]
    }
  } ]
}
```

Parquet 원래 스키마는 `--parquet` 옵션으로 확인합니다.

```
$ parquet schema --parquet samples/users.parquet
message com.example.User {
  required int32 id;
  required binary name (STRING);
  optional binary email (STRING);
  required int32 age;
  required double score;
  required boolean active;
  required int32 signup_date (DATE);
  required int64 last_login (TIMESTAMP(MILLIS,true));
  required group tags (LIST) {
    repeated binary array (STRING);
  }
  required group address {
    required binary city (STRING);
    required binary zip (STRING);
  }
}
```

`schema`는 Avro 파일에도 사용할 수 있습니다.

```
$ parquet schema samples/users.avro
{
  "type" : "record",
  "name" : "User",
  "namespace" : "com.example",
  "doc" : "Sample user records",
  "fields" : [ {
    "name" : "id",
    "type" : "int"
...
```

## 데이터 조회

`head`는 앞의 10건을, `cat`은 전체 레코드를 출력합니다. 두 명령 모두 `-n`으로 건수를 지정할 수 있습니다.

```
$ parquet head -n 3 samples/users.parquet
{"id": 1, "name": "Alice Kim", "email": "alice@example.com", "age": 34, "score": 91.5, "active": true, "signup_date": 19372, "last_login": 1790845950000, "tags": ["admin", "beta"], "address": {"city": "Seoul", "zip": "04524"}}
{"id": 2, "name": "Bob Lee", "email": null, "age": 28, "score": 78.25, "active": true, "signup_date": 19418, "last_login": 1790621100000, "tags": ["beta"], "address": {"city": "Busan", "zip": "48058"}}
{"id": 3, "name": "Charlie Park", "email": "charlie@example.com", "age": 45, "score": 66.0, "active": false, "signup_date": 19316, "last_login": 1781422215000, "tags": [], "address": {"city": "Incheon", "zip": "21554"}}
```

`date`는 1970-01-01부터의 일수, `timestamp-millis`는 epoch 밀리초 값 그대로 출력됩니다.

`-c`로 출력할 컬럼을 고릅니다. 중첩 그룹(`address`)은 그룹 전체를 고를 수 있습니다.

```
$ parquet cat -c name,age,address samples/users.parquet
{"name": "Alice Kim", "age": 34, "address": {"city": "Seoul", "zip": "04524"}}
{"name": "Bob Lee", "age": 28, "address": {"city": "Busan", "zip": "48058"}}
{"name": "Charlie Park", "age": 45, "address": {"city": "Incheon", "zip": "21554"}}
{"name": "Diana Choi", "age": 31, "address": {"city": "Seoul", "zip": "06236"}}
{"name": "Evan Jung", "age": 52, "address": {"city": "Daegu", "zip": "41911"}}
{"name": "Fiona Han", "age": 26, "address": {"city": "Gwangju", "zip": "61945"}}
{"name": "George Yoon", "age": 39, "address": {"city": "Daejeon", "zip": "35204"}}
{"name": "Hana Lim", "age": 23, "address": {"city": "Seoul", "zip": "03187"}}
{"name": "Ian Shin", "age": 47, "address": {"city": "Ulsan", "zip": "44543"}}
{"name": "Jane Oh", "age": 36, "address": {"city": "Suwon", "zip": "16490"}}
```

컬럼을 하나만 고르면 값만 출력됩니다.

```
$ parquet cat -c email samples/users.parquet
"alice@example.com"
null
"charlie@example.com"
"diana@example.com"
null
"fiona@example.com"
"george@example.com"
null
"ian@example.com"
"jane@example.com"
```

`cat`/`head`는 Avro 파일도 읽습니다.

```
$ parquet cat -n 2 samples/users.avro
{"id": 1, "name": "Alice Kim", "email": "alice@example.com", "age": 34, "score": 91.5, "active": true, "signup_date": 19372, "last_login": 1790845950000, "tags": ["admin", "beta"], "address": {"city": "Seoul", "zip": "04524"}}
{"id": 2, "name": "Bob Lee", "email": null, "age": 28, "score": 78.25, "active": true, "signup_date": 19418, "last_login": 1790621100000, "tags": ["beta"], "address": {"city": "Busan", "zip": "48058"}}
```

## INT96 타임스탬프

Hive, Impala, 이전 버전 Spark가 쓰는 INT96 타임스탬프 컬럼은 별도 옵션 없이 읽히며, `cat`/`head`에서 UTC 기준 ISO-8601 문자열로 출력됩니다.
나노초 정밀도까지 표시하고, 0인 소수 자릿수는 생략합니다.

```
$ parquet schema --parquet samples/events_int96.parquet
message event {
  required int32 event_id;
  required int32 user_id;
  required binary event_type (STRING);
  required int96 event_time;
  optional int96 processed_at;
  optional group detail {
    optional binary ip (STRING);
    optional int64 duration_ms;
  }
}
```

```
$ parquet cat samples/events_int96.parquet
{"event_id": 1, "user_id": 1, "event_type": "login", "event_time": "2026-10-01T09:12:30.123456789Z", "processed_at": "2026-10-01T09:12:31Z", "detail": {"ip": "10.0.0.11", "duration_ms": 120}}
{"event_id": 2, "user_id": 4, "event_type": "purchase", "event_time": "2026-10-15T21:05:42.500Z", "processed_at": "2026-10-15T21:05:43.250Z", "detail": {"ip": "10.0.0.24", "duration_ms": 3400}}
{"event_id": 3, "user_id": 6, "event_type": "login", "event_time": "2026-10-19T12:00:00Z", "processed_at": null, "detail": {"ip": "10.0.0.36", "duration_ms": 95}}
{"event_id": 4, "user_id": 2, "event_type": "logout", "event_time": "2026-09-28T18:45:00Z", "processed_at": "2026-09-28T18:45:02Z", "detail": null}
{"event_id": 5, "user_id": 7, "event_type": "view", "event_time": "2026-10-18T08:20:10.000001Z", "processed_at": "2026-10-18T08:20:11Z", "detail": {"ip": "10.0.0.47", "duration_ms": 15800}}
```

INT96 컬럼만 골라서 읽을 수도 있습니다.

```
$ parquet cat -c event_type,event_time samples/events_int96.parquet
{"event_type": "login", "event_time": "2026-10-01T09:12:30.123456789Z"}
{"event_type": "purchase", "event_time": "2026-10-15T21:05:42.500Z"}
{"event_type": "login", "event_time": "2026-10-19T12:00:00Z"}
{"event_type": "logout", "event_time": "2026-09-28T18:45:00Z"}
{"event_type": "view", "event_time": "2026-10-18T08:20:10.000001Z"}
```

`schema`에서는 INT96 컬럼이 12바이트 `fixed` 타입으로 표시됩니다.

```
$ parquet schema samples/events_int96.parquet
{
  "type" : "record",
  "name" : "event",
  "fields" : [ {
    "name" : "event_id",
    "type" : "int"
  }, {
    "name" : "user_id",
    "type" : "int"
  }, {
    "name" : "event_type",
    "type" : "string"
  }, {
    "name" : "event_time",
    "type" : {
      "type" : "fixed",
      "name" : "event_time",
      "doc" : "INT96 represented as byte[12]",
      "size" : 12
    }
  }, {
...
```

upstream과 같이 INT96 읽기를 끄려면 `parquet.avro.readInt96AsFixed=false`를 지정합니다. 이 경우 INT96 컬럼이 있는 파일은 읽을 수 없습니다.

```
$ JAVA_OPTS="-Dparquet.avro.readInt96AsFixed=false" parquet head -n 1 samples/events_int96.parquet
Argument error: INT96 is deprecated. As interim enable READ_INT96_AS_FIXED flag to read as byte array.
```

`parquet -D parquet.avro.readInt96AsFixed=false head ...`처럼 `-D`로도 지정할 수 있으며, `-D` 값이 `JAVA_OPTS`보다 우선합니다.

```
$ JAVA_OPTS=-Dparquet.avro.readInt96AsFixed=false parquet -D parquet.avro.readInt96AsFixed=true head -n 1 samples/events_int96.parquet
{"event_id": 1, "user_id": 1, "event_type": "login", "event_time": "2026-10-01T09:12:30.123456789Z", "processed_at": "2026-10-01T09:12:31Z", "detail": {"ip": "10.0.0.11", "duration_ms": 120}}
```

## 메타데이터 조회

### meta

파일 속성, 스키마, row group별 컬럼 통계를 출력합니다.

```
$ parquet meta samples/users.parquet

File path:  samples/users.parquet
Created by: parquet-mr version 1.18.1 (build f682a808de9b3d4d50bf6953a670640683dbd4b1)
Properties:
  parquet.avro.schema: {"type":"record","name":"User","namespace":"com.example","doc":"Sample user records","fields":[...]}
    writer.model.name: avro
Schema:
message com.example.User {
  ...
}


Row group 0:  count: 10  110.40 B records  start: 4  total(compressed): 1.078 kB total(uncompressed):1.073 kB 
--------------------------------------------------------------------------------
              type      encodings count     avg size   nulls   min / max
id            INT32     G   _     10        6.70 B     0       "1" / "10"
name          BINARY    G   _     10        15.20 B    0       "Alice Kim" / "Jane Oh"
email         BINARY    G   _     10        12.10 B    3       "alice@example.com" / "jane@example.com"
age           INT32     G   _     10        6.90 B     0       "23" / "52"
score         DOUBLE    G   _     10        8.30 B     0       "59.5" / "95.0"
active        BOOLEAN   G   _     10        4.50 B     0       "false" / "true"
signup_date   INT32     G   _     10        7.80 B     0       "2021-07-07" / "2026-01-03"
last_login    INT64     G   _     10        11.00 B    0       "2025-12-31T23:59:59.000+0000" / "2026-10-20T06:45:33.000+0000"
tags.array    BINARY    G _ R     13        10.15 B    2       "admin" / "mobile"
address.city  BINARY    G _ R     10        14.90 B    0       "Busan" / "Ulsan"
address.zip   BINARY    G   _     10        9.80 B     0       "03187" / "61945"
```

(`parquet.avro.schema` 값과 스키마 본문은 앞 절과 같아 생략했습니다.)

`encodings` 열의 의미는 다음과 같습니다.

- 첫 글자는 압축 코덱입니다: `_` 없음, `S` SNAPPY, `G` GZIP, `Z` ZSTD, `B` BROTLI, `4` LZ4, `F` LZ4_RAW
- 가운데에 `_`가 있으면 딕셔너리 페이지가 있습니다.
- 마지막 글자는 데이터 페이지 인코딩입니다: `_` PLAIN, `R` 딕셔너리(RLE_DICTIONARY), `D` 델타 인코딩

### footer

footer 전체를 JSON으로 출력합니다. 출력이 길어서 앞부분만 표시합니다.

```
$ parquet footer samples/users.parquet
{
  "fileMetaData" : {
    "schema" : {
      "name" : "com.example.User",
      "repetition" : "REPEATED",
      "logicalTypeAnnotation" : null,
      "id" : null,
      "fields" : [ {
        "name" : "id",
        "repetition" : "REQUIRED",
        "logicalTypeAnnotation" : null,
        "id" : null,
        "primitive" : "INT32",
        "length" : 0,
        "decimalMeta" : null,
        "columnOrder" : {
          "columnOrderName" : "TYPE_DEFINED_ORDER"
        }
      }, {
...
```

### pages

컬럼별 페이지 정보를 출력합니다. `0-D`는 딕셔너리 페이지, `0-0`, `0-1`은 데이터 페이지입니다(row group 번호-페이지 번호).

```
$ parquet pages samples/users.parquet

Column: id
--------------------------------------------------------------------------------
  page   type  enc  count   avg size   size       rows     nulls   min / max
  0-0    data  G _  10      4.40 B     44 B                        

...

Column: tags.array
--------------------------------------------------------------------------------
  page   type  enc  count   avg size   size       rows     nulls   min / max
  0-D    dict  G _  4       9.25 B     37 B      
  0-1    data  G R  13      2.92 B     38 B                        


Column: address.city
--------------------------------------------------------------------------------
  page   type  enc  count   avg size   size       rows     nulls   min / max
  0-D    dict  G _  8       9.75 B     78 B      
  0-1    data  G R  10      2.80 B     28 B                        
...
```

### dictionary

딕셔너리 인코딩된 컬럼의 딕셔너리 값을 출력합니다. `-c`는 필수입니다.

```
$ parquet dictionary -c address.city samples/users.parquet

Row group 0 dictionary for "address.city":
     0: "Seoul"
     1: "Busan"
     2: "Incheon"
     3: "Daegu"
     4: "Gwangju"
     5: "Daejeon"
     6: "Ulsan"
     7: "Suwon"
```

### column-size

컬럼별 크기를 출력합니다. `-s`는 크기순 정렬, `-p`는 비율을 백분율로 표시합니다.

```
$ parquet column-size -s -p samples/users.parquet
name-> Size In Bytes: 152 Size In Ratio: 13.7681%
address.city-> Size In Bytes: 149 Size In Ratio: 13.4964%
tags.array-> Size In Bytes: 132 Size In Ratio: 11.9565%
email-> Size In Bytes: 121 Size In Ratio: 10.9601%
last_login-> Size In Bytes: 110 Size In Ratio: 9.9638%
address.zip-> Size In Bytes: 98 Size In Ratio: 8.8768%
score-> Size In Bytes: 83 Size In Ratio: 7.5181%
signup_date-> Size In Bytes: 78 Size In Ratio: 7.0652%
age-> Size In Bytes: 69 Size In Ratio: 6.2500%
id-> Size In Bytes: 67 Size In Ratio: 6.0688%
active-> Size In Bytes: 45 Size In Ratio: 4.0761%
```

### column-index

컬럼 인덱스(페이지별 min/max)와 오프셋 인덱스를 출력합니다. `-c`로 컬럼을, `-r`로 row group을 지정할 수 있습니다.

```
$ parquet column-index -c age samples/users.parquet
row-group 0:
column index for column age:
Boundary order: ASCENDING
                      null count  min                                       max                                        rep level histogram   def level histogram
page-0                         0  23                                        52                                                      <none>                <none>

offset index for column age:
                          offset       compressed size       first row index       unencoded bytes
page-0                       344                    69                     0                     -
```

### size-stats

컬럼별 크기 통계를 출력합니다.

```
$ parquet size-stats samples/users.parquet

File path: samples/users.parquet

Row group 0
--------------------------------------------------------------------------------
column          unencoded bytes rep level histogram                      def level histogram                     
[id]            -               -                                        -                                       
[name]          90 B            -                                        -                                       
[email]         119 B           -                                        -                                       
[age]           -               -                                        -                                       
[score]         -               -                                        -                                       
[active]        -               -                                        -                                       
[signup_date]   -               -                                        -                                       
[last_login]    -               -                                        -                                       
[tags, array]   55 B            [10, 3]                                  -                                       
[address, city] 56 B            -                                        -                                       
[address, zip]  50 B            -                                        -                                       
```

### check-stats, scan

`check-stats`는 손상된 통계가 있는지 검사하고, `scan`은 모든 레코드를 읽어서 건수와 소요 시간을 출력합니다. 파일이 정상적으로 읽히는지 확인할 때 유용합니다.

```
$ parquet check-stats samples/users.parquet
samples/users.parquet has no corrupt stats

$ parquet scan samples/users.parquet
Scanned 10 records from 1 file(s)
Time: 0.248 s
```

## Bloom filter 조회

`bloom-filter`는 Bloom filter가 있는 컬럼에서 값이 존재할 수 있는지 확인합니다. `-c`(컬럼)와 `-v`(값, 쉼표로 구분)는 필수입니다.
Bloom filter는 오탐(false positive)이 있을 수 있으므로 결과는 "있을 수 있음(maybe exists)" 또는 "없음(NOT exists)"입니다.

```
$ parquet bloom-filter -c event_type -v login,signup samples/events_int96.parquet

Row group 0: 
--------------------------------------------------------------------------------
value login maybe exists.
value signup NOT exists.
```

Bloom filter가 없는 컬럼은 다음과 같이 표시됩니다.

```
$ parquet bloom-filter -c name -v "Alice Kim" samples/users.parquet
Row group 0: 
--------------------------------------------------------------------------------
column name has no bloom filter
```

`parquet` 명령으로 만드는 파일에는 Bloom filter를 넣을 수 없습니다. `--conf parquet.bloom.filter.enabled=true`를 지정해도 적용되지 않습니다.
`samples/events_int96.parquet`의 Bloom filter는 `samples/Int96Sample.java`에서 Parquet API로 넣은 것입니다.

## Avro/CSV에서 Parquet으로 변환

### convert

Avro 파일을 Parquet으로 변환합니다. 기본 압축 코덱은 GZIP입니다(`meta`의 `G`).

```
$ parquet convert samples/users.avro -o out/users.parquet
```

`--compression-codec`으로 코덱을 지정합니다(`uncompressed`, `snappy`, `gzip`, `zstd`, `lz4_raw`, `brotli` 등).

```
$ parquet convert samples/users.avro -o out/users_zstd.parquet --compression-codec zstd

$ parquet meta out/users_zstd.parquet
...
Row group 0:  count: 10  111.10 B records  start: 4  total(compressed): 1.085 kB total(uncompressed):1.073 kB 
--------------------------------------------------------------------------------
              type      encodings count     avg size   nulls   min / max
id            INT32     Z   _     10        7.50 B     0       "1" / "10"
name          BINARY    Z   _     10        14.20 B    0       "Alice Kim" / "Jane Oh"
...
```

`-c`로 일부 컬럼만 변환합니다.

```
$ parquet convert samples/users.avro -o out/users_small.parquet -c id,name,email

$ parquet schema --parquet out/users_small.parquet
message com.example.User {
  required int32 id;
  required binary name (STRING);
  optional binary email (STRING);
}
```

출력 파일이 이미 있으면 실패하므로 덮어쓰려면 `--overwrite`를 지정합니다.

```
$ parquet convert samples/users.avro -o out/users.parquet
Unknown error
org.apache.hadoop.fs.FileAlreadyExistsException: File already exists: file:/.../out/users.parquet
...

$ parquet convert samples/users.avro -o out/users.parquet --overwrite
```

### csv-schema

CSV 내용에서 Avro 스키마를 추론합니다. `--record-name`은 필수입니다. 모든 필드가 nullable로 추론되며, 숫자가 아닌 값(`true`, 날짜)은 string이 됩니다.

```
$ parquet csv-schema samples/users.csv --record-name User
{
  "type" : "record",
  "name" : "User",
  "fields" : [ {
    "name" : "id",
    "type" : [ "null", "long" ],
    "doc" : "Type inferred from '1'",
    "default" : null
  }, {
    "name" : "name",
    "type" : [ "null", "string" ],
    "doc" : "Type inferred from 'Alice Kim'",
    "default" : null
  }, {
    "name" : "email",
    "type" : [ "null", "string" ],
    "doc" : "Type inferred from 'alice@example.com'",
    "default" : null
  }, {
    "name" : "age",
    "type" : [ "null", "long" ],
    "doc" : "Type inferred from '34'",
    "default" : null
  }, {
    "name" : "score",
    "type" : [ "null", "double" ],
    "doc" : "Type inferred from '91.5'",
    "default" : null
  }, {
    "name" : "active",
    "type" : [ "null", "string" ],
    "doc" : "Type inferred from 'true'",
    "default" : null
  }, {
    "name" : "signup_date",
    "type" : [ "null", "string" ],
    "doc" : "Type inferred from '2023-01-15'",
    "default" : null
  }, {
    "name" : "city",
    "type" : [ "null", "string" ],
    "doc" : "Type inferred from 'Seoul'",
    "default" : null
  } ]
}
```

`--require`로 null을 허용하지 않을 필드를 지정하고, `-o`로 스키마를 파일에 저장합니다.

```
$ parquet csv-schema samples/users.csv --record-name User --require id --require name -o out/users_csv.avsc
```

### convert-csv

CSV를 Parquet으로 변환합니다. `-s`로 스키마를 지정하지 않으면 `csv-schema`와 같은 방식으로 추론합니다.

```
$ parquet convert-csv samples/users.csv -s out/users_csv.avsc -o out/users_csv.parquet

$ parquet head -n 2 out/users_csv.parquet
{"id": 1, "name": "Alice Kim", "email": "alice@example.com", "age": 34, "score": 91.5, "active": "true", "signup_date": "2023-01-15", "city": "Seoul"}
{"id": 2, "name": "Bob Lee", "email": "", "age": 28, "score": 78.25, "active": "true", "signup_date": "2023-03-02", "city": "Busan"}
```

CSV의 빈 값은 null이 아니라 빈 문자열(`""`)로 들어갑니다. 구분자, 따옴표, 헤더 등은 `--delimiter`, `--quote`, `--escape`, `--no-header`, `--header`, `--skip-lines`로 지정합니다.

## Parquet에서 Avro로 변환

`to-avro`는 Parquet 파일을 Avro로 변환합니다. 결과는 [`avro`](avro.md) 명령으로 확인할 수 있습니다.

```
$ parquet to-avro samples/users.parquet -o out/users.avro
```

INT96 컬럼은 12바이트 `fixed` 값 그대로 기록됩니다(타임스탬프로 변환되지 않음).

```
$ parquet to-avro samples/events_int96.parquet -o out/events.avro
```

## Parquet 파일 재작성

`rewrite`는 기존 Parquet 파일에서 컬럼을 제거하거나 마스킹하고, 압축 코덱을 바꾸거나 여러 파일을 합쳐서 새 파일로 씁니다. `-i`(입력, 쉼표로 여러 개)와 `-o`(출력)는 필수입니다.

### 컬럼 제거

```
$ parquet rewrite -i samples/users.parquet -o out/users_pruned.parquet --prune-columns email,tags

$ parquet schema --parquet out/users_pruned.parquet
message com.example.User {
  required int32 id;
  required binary name (STRING);
  required int32 age;
  required double score;
  required boolean active;
  required int32 signup_date (DATE);
  required int64 last_login (TIMESTAMP(MILLIS,true));
  required group address {
    required binary city (STRING);
    required binary zip (STRING);
  }
}
```

### 컬럼 마스킹

`--mask-mode nullify`는 컬럼 값을 모두 null로 바꿉니다. nullable 컬럼에만 사용할 수 있습니다.

```
$ parquet rewrite -i samples/users.parquet -o out/users_masked.parquet --mask-columns email --mask-mode nullify

$ parquet cat -c name,email out/users_masked.parquet
{"name": "Alice Kim", "email": null}
{"name": "Bob Lee", "email": null}
{"name": "Charlie Park", "email": null}
{"name": "Diana Choi", "email": null}
{"name": "Evan Jung", "email": null}
{"name": "Fiona Han", "email": null}
{"name": "George Yoon", "email": null}
{"name": "Hana Lim", "email": null}
{"name": "Ian Shin", "email": null}
{"name": "Jane Oh", "email": null}
```

required 컬럼을 지정하면 실패합니다.

```
$ parquet rewrite -i samples/users.parquet -o out/m2.parquet --mask-columns name --mask-mode nullify
Unknown error
java.io.IOException: Required column [name] cannot be nullified
...
```

### 압축 코덱 변경

`-c`(`--compression-codec`)로 코덱을 바꿉니다(`SNAPPY`, `ZSTD`, `GZIP` 등, 대소문자 무관).

```
$ parquet rewrite -i samples/users.parquet -o out/users_snappy.parquet -c SNAPPY
```

재작성한 파일의 `meta`에는 원본 작성 정보가 `original.created.by`로 남습니다.

```
$ parquet meta out/users_snappy.parquet

File path:  out/users_snappy.parquet
Created by: parquet-mr version 1.18.1 (build f682a808de9b3d4d50bf6953a670640683dbd4b1)
Properties:
  original.created.by: parquet-mr version 1.18.1 (build f682a808de9b3d4d50bf6953a670640683dbd4b1)
...
```

### 파일 병합

스키마가 같은 여러 파일을 하나로 합칩니다.

```
$ parquet rewrite -i samples/users.parquet,samples/users.parquet -o out/users_merged.parquet

$ parquet scan out/users_merged.parquet
Scanned 20 records from 1 file(s)
Time: 0.253 s
```

`prune`, `trans-compression`, `masking` 명령도 있지만 deprecated 상태이며 2.0.0에서 제거될 예정이므로 `rewrite`를 사용합니다.

## 주의 사항

- **optional 그룹 안의 컬럼 하나만 선택할 수 없음**: optional 그룹(`detail`) 안의 컬럼을 고르면 오류가 발생합니다. upstream의 기존 버그이며, `-c detail`처럼 그룹 전체를 고르면 됩니다.

  ```
  $ parquet cat -c detail.ip samples/events_int96.parquet
  Unknown error
  org.apache.avro.AvroTypeException: Invalid default for field detail: null not a {"type":"record","name":"detail","fields":[{"name":"ip","type":["null","string"],"default":null}]}
  ...

  $ parquet cat -c detail samples/events_int96.parquet
  {"ip": "10.0.0.11", "duration_ms": 120}
  {"ip": "10.0.0.24", "duration_ms": 3400}
  {"ip": "10.0.0.36", "duration_ms": 95}
  null
  {"ip": "10.0.0.47", "duration_ms": 15800}
  ```

- **오류 메시지**: 파일이 없는 등의 오류는 `Unknown error`와 스택 트레이스로 출력됩니다.

  ```
  $ parquet head samples/nope.parquet
  Unknown error
  java.io.FileNotFoundException: File file:/.../samples/nope.parquet does not exist
  	at org.apache.hadoop.fs.RawLocalFileSystem.deprecatedGetFileStatus(RawLocalFileSystem.java:668)
  ...
  ```

  (경로 일부와 스택 트레이스는 생략했습니다.)

- **CRC 파일**: 결과 파일과 함께 `.파일명.crc` 체크섬 파일이 생깁니다. Hadoop 로컬 파일 시스템이 만드는 파일이며 필요 없으면 지워도 됩니다.
