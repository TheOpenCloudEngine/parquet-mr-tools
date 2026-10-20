# avro 명령 사용법

`avro`는 Avro 데이터 파일의 스키마와 데이터를 조회하고, JSON과 서로 변환하거나 압축 코덱을 바꾸는 명령입니다.
Apache Avro 1.12.2의 `avro-tools`를 그대로 빌드한 것입니다.

이 문서의 모든 예시는 저장소의 [`samples/`](samples/) 파일로 실제로 실행한 결과입니다(JDK 17, `parquet-mr-tools-1.18.1-1` RPM).
명령은 저장소 루트에서 실행했고, 결과 파일은 `out/` 디렉터리에 썼습니다. 샘플 파일 설명은 [parquet.md의 샘플 파일](parquet.md#샘플-파일)을 참고하세요.

```
mkdir -p out
```

> Hadoop이 출력하는 `WARN util.NativeCodeLoader: Unable to load native-hadoop library ...` 경고와 `INFO tool.DataFileReadTool: Reading schema from file ...` 메시지는 stderr로 출력되며 결과와 무관하므로 예시에서 생략했습니다.

## 목차

- [기본 사용법](#기본-사용법)
- [명령 목록](#명령-목록)
- [스키마와 메타데이터 조회](#스키마와-메타데이터-조회)
- [데이터 조회](#데이터-조회)
- [JSON에서 Avro 파일 만들기](#json에서-avro-파일-만들기)
- [레코드 추출과 파일 합치기](#레코드-추출과-파일-합치기)
- [압축 코덱 변경](#압축-코덱-변경)
- [스키마 도구](#스키마-도구)
- [Parquet과 함께 사용하기](#parquet과-함께-사용하기)
- [주의 사항](#주의-사항)

## 기본 사용법

```
avro <도구> [옵션] [인자...]
```

- 인자 없이 `avro`를 실행하면 도구 목록이 출력됩니다.
- 도구 이름만 쓰면 대부분 사용법이 출력됩니다(예: `avro tojson`).
- 입력 파일 대신 `-`를 쓰면 stdin에서 읽고, 출력 파일 대신 `-`를 쓰면 stdout으로 씁니다(지원하는 도구에 한함).
- JVM 옵션은 환경 변수 `JAVA_OPTS`로 전달합니다.

```
$ avro tojson
tojson [--pretty] [--head[=X]] input-file

Dumps an Avro data file as JSON, record per line or pretty.
A dash ('-') can be given as an input file to use stdin

Option                         Description                                  
------                         -----------                                  
--head [String]                Converts the first X records (default is 10).
--pretty                       Turns on pretty printing.                    
--reader-schema [String]       Reader schema                                
--reader-schema-file [String]  Reader schema file                           
```

## 명령 목록

자주 쓰는 도구입니다. 전체 목록은 `avro`를 인자 없이 실행해서 확인합니다.

| 도구 | 설명 |
|---|---|
| `getschema` | 데이터 파일의 스키마 출력 |
| `getmeta` | 데이터 파일의 메타데이터(스키마, 코덱 등) 출력 |
| `count` | 레코드 수 출력 |
| `tojson` | 데이터 파일을 JSON으로 출력 |
| `fromjson` | JSON 레코드로 데이터 파일 생성 |
| `cat` | 레코드 일부를 추출해서 새 파일로 저장 |
| `concat` | 같은 스키마/코덱의 파일을 다시 압축하지 않고 합침 |
| `recodec` | 압축 코덱 변경 |
| `random` | 스키마에 맞는 임의 데이터 파일 생성 |
| `canonical` | 스키마를 정규형(Parsing Canonical Form)으로 변환 |
| `fingerprint` | 스키마 지문(fingerprint) 출력 |
| `compile` | 스키마에서 Java 클래스 생성 |
| `idl`, `idl2schemata` | Avro IDL을 JSON 스키마/프로토콜로 변환 |
| `totext`, `fromtext` | 텍스트 파일과 변환 |
| `repair` | 손상된 데이터 파일 복구 |

## 스키마와 메타데이터 조회

### getschema

```
$ avro getschema samples/users.avro
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

### getmeta

파일 헤더의 메타데이터를 `키<탭>값` 형식으로 출력합니다.

```
$ avro getmeta samples/users.avro
avro.schema	{"type":"record","name":"User","namespace":"com.example","doc":"Sample user records","fields":[{"name":"id","type":"int"},{"name":"name","type":"string"},{"name":"email","type":["null","string"],"default":null},{"name":"age","type":"int"},{"name":"score","type":"double"},{"name":"active","type":"boolean"},{"name":"signup_date","type":{"type":"int","logicalType":"date"}},{"name":"last_login","type":{"type":"long","logicalType":"timestamp-millis"}},{"name":"tags","type":{"type":"array","items":"string"}},{"name":"address","type":{"type":"record","name":"Address","fields":[{"name":"city","type":"string"},{"name":"zip","type":"string"}]}}]}
avro.codec	snappy
```

특정 키만 보려면 `--key`를 **파일 이름 뒤에** 씁니다.

```
$ avro getmeta samples/users.avro --key avro.codec
snappy
```

`--key`를 파일 이름 앞에 쓰면 `--key`를 파일 이름으로 인식해서 실패합니다.

```
$ avro getmeta --key avro.codec samples/users.avro
Exception in thread "main" java.io.FileNotFoundException: File --key does not exist
...
```

### count

```
$ avro count samples/users.avro
10
```

stdin에서도 읽을 수 있습니다.

```
$ cat samples/users.avro | avro count -
10
```

## 데이터 조회

### tojson

레코드를 한 줄에 하나씩 JSON으로 출력합니다. `--head=N`으로 앞의 N건만 출력합니다.

```
$ avro tojson --head=2 samples/users.avro
{"id":1,"name":"Alice Kim","email":{"string":"alice@example.com"},"age":34,"score":91.5,"active":true,"signup_date":19372,"last_login":1790845950000,"tags":["admin","beta"],"address":{"city":"Seoul","zip":"04524"}}
{"id":2,"name":"Bob Lee","email":null,"age":28,"score":78.25,"active":true,"signup_date":19418,"last_login":1790621100000,"tags":["beta"],"address":{"city":"Busan","zip":"48058"}}
```

출력은 Avro JSON 인코딩이므로 union 타입 값은 `{"string": "..."}`처럼 타입 이름으로 감싸집니다. `date`, `timestamp-millis`는 숫자 그대로 출력됩니다.

`--pretty`로 보기 좋게 출력합니다.

```
$ avro tojson --pretty --head=1 samples/users.avro
{
  "id" : 1,
  "name" : "Alice Kim",
  "email" : {
    "string" : "alice@example.com"
  },
  "age" : 34,
  "score" : 91.5,
  "active" : true,
  "signup_date" : 19372,
  "last_login" : 1790845950000,
  "tags" : [ "admin", "beta" ],
  "address" : {
    "city" : "Seoul",
    "zip" : "04524"
  }
}
```

다른 명령과 조합해서 쓸 수 있습니다.

```
$ avro tojson samples/users.avro | grep Seoul
{"id":1,"name":"Alice Kim","email":{"string":"alice@example.com"},"age":34,"score":91.5,"active":true,"signup_date":19372,"last_login":1790845950000,"tags":["admin","beta"],"address":{"city":"Seoul","zip":"04524"}}
{"id":4,"name":"Diana Choi","email":{"string":"diana@example.com"},"age":31,"score":88.75,"active":true,"signup_date":19763,"last_login":1792098342000,"tags":["admin"],"address":{"city":"Seoul","zip":"06236"}}
{"id":8,"name":"Hana Lim","email":null,"age":23,"score":59.5,"active":true,"signup_date":20456,"last_login":1792478733000,"tags":[],"address":{"city":"Seoul","zip":"03187"}}
```

`--reader-schema-file`로 읽기 스키마를 지정하면 스키마 변환(schema evolution) 결과를 확인할 수 있습니다. 아래는 쓰기 스키마와 같은 스키마로 읽은 예시입니다(10건 중 앞의 2건).

```
$ avro tojson --reader-schema-file samples/users.avsc samples/users.avro
{"id":1,"name":"Alice Kim","email":{"string":"alice@example.com"},"age":34,"score":91.5,"active":true,"signup_date":19372,"last_login":1790845950000,"tags":["admin","beta"],"address":{"city":"Seoul","zip":"04524"}}
{"id":2,"name":"Bob Lee","email":null,"age":28,"score":78.25,"active":true,"signup_date":19418,"last_login":1790621100000,"tags":["beta"],"address":{"city":"Busan","zip":"48058"}}
...
```

## JSON에서 Avro 파일 만들기

`fromjson`은 Avro JSON 인코딩으로 된 레코드(한 줄에 하나)를 읽어서 데이터 파일을 **stdout으로** 씁니다. 결과는 리다이렉션(`>`)으로 저장합니다.
스키마는 `--schema-file`(파일) 또는 `--schema`(문자열)로 지정합니다.

```
$ avro fromjson --schema-file samples/users.avsc samples/users.json > out/users_null.avro

$ avro getmeta out/users_null.avro --key avro.codec
null
```

입력 JSON은 `avro tojson` 출력과 같은 Avro JSON 인코딩이어야 합니다. 예를 들어 nullable 필드 값은 `{"string": "alice@example.com"}` 또는 `null`로 써야 합니다(`samples/users.json` 참고).

`--codec`으로 압축 코덱을, `--level`로 압축 레벨을 지정합니다(기본은 압축 없음).

```
$ avro fromjson --schema-file samples/users.avsc --codec deflate --level 9 samples/users.json > out/users_deflate.avro

$ avro getmeta out/users_deflate.avro --key avro.codec
deflate
```

## 레코드 추출과 파일 합치기

### cat

`--offset`부터 `--limit`건을 추출해서 새 파일로 저장합니다. `--samplerate`로 일정 비율만 추출할 수도 있습니다.

```
$ avro cat --offset 2 --limit 3 samples/users.avro out/users_part.avro
List of input files:
samples/users.avro
3 records written.

$ avro tojson out/users_part.avro
{"id":3,"name":"Charlie Park","email":{"string":"charlie@example.com"},"age":45,"score":66.0,"active":false,"signup_date":19316,"last_login":1781422215000,"tags":[],"address":{"city":"Incheon","zip":"21554"}}
{"id":4,"name":"Diana Choi","email":{"string":"diana@example.com"},"age":31,"score":88.75,"active":true,"signup_date":19763,"last_login":1792098342000,"tags":["admin"],"address":{"city":"Seoul","zip":"06236"}}
{"id":5,"name":"Evan Jung","email":null,"age":52,"score":72.0,"active":false,"signup_date":18815,"last_login":1767225599000,"tags":["legacy"],"address":{"city":"Daegu","zip":"41911"}}
```

### concat

스키마, 메타데이터, 코덱이 같은 파일을 다시 압축하지 않고 합칩니다. 마지막 인자가 출력 파일입니다.

```
$ avro concat samples/users.avro samples/users.avro out/users_concat.avro

$ avro count out/users_concat.avro
20
```

코덱이 다르면 종료 코드 3으로 실패합니다(스키마가 다르면 1, 메타데이터가 다르면 2). 이때도 빈 출력 파일이 만들어지므로 지워야 합니다.

```
$ avro concat samples/users.avro out/users_deflate.avro out/fail.avro
input files have different codecs
$ echo $?
3
```

## 압축 코덱 변경

`recodec`은 압축 코덱을 바꿔서 새 파일로 씁니다. 사용할 수 있는 코덱은 `null`, `deflate`, `snappy`, `bzip2`, `xz`, `zstandard`입니다.

```
$ avro recodec --codec zstandard samples/users.avro out/users_zstd.avro

$ avro getmeta out/users_zstd.avro --key avro.codec
zstandard
```

`xz`는 `--level`을 지정해야 합니다. 지정하지 않으면 기본값 -1이 xz에서 지원되지 않아 실패합니다(`fromjson --codec xz`도 같음).

```
$ avro recodec --codec xz samples/users.avro out/users_xz.avro
Exception in thread "main" org.tukaani.xz.UnsupportedOptionsException: Unsupported preset: -1
...

$ avro recodec --codec xz --level 6 samples/users.avro out/users_xz.avro

$ avro getmeta out/users_xz.avro --key avro.codec
xz
```

## 스키마 도구

### random

스키마에 맞는 임의 데이터 파일을 만듭니다. 테스트 데이터를 만들 때 유용합니다. `--seed`를 지정하면 같은 데이터가 만들어집니다.

```
$ avro random --schema-file samples/users.avsc --count 3 --seed 42 --codec null out/random.avro

$ avro tojson --head=1 out/random.avro
{"id":-1170105035,"name":"xju","email":{"string":"stshcbrbhgusjanbnsqfimvfg"},"age":900944861,"score":0.2506613258416336,"active":false,"signup_date":739670425,"last_login":2926660232063067186,"tags":["pxmajhkhsspigkknjld","tcbttxtaypbdexilk","satdrhsjtxewvxujxebbduphe","ljvawvewbgugugevdixchwx","ccwnhwsarcixcvxeddswqfkvfgyabrqjv"],"address":{"city":"kpufeeqyjqiggmvervok","zip":"yklurgxsqdqeossyjsdntcwqefasnxtfjgixagl"}}
```

### canonical

스키마를 정규형으로 변환합니다. 입력과 출력 두 인자가 필요하며, `-`는 stdin/stdout입니다. `doc`, `default`, `logicalType` 같은 속성은 빠집니다.

```
$ avro canonical samples/users.avsc -
{"name":"com.example.User","type":"record","fields":[{"name":"id","type":"int"},{"name":"name","type":"string"},{"name":"email","type":["null","string"]},{"name":"age","type":"int"},{"name":"score","type":"double"},{"name":"active","type":"boolean"},{"name":"signup_date","type":"int"},{"name":"last_login","type":"long"},{"name":"tags","type":{"type":"array","items":"string"}},{"name":"address","type":{"name":"com.example.Address","type":"record","fields":[{"name":"city","type":"string"},{"name":"zip","type":"string"}]}}]}
```

### fingerprint

스키마 지문을 출력합니다. 기본 알고리즘은 `CRC-64-AVRO`이며, `--fingerprint`로 `MD5`, `SHA-256`을 지정할 수 있습니다.

```
$ avro fingerprint samples/users.avsc
e07fd7996612e7c4 samples/users.avsc

$ avro fingerprint --fingerprint SHA-256 samples/users.avsc
382635ffe264db9a43694f9756c85234034bb839896e202c7475c220405b427b samples/users.avsc
```

### compile

스키마에서 Java 클래스를 생성합니다.

```
$ avro compile schema samples/users.avsc out/java
Input files to compile:
  samples/users.avsc

$ find out/java -name "*.java"
out/java/com/example/User.java
out/java/com/example/Address.java
```

## Parquet과 함께 사용하기

[`parquet`](parquet.md) 명령으로 Avro와 Parquet을 서로 변환할 수 있습니다.

```
$ parquet convert samples/users.avro -o out/users.parquet
$ parquet to-avro samples/users.parquet -o out/users.avro
```

Parquet의 INT96 타임스탬프 컬럼은 Avro로 변환하면 12바이트 `fixed` 값이 되며, `avro tojson`에서는 바이트 문자열로 출력됩니다. 날짜/시간으로 보려면 Parquet 파일을 `parquet cat`으로 조회하세요.

```
$ parquet to-avro samples/events_int96.parquet -o out/events.avro

$ avro tojson --head=1 out/events.avro
{"event_id":1,"user_id":1,"event_type":"login","event_time":"\u00159U]&\u001E\u0000\u0000%\u0000","processed_at":{"processed_at":"\u00006&\u001E\u0000\u0000%\u0000"},"detail":{"detail":{"ip":{"string":"10.0.0.11"},"duration_ms":{"long":120}}}}
```

## 주의 사항

- **옵션 위치**: 도구마다 옵션 위치가 다릅니다. 예를 들어 `getmeta`의 `--key`는 파일 이름 뒤에 써야 합니다. 사용법은 도구 이름만 실행해서 확인하세요.
- **오류 출력**: 잘못된 옵션이나 파일 오류는 Java 예외와 스택 트레이스로 출력됩니다. `--help` 옵션은 지원하지 않습니다.

  ```
  $ avro fingerprint --help
  Exception in thread "main" joptsimple.UnrecognizedOptionException: help is not a recognized option
  ...
  ```

- **CRC 파일**: 결과 파일과 함께 `.파일명.crc` 체크섬 파일이 생깁니다. Hadoop 로컬 파일 시스템이 만드는 파일이며 필요 없으면 지워도 됩니다.
