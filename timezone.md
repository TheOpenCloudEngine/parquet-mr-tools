# Parquet Timestamp와 시간대: NiFi, Impala, Spark

같은 Parquet 파일의 timestamp가 NiFi, Impala에서는 9시간 이르게(UTC로), Spark에서는 한국 시간으로 보이는 이유와 대응 방법을 정리한 문서입니다.

> 이 문서는 각 프로젝트의 공식 문서와 커뮤니티 자료를 바탕으로 정리했으며, Spark, Impala, NiFi로 직접 실험한 결과는 아닙니다.
> 특히 NiFi 동작은 버전 차이가 커서 일부는 커뮤니티 보고에 기대고 있으므로, 운영 환경의 버전과 설정으로 확인하세요.
> 마지막 절의 `parquet` 명령 예시는 이 저장소의 샘플 파일로 실제 실행한 결과입니다.

## 목차

- [요약](#요약)
- [Parquet에 저장되는 값](#parquet에-저장되는-값)
- [엔진별 표시 방식](#엔진별-표시-방식)
- [반대 방향의 문제: Impala가 쓴 INT96](#반대-방향의-문제-impala가-쓴-int96)
- [+9 대신 권장하는 방법](#9-대신-권장하는-방법)
- [parquet 명령으로 확인하기](#parquet-명령으로-확인하기)
- [참고 자료](#참고-자료)

## 요약

세 엔진은 저장된 값을 다르게 해석하는 것이 아니라, **같은 시점(instant)을 어느 시간대 기준으로 표시하는지**가 다릅니다.

* Parquet 파일에는 timestamp가 UTC 기준의 시점으로 저장됩니다. 서울에서 `2026-10-01 09:00`(KST)을 Spark로 저장하면 파일에는 `2026-10-01T00:00Z`가 들어갑니다.
* Spark는 이 값을 session time zone(기본값은 JVM의 현지 시간대, 서울 서버라면 Asia/Seoul)으로 바꿔서 `09:00`으로 보여 줍니다.
* Impala와 NiFi는 별도 설정이 없으면 UTC 그대로 `00:00`으로 보여 줍니다. 그래서 +9를 해야 한국 시간이 됩니다.

다만 데이터를 어느 엔진이 썼는지에 따라 저장된 값 자체가 다를 수 있으므로(아래 [Impala가 쓴 INT96](#반대-방향의-문제-impala가-쓴-int96)), +9를 고정으로 더하는 방식은 경우에 따라 9시간 어긋납니다.

## Parquet에 저장되는 값

Parquet 명세의 TIMESTAMP 논리 타입에는 `isAdjustedToUTC` 속성이 있습니다.

| `isAdjustedToUTC` | 의미 | 표시 방법 (명세) |
|---|---|---|
| `true` | UTC 기준의 한 시점(instant) | 보통 사용자의 현지 시간대로 변환해서 표시하며, 실행 환경에 따라 다르게 보일 수 있음 |
| `false` | 시간대 없는 벽시계 시각(local) | 시간대와 무관하게 항상 같게 표시 |

INT96은 이 속성이 없는 비표준(구) 형식입니다. 값이 무엇을 뜻하는지는 쓴 엔진에 따라 다릅니다.

| 쓴 엔진 | INT96에 저장하는 값 |
|---|---|
| Hive, Spark | 현지 시각을 **UTC로 변환한** 시점 |
| Impala | 시간대 변환 없이 **벽시계 시각 그대로** |

Spark는 Parquet을 쓸 때 기본으로 INT96을 사용합니다(`spark.sql.parquet.outputTimestampType` 기본값 `INT96`).

## 엔진별 표시 방식

### Spark

* 기본 TIMESTAMP 타입은 `TIMESTAMP_LTZ`(local time zone)로, "session local time-zone 기준의 절대 시점"입니다(`spark.sql.timestampType` 기본값 `TIMESTAMP_LTZ`).
* `spark.sql.session.timeZone`의 기본값은 "(value of local timezone)", 즉 JVM의 현지 시간대입니다.
* 따라서 서울 시간대 서버에서는 UTC로 저장된 시점을 자동으로 한국 시간으로 표시합니다. 서버 시간대가 UTC면 Spark도 UTC로 표시합니다.
* INT96은 `spark.sql.parquet.int96AsTimestamp`(기본 `true`)에 따라 timestamp로 읽습니다.
* Spark 3.4부터는 `isAdjustedToUTC = false`인 컬럼을 `TIMESTAMP_NTZ`로 읽습니다(`spark.sql.parquet.inferTimestampNTZ.enabled`, Spark가 쓰지 않은 파일에 한함).

### Impala

* Impala의 TIMESTAMP는 시간대 정보가 없는 값이며, "기본적으로 UTC로 저장하고 해석"합니다.
* **INT96**: "읽거나 쓸 때 시간대 조정을 하지 않습니다." 따라서 Hive/Spark가 UTC로 변환해 저장한 값이 UTC 그대로 보입니다.
* **INT64** (`TIMESTAMP_MILLIS`/`TIMESTAMP_MICROS`, Impala 3.2 이상): "항상 UTC로 정규화된 값으로 보고 UTC에서 현지 시간으로 변환"합니다. `TIMESTAMP` 논리 타입은 Parquet 메타데이터(`isAdjustedToUTC`)에 따라 변환 여부를 정합니다.
* 여기서 "현지" 시간대는 다음 순서로 정해집니다: `TIMEZONE` 쿼리 옵션 → `$TZ` 환경 변수 → impalad coordinator가 실행되는 서버의 시스템 시간대. 서버가 UTC로 설정되어 있으면 결과적으로 UTC로 보입니다.
* `--convert_legacy_hive_parquet_utc_timestamps`(impalad 시작 옵션, 기본 꺼짐): 켜면 Hive가 쓴 INT96 파일에 Hive와 같은 UTC→현지 시간 변환을 적용합니다. Impala 3.0 이하에서는 성능 저하가 심했고, 3.1부터 대부분 개선되었지만 성능 때문에 여전히 기본은 꺼져 있습니다.
* `--use_local_tz_for_unix_timestamp_conversions`: TIMESTAMP를 현지 시간대 기준으로 다루게 합니다. 문서에서는 위 옵션과 함께 사용하는 것을 안내합니다.

### NiFi

* NiFi의 레코드 처리와 Parquet 쓰기는 Avro 스키마를 기반으로 하며, timestamp는 Avro `timestamp-millis`(epoch 밀리초, 즉 UTC 기준 시점)로 다룹니다. ExecuteSQL에서 "Use Avro Logical Types"를 켜면 TIMESTAMP를 epoch 밀리초 `long`으로 씁니다.
* 문자열로 바꿀 때 RecordPath `format()` 함수의 기본 시간대는 GMT입니다. 시간대를 지정하지 않으면 UTC 기준 시각이 나옵니다.
* 시간대 없는 날짜 문자열(예: CSV의 `yyyy-MM-dd HH:mm:ss`)을 JVM 시간대와 무관하게 UTC로 해석했다는 사용자 보고가 있습니다.
* DB에서 가져오는 경우, 시간대 없는 DB 시각을 epoch 시점으로 바꾸는 단계(JDBC 드라이버, JVM 시간대)에서 이미 어긋날 수 있습니다. 관련 버그(NIFI-6442, Avro logical type 사용 시 날짜 변환 오류)는 1.10.0에서 수정된 것으로 기록되어 있습니다.

### 비교

서울에서 `2026-10-01 09:00`(KST)을 Spark로 저장한 경우(파일에는 `2026-10-01T00:00Z`):

| 엔진 | 표시 기준 시간대 | 표시되는 값 |
|---|---|---|
| Spark | session time zone (기본: JVM 현지 시간대) | `09:00` |
| Impala | INT96: 변환 없음 / INT64: `TIMEZONE` → `$TZ` → 서버 시간대 | `00:00` (서버가 UTC인 경우) |
| NiFi | UTC/GMT (시간대 미지정 시) | `00:00` |

## 반대 방향의 문제: Impala가 쓴 INT96

Impala는 INT96에 벽시계 시각을 변환 없이 저장합니다. 서울에서 Impala로 `09:00`을 저장하면 파일에는 `09:00`이 그대로 들어가고, Spark는 이를 UTC 시점(`09:00Z`)으로 보고 서울 시간으로 바꿔 **`18:00`**으로 표시합니다.

Spark에는 이 문제를 위한 설정 `spark.sql.parquet.int96TimestampConversion`(기본 `false`)이 있습니다. Impala가 쓴 INT96 데이터를 Impala와 같은 기준으로 해석하도록 조정합니다.

즉, 표시 값에 +9를 고정으로 더하는 방식은 데이터를 Hive/Spark가 썼는지, Impala가 썼는지에 따라 맞기도 하고 9시간 어긋나기도 합니다.

## +9 대신 권장하는 방법

### Impala

* 세션에서 `SET TIMEZONE='Asia/Seoul';`을 지정하거나, impalad를 `TZ=Asia/Seoul`로 실행해서 표시 시간대를 명시합니다.
* Hive/Spark가 쓴 INT96을 현지 시간으로 보려면 impalad 시작 옵션 `--convert_legacy_hive_parquet_utc_timestamps=true`(필요 시 `--use_local_tz_for_unix_timestamp_conversions=true`)를 사용합니다. 문서는 Hive가 쓴 파일을 기준으로 설명하므로, Spark가 쓴 파일에서도 같은 결과가 나오는지는 운영 환경에서 확인하세요.
* SQL에서 직접 변환해야 한다면 `+ interval 9 hours` 대신 `from_utc_timestamp(col, 'Asia/Seoul')`처럼 시간대 이름을 사용해 의도를 분명히 합니다.

### Spark

* `spark.sql.session.timeZone`을 명시해서 서버 설정에 따라 결과가 바뀌지 않게 합니다.
* 가능하면 `spark.sql.parquet.outputTimestampType=TIMESTAMP_MICROS`로 표준 INT64 형식을 쓰게 합니다. `isAdjustedToUTC` 정보가 파일에 남아 다른 엔진도 같은 기준으로 해석합니다.
* Impala가 쓴 INT96을 읽을 때는 `spark.sql.parquet.int96TimestampConversion=true`를 검토합니다.

### NiFi

* 문자열로 변환할 때 `format(/ts, 'yyyy-MM-dd HH:mm:ss', 'Asia/Seoul')`처럼 시간대를 지정합니다(RecordPath 함수의 시간대 인자는 NIFI-6305에서 추가되었으며, 지원 버전은 사용하는 NiFi 버전의 문서에서 확인하세요).
* DB에서 가져오는 경우 JDBC 드라이버와 NiFi JVM의 시간대가 원본 DB의 시간대와 맞는지 확인합니다.

## parquet 명령으로 확인하기

이 저장소의 `parquet` 명령은 Impala, NiFi처럼 기본적으로 UTC(`Z`)로 표시하고, `parquet.cli.timezone`을 지정하면 Spark처럼 그 시간대로 표시합니다. 파일에 저장된 시점을 확인할 때는 기본값(UTC)으로 보는 것이 덜 헷갈립니다.
자세한 사용법은 [parquet.md의 날짜/시간 표시와 시간대](parquet.md#날짜시간-표시와-시간대)를 참고하세요.

`samples/users.parquet`의 `last_login`은 `isAdjustedToUTC = true`인 INT64 timestamp입니다(`TIMESTAMP(MILLIS,true)`).

```
$ parquet schema --parquet samples/users.parquet
message com.example.User {
...
  required int64 last_login (TIMESTAMP(MILLIS,true));
...
}
```

기본(UTC) 표시는 Impala(서버가 UTC인 경우), NiFi와 같은 기준입니다.

```
$ parquet cat -c last_login -n 2 samples/users.parquet
"2026-10-01T09:12:30Z"
"2026-09-28T18:45:00Z"
```

`Asia/Seoul`로 표시하면 Spark(session time zone이 Asia/Seoul)와 같은 기준이 됩니다. 저장된 시점은 같고 표시만 9시간 다릅니다.

```
$ parquet -D parquet.cli.timezone=Asia/Seoul cat -c last_login -n 2 samples/users.parquet
"2026-10-01T18:12:30+09:00"
"2026-09-29T03:45:00+09:00"
```

INT96 컬럼(`samples/events_int96.parquet`의 `event_time`)도 같은 방식으로 표시됩니다.

```
$ parquet cat -c event_time -n 1 samples/events_int96.parquet
"2026-10-01T09:12:30.123456789Z"

$ parquet -D parquet.cli.timezone=Asia/Seoul cat -c event_time -n 1 samples/events_int96.parquet
"2026-10-01T18:12:30.123456789+09:00"
```

INT96은 값만으로는 Hive/Spark가 UTC로 변환해 저장한 것인지, Impala가 벽시계 시각 그대로 저장한 것인지 알 수 없습니다. 파일을 쓴 엔진은 `parquet meta`의 `Created by`로 확인할 수 있습니다.

```
$ parquet meta samples/events_int96.parquet
...
Created by: parquet-mr version 1.18.1 (build f682a808de9b3d4d50bf6953a670640683dbd4b1)
...
```

`parquet-mr`(parquet-java)로 쓴 파일은 Hive나 Spark 같은 Java 기반 엔진이 쓴 것이고, Impala가 쓴 파일은 `impala version ...`으로 표시됩니다.

## 참고 자료

* [Parquet LogicalTypes (TIMESTAMP, isAdjustedToUTC)](https://github.com/apache/parquet-format/blob/master/LogicalTypes.md)
* [Apache Impala: TIMESTAMP data type](https://impala.apache.org/docs/build/html/topics/impala_timestamp.html)
* [Cloudera: Impala TIMESTAMP data type (CDP 7.3.1)](https://docs.cloudera.com/cdp-private-cloud-base/7.3.1/impala-sql-reference/topics/impala-timestamp.html)
* [Spark: Parquet Files (configuration)](https://spark.apache.org/docs/latest/sql-data-sources-parquet.html)
* [Spark: Data Types (TIMESTAMP_LTZ, TIMESTAMP_NTZ)](https://spark.apache.org/docs/latest/sql-ref-datatypes.html)
* [Spark: Configuration (spark.sql.session.timeZone)](https://spark.apache.org/docs/latest/configuration.html)
* [Cloudera: Spark TIMESTAMP compatibility for Parquet files](https://docs.cloudera.com/runtime/latest/developing-spark-applications/topics/spark-timestamp-compatibility-parquet.html)
* [Cloudera: NiFi Record Path reference (format, toDate)](https://docs.cloudera.com/cfm/2.0.4/nifi-record-guide/topics/nifi-format.html)
* [NIFI-6245: RecordPath toDate/format time zone](https://issues.apache.org/jira/browse/NIFI-6245)
* [NIFI-6442: ExecuteSQL date conversion with Avro logical types](https://issues-test.apache.org/jira/browse/NIFI-6442)
* [Cloudera Community: CSV timestamps parsed as UTC](https://community.cloudera.com/t5/Support-Questions/Specify-timezone-when-parsing-CSV-data/m-p/304300)
* [Cloudera Community: ExecuteSQL Avro logical type converts dates to UTC](https://community.cloudera.com/t5/Support-Questions/NIFI-ExecuteSQL-AVRO-logical-type-converts-all-dates-to-UTC/m-p/345771)
