# Parquet Timestamp와 Timezone: NiFi · Impala · Spark 상호운용성 기술 가이드

> **수정·심화판 v2.0** | 2026-10-20  
> 원문: https://github.com/TheOpenCloudEngine/parquet-avro-tools/blob/main/timezone.md  
> 적용 범위: Apache Parquet / Avro, Apache Spark 3.x, Apache Impala 및 Cloudera CDP Private Cloud Base 7.3.1, Apache NiFi / CFM  
> **검증 수준:** 공식 규격·제품 문서와 원본 프로젝트 문서를 대조한 문헌 검토. 실제 NiFi–Impala–Spark 통합 실행 결과는 포함하지 않으며, 아래 테스트의 결과 표는 **예상값 또는 확인 항목**이다.

## 목차

1. [핵심 결론](#1-핵심-결론)
2. [원본 문서 정밀 리뷰와 수정 사항](#2-원본-문서-정밀-리뷰와-수정-사항)
3. [시간 데이터 모델: Instant, Local, Offset, Zone](#3-시간-데이터-모델-instant-local-offset-zone)
4. [Parquet의 물리 타입과 논리 타입](#4-parquet의-물리-타입과-논리-타입)
5. [Avro와 JDBC 계층](#5-avro와-jdbc-계층)
6. [Spark 동작과 설정](#6-spark-동작과-설정)
7. [Impala 동작과 설정](#7-impala-동작과-설정)
8. [NiFi 데이터 파이프라인 분석](#8-nifi-데이터-파이프라인-분석)
9. [9시간 차이의 실제 발생 시나리오](#9-9시간-차이의-실제-발생-시나리오)
10. [진단 및 재현 테스트](#10-진단-및-재현-테스트)
11. [권장 운영 아키텍처 및 이행 방안](#11-권장-운영-아키텍처-및-이행-방안)
12. [FAQ 및 주의사항](#12-faq-및-주의사항)
13. [근거 자료와 검증 범위](#13-근거-자료와-검증-범위)

---

## 1. 핵심 결론

**같은 Parquet 파일이 엔진마다 9시간 다르게 보이는 원인을 항상 ‘표시 시간대 차이’로 단정할 수 없다.** 서로 구별해야 할 원인은 다음과 같다.

1. **데이터의 의미가 다름:** 특정 시점(instant)인가, 시간대 없는 로컬 일시(local date-time)인가?
2. **파일 형식이 다름:** INT96, INT64+`TIMESTAMP(MICROS,true)`, INT64+`TIMESTAMP(MICROS,false)` 중 무엇인가?
3. **기록 정책이 다름:** 작성 엔진이 로컬 시각을 UTC 정규화했는가, 벽시계 시각을 그대로 저장했는가?
4. **읽기 규칙이 다름:** Spark가 session timezone을 적용하는가, Impala가 INT96 변환을 생략하는가?
5. **추가 변환이 있음:** JDBC, Avro 변환, NiFi Record Reader/Writer, SQL 함수, 클라이언트 표시 단계에 중복 또는 누락된 변환이 있는가?

**운영 원칙:** `+ INTERVAL 9 HOURS`를 기본 해결책으로 사용하지 않는다. 파일 footer에서 타입을 확인하고, 원본 시간 의미 및 작성 엔진을 식별한 후, 변환은 필요한 **한 지점**에서만 수행한다.

### 한눈에 보는 저장 의미

| 파일 스키마/형식 | 실제 의미 | 구분 가능성 | 읽기 시 주의 |
|---|---|---|---|
| `INT64 TIMESTAMP(MILLIS,true)` | Unix epoch 기준 절대 시점 | 명시적 | 로컬 표시 변환은 소비자의 책임 |
| `INT64 TIMESTAMP(MICROS,true)` | 절대 시점 | 명시적 | microsecond 정밀도 유지 |
| `INT64 TIMESTAMP(MILLIS,false)` | 시간대 없는 로컬 일시 | 명시적 | UTC에서 현지로 +9 변환하면 안 됨 |
| `INT64 TIMESTAMP(MICROS,false)` | 시간대 없는 로컬 일시 | 명시적 | session TZ와 관계없이 같은 달력·시각이어야 함 |
| Legacy `INT64 TIMESTAMP_MILLIS/MICROS` 주석 | 전통적으로 UTC 정규화 | 구형 주석만으로 지역 의미 표현 불가 | 최신 LogicalType 정보가 있는지 함께 확인 |
| Legacy **physical `INT96`** | timezone 의미가 자체 명시되지 않음 | **불명확** | 생성 엔진·옵션 및 실제 샘플 비교 필수 |

> 주의: 2026년 시점의 최신 Parquet 명세는 새로운 12바이트 `FIXED_LEN_BYTE_ARRAY(12)` timestamp carrier도 설명한다. 그러나 **레거시 physical `INT96`**과 동일시하면 안 된다. 본 문서는 Spark·Impala에서 흔히 문제가 되는 레거시 INT96 및 INT64 경로에 초점을 맞춘다. [P1]

---

## 2. 원본 문서 정밀 리뷰와 수정 사항

| ID | 원본 설명·권고 요지 | 판정 | 수정 또는 제한 조건 |
|---|---|---|---|
| R01 | “Parquet에는 UTC 기준 시점이 저장된다.” | **정정 필수** | `isAdjustedToUTC=false`는 local timestamp다. INT96은 UTC 의미가 보장되지 않는다. |
| R02 | “세 엔진은 같은 instant의 표시만 다르다.” | **정정 필수** | 실제 원인은 작성 단계의 의미 변경, JDBC 해석, 파일 주석, 조회 변환을 모두 포함한다. |
| R03 | “Spark는 서버가 KST면 KST로 표시한다.” | 조건부 | session timezone이 우선이고, 실제 기본값은 런타임/JVM 등 환경에 따른다. 명시 설정 권장. |
| R04 | “Hive·Spark INT96은 UTC 정규화, Impala INT96은 벽시계 값.” | 대체로 타당 | 일반적인 엔진/기본 경로의 차이. 버전·Spark Impala 호환 옵션·작성 환경에 따라 단정 금지. |
| R05 | “Impala INT64는 UTC→로컬 변환.” | **세분화 필요** | Legacy OriginalType과 LogicalType(`isAdjustedToUTC`)의 규칙이 다르다. [I1] |
| R06 | “`SET TIMEZONE='Asia/Seoul'`로 해결.” | **조건부** | `TIMEZONE`만으로 Impala의 모든 INT96 조회가 바뀌지 않는다. 관련 impalad flag 및 읽기 경로 확인. |
| R07 | “`convert_legacy_hive_parquet_utc_timestamps`를 켠다.” | 신중 적용 | Hive 계열 INT96 대상. Impala가 만든 파일 등 혼합 디렉터리에서 전체 적용 시 재해석 위험. 운영 변경 전 분리 테스트. |
| R08 | “`from_utc_timestamp()` 사용.” | 타당하지만 조건부 | 입력 컬럼이 **UTC 벽시계 값**인 것으로 증명되고 기존 변환이 적용되지 않았을 때만 사용. Impala에도 실제 지원 함수다. [I2] |
| R09 | “NiFi의 Timestamp는 모두 GMT로 보인다.” | **일반화 금지** | RecordPath `format()` 기본 표시 규칙, JDBC 읽기, Avro 매핑, PutParquet를 별도로 분석해야 한다. |
| R10 | “ExecuteSQL의 Avro logical type은 epoch millis.” | 확인 | `Use Avro Logical Types=true`인 경우 TIMESTAMP를 `timestamp-millis`로 기술. `false`는 문자열 경로. [N1] |
| R11 | “`Created by: parquet-mr`면 Hive/Spark.” | **정정 필수** | parquet-mr는 Java 라이브러리. NiFi 및 자체 Java 애플리케이션도 사용할 수 있다. 생성 엔진 식별의 단서일 뿐. |
| R12 | “repo CLI가 INT96을 `...Z`로 표시한다.” | 의미 제한 | 이 저장소 CLI의 **해석·포맷 정책**이다. INT96 자체의 UTC semantics를 입증하지 않는다. [R2] |
| R13 | “INT96 보정 옵션은 읽기 문제 해결.” | 보강 필요 | Cloudera 호환 문서는 설정의 **읽기와 쓰기 모두**에 걸친 영향을 예시로 설명한다. [C1] |
| R14 | “`TIMESTAMP_MICROS`로 전환하면 호환성 해결.” | 조건부 | 더 명료한 물리/논리 주석이지만, NTZ/LTZ 의미·구버전 리더 지원·기존 데이터 의미는 별도 검증해야 한다. |

### 원본에서 반드시 고쳐야 할 두 문장

- **부정확:** “Parquet 파일에는 timestamp가 UTC 기준의 시점으로 저장됩니다.”  
  **수정:** “Parquet timestamp는 UTC 정규화된 instant 또는 시간대 없는 local timestamp를 나타낼 수 있다. `isAdjustedToUTC` 및 물리 타입을 확인해야 하고 레거시 INT96의 의미는 작성 엔진에 따라 달라질 수 있다.”
- **부정확:** “Impala와 NiFi는 별도 설정이 없으면 UTC 그대로 00:00으로 보여 줍니다.”  
  **수정:** “Impala INT96은 기본적으로 파일 값을 시간대 변환 없이 읽으며, INT64는 주석과 구현 설정에 따라 달리 처리한다. NiFi의 출력은 사용한 Processor, Reader, Writer, Avro schema 및 포맷 함수에 따라 달라진다.”

---

## 3. 시간 데이터 모델: Instant, Local, Offset, Zone

### 3.1 네 가지 개념을 혼동하지 않는다

| 개념 | 예 | 특성 | 대표 Java 타입 |
|---|---|---|---|
| Instant | `2026-10-01T00:00:00Z` | 전 세계에서 동일한 한 순간 | `Instant` |
| Local date-time | `2026-10-01 09:00:00` | 어느 지역의 09시인지는 불명확 | `LocalDateTime` |
| Offset date-time | `2026-10-01T09:00:00+09:00` | 오프셋으로 instant 확인 가능 | `OffsetDateTime` |
| Zoned date-time | `2026-10-01T09:00:00+09:00[Asia/Seoul]` | 지역별 역사적 DST 규칙까지 표현 | `ZonedDateTime` |

서울의 `2026-10-01 09:00:00` **KST**는 같은 instant로 `2026-10-01T00:00:00Z`이다. 하지만 원본 DB의 `TIMESTAMP WITHOUT TIME ZONE`에 들어 있는 `09:00`은, 데이터 계약에서 `Asia/Seoul`이라고 정의하지 않는 한 **UTC 00:00이라고 결론 낼 수 없다**.

### 3.2 정밀도도 계약의 일부

- Millis(밀리초): 소수점 3자리, epoch × 1,000.
- Micros(마이크로초): 소수점 6자리, epoch × 1,000,000.
- Nanos(나노초): 소수점 9자리. 엔진·파일 타입마다 지원 범위가 다르다.
- `TIMESTAMP_MILLIS`로 낮출 때 microsecond 하위 자릿수가 손실될 수 있다.
- Parquet timestamp는 원래 지역 이름 `Asia/Seoul` 자체를 보존하지 않는다. 필요하면 별도 컬럼(`source_timezone`, `source_offset`)으로 보존한다.

---

## 4. Parquet의 물리 타입과 논리 타입

### 4.1 `isAdjustedToUTC`의 정확한 의미

**`TIMESTAMP(..., true)`**

- UTC Unix epoch부터 경과한 단위를 정수로 저장한다.
- 값은 하나의 instant를 지정한다.
- `2026-10-01T00:00:00Z`의 KST 화면 표현은 `2026-10-01 09:00:00`이다.

**`TIMESTAMP(..., false)`**

- 시간대 없는 달력·시각을 표현한다.
- 구현상 epoch 유사 정수 계산을 이용하더라도, 실제 instant라는 의미는 **없다**.
- `2026-10-01 09:00:00`은 UTC session과 KST session에서 같은 로컬 시각으로 취급되어야 한다.
- `false`에 `+09:00`을 덧붙이거나 UTC→KST 변환을 자동 적용하면 의미가 달라진다.

### 4.2 INT96을 별도로 다루는 이유

전통적인 **physical INT96**은 레거시 12바이트 timestamp 표현이며 `isAdjustedToUTC` 플래그가 없다. Hive·Spark의 일반적인 기록 정책은 UTC 정규화에 가깝지만, Impala는 INT96을 읽고 쓸 때 시간대 조정을 하지 않는 것으로 문서화되어 있다. 따라서 동일한 `09:00` 텍스트라도 어떤 파일에서는 UTC instant로 해석되고 어떤 파일에서는 원래 벽시계 값으로 해석될 수 있다. [I1], [S1]

**위험한 오해:** `parquet cat ...`이 INT96을 `2026-10-01T09:00:00Z`로 출력했다고 해서 생성 데이터가 실제로 UTC 09:00을 의도했다고 증명된 것은 아니다. CLI가 INT96을 UTC로 *가정하여 포맷*했을 수 있다. 원본 저장소는 그런 출력 정책을 가진 확장 도구다. [R2]

### 4.3 물리 타입/논리 타입은 파일 footer에서 확인

```bash
# 아래 parquet 명령은 원본 저장소의 parquet-avro-tools CLI 기준
parquet schema --parquet /path/to/data.parquet
parquet meta /path/to/data.parquet
parquet footer /path/to/data.parquet

# 같은 파일을 '기본 UTC 형식'과 KST 포맷으로 비교
parquet cat -c event_time -n 5 /path/to/data.parquet
parquet -D parquet.cli.timezone=Asia/Seoul cat -c event_time -n 5 /path/to/data.parquet
```

**해석 규칙:** CLI의 출력 변화를 확인하는 것은 표시 정책 테스트다. 저장 시점에 원본 시스템이 무엇을 뜻했는지까지 판정하는 검증은 아니다. `Created by`와 Key-Value metadata, 원본 작업 로그, 입력 샘플, DB 컬럼 정의가 필요하다.

---

## 5. Avro와 JDBC 계층

### 5.1 Avro Logical Types

```json
{
  "type": "record",
  "name": "Event",
  "fields": [
    {"name": "event_id", "type": "long"},
    {"name": "event_ts", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

- Avro `timestamp-millis` / `timestamp-micros`: 절대 시점(epoch 기반)을 표현한다.
- Avro `local-timestamp-millis` / `local-timestamp-micros`: 특정 시간대가 없는 local date-time.
- 숫자만 같아도 `timestamp-millis`와 `local-timestamp-millis`는 *의미적으로 다른 타입*이다. [A1]

### 5.2 JDBC가 문제를 만들 수 있는 이유

`ResultSet.getTimestamp(...)` 및 벤더 드라이버의 타입 매핑 과정에서, **DB의 TIMESTAMP WITHOUT TIME ZONE → Java Timestamp/Instant 유사 값**으로 넘어갈 때 ‘어느 시간대의 로컬 시각인가’가 결정될 수 있다. 이 지점에서 잘못 해석한 값을 이후 Avro와 Parquet로 정확히 전달해도 최종 결과는 9시간 틀릴 수 있다.

- Oracle `TIMESTAMP` (timezone 없음)과 `TIMESTAMP WITH TIME ZONE`은 의미와 반환 경로가 다르다.
- JDBC 드라이버 버전, DB 세션 TZ, JVM 기본 TZ, `getTimestamp(..., Calendar)` 사용 여부 등은 사례별 검토가 필요하다.
- PostgreSQL `timestamp without time zone` / `timestamptz` 또한 분리해야 한다.
- 단순히 **JVM의 `-Duser.timezone=Asia/Seoul`을 바꾸는 것만으로** 해결된다고 단정하면 안 된다. 다른 타임스탬프 처리까지 영향을 받는다.

---

## 6. Spark 동작과 설정

### 6.1 확인할 설정

```sql
SET spark.sql.session.timeZone;
SET spark.sql.timestampType;
SET spark.sql.parquet.outputTimestampType;
SET spark.sql.parquet.int96AsTimestamp;
SET spark.sql.parquet.int96TimestampConversion;
SET spark.sql.parquet.inferTimestampNTZ.enabled;
SET spark.sql.parquet.datetimeRebaseModeInRead;
SET spark.sql.parquet.int96RebaseModeInRead;
```

| 설정 | Spark 3.5 문서 기본값 | 의미 |
|---|---|---|
| `spark.sql.session.timeZone` | 실행 환경 local timezone | LTZ 파싱·표현의 session 규칙 |
| `spark.sql.timestampType` | 보통 `TIMESTAMP_LTZ` | 기본 TIMESTAMP 타입 선택, 실제 배포판에서 확인 |
| `spark.sql.parquet.outputTimestampType` | `INT96` | Parquet Timestamp 기록 방식 |
| `spark.sql.parquet.int96AsTimestamp` | `true` | INT96을 Timestamp로 해석 |
| `spark.sql.parquet.int96TimestampConversion` | `false` | Impala 작성 INT96 호환 보정 |
| `spark.sql.parquet.inferTimestampNTZ.enabled` | `true` (3.4+) | 타 엔진 파일의 `isAdjustedToUTC=false` 스키마 추론 |

> 위 값은 Apache Spark **3.5.x** 문서 기준이다. CDP에 번들된 Spark의 실제 버전·기본값은 `spark.version`, `spark.conf.get(...)`으로 재확인한다. [S1]

### 6.2 LTZ와 NTZ의 실질적인 차이

- `TIMESTAMP_LTZ`: instant를 session timezone 기준 달력·시각으로 보여준다.
- `TIMESTAMP_NTZ`: 오프셋 없는 달력·시각을 보존한다. UTC/KST 세션 변경이 단순한 표시 시각 변경을 유발해서는 안 된다.
- `spark.sql.session.timeZone`은 서버의 OS 시간대와 항상 동일하다고 가정하면 안 된다.

### 6.3 권장 write 정책 (테스트 후 채택)

```python
# PySpark - 시간 의미가 instant인 컬럼에 대한 권장 테스트 설정
spark.conf.set("spark.sql.session.timeZone", "Asia/Seoul")
spark.conf.set("spark.sql.parquet.outputTimestampType", "TIMESTAMP_MICROS")

# timestamp가 진짜 instant인지 확정한 뒤 기록
# df.write.mode("overwrite").parquet("/tmp/tztest/spark_micros")
```

이 설정은 **Spark 일반 TIMESTAMP(LTZ)** 컬럼의 저장 형식을 정한다. `TIMESTAMP_NTZ` 컬럼이나 구버전/외부 Parquet Reader에는 같은 결과를 일괄 보장하지 않는다. NiFi/Impala 호환성 테스트가 선행되어야 한다.

### 6.4 Spark와 Impala INT96 상호운용성

```python
spark.conf.set("spark.sql.parquet.int96TimestampConversion", "true")
```

Impala가 작성한 INT96을 읽는 호환 방법으로 문서화되어 있다. Cloudera는 이 설정에 대해 **읽기와 쓰기 측 동작을 모두 예시로 설명**한다. 따라서 혼합 파일에 일괄 적용하지 않고, Impala 작성 전용 경로에 한정하여 검증할 것을 권장한다. [C1]

### 6.5 별개의 오류: historical calendar rebase

Spark 3.x는 오래된 날짜/시간에서 Julian·Gregorian 전환을 다루는 `datetimeRebaseMode...` 및 `int96RebaseMode...` 옵션을 제공한다. **이 문제는 일반적인 9시간 timezone 문제와 별개다.** 달력 개정 이전 데이터 또는 매우 오래된 이력 데이터가 있으면 추가 검토한다. [S1]

---

## 7. Impala 동작과 설정

### 7.1 INT96과 INT64를 반드시 분리

**INT96:** Impala 공식/Cloudera 문서는 기본적으로 INT96를 읽고 쓸 때 timezone adjustment를 하지 않는다고 명시한다. Hive가 로컬 값을 UTC로 정규화한 파일을 Impala에서 읽으면 원래 local 값과 다를 수 있다. [I1]

**INT64 + 레거시 `TIMESTAMP_MILLIS/MICROS` OriginalType:** Impala 3.2 이상 공식 문서는 UTC normalized 값으로 간주하고 UTC→local conversion을 적용한다고 설명한다.

**INT64 + `TIMESTAMP` LogicalType:** `isAdjustedToUTC` metadata를 보고 변환 필요성을 결정한다. 이 처리 역시 버전별 동작과 실제 TZ/flag 설정으로 검증한다. [I1]

### 7.2 쿼리 옵션과 daemon flag 역할

```sql
-- Impala SQL 세션의 TIMEZONE 옵션 설정 예시
SET TIMEZONE='Asia/Seoul';

-- 현재 설정을 조사하는 용도 (사용 가능한 출력 형식은 버전별 확인)
SET;
```

```text
# impalad 시작 flag (클러스터 운영 변경이므로 테스트 후 적용)
--convert_legacy_hive_parquet_utc_timestamps=true
--use_local_tz_for_unix_timestamp_conversions=true
```

- `TIMEZONE`은 특정 처리에서 현지 시간대를 결정하는 설정이고, `TZ`/coordinator 시스템 설정과 우선순위가 관련된다.
- `--use_local_tz_for_unix_timestamp_conversions`는 Unix timestamp 관련 변환 함수 등의 지역 시간대 해석에 영향을 준다.
- `--convert_legacy_hive_parquet_utc_timestamps`는 레거시 Hive INT96 호환 읽기 변환에 관여한다.
- **`SET TIMEZONE`만으로 모든 INT96 SELECT 값이 KST로 바뀐다는 설명은 틀리다.**
- **`--convert_legacy_hive...`를 혼합 출처 전체 데이터에 무조건 켜는 것은 위험하다.** Hive/Spark/Impala/NiFi writer의 파일을 분리 평가한다.
- CDP 7.3.1 문서에 옵션 설명 중 `TIMESTAMP query option`이라는 문구가 있는 대목과 Apache Impala 문서의 `TIMEZONE query option` 표기를 대조했다. 실제 SQL에서 사용하는 명칭은 `TIMEZONE`이며 테스트 클러스터에서 `SET TIMEZONE` 동작을 확인한다. [I1], [I3]

### 7.3 UTC라고 확인된 벽시계 컬럼만 명시 변환

```sql
SELECT
    event_ts AS source_utc_wallclock,
    FROM_UTC_TIMESTAMP(event_ts, 'Asia/Seoul') AS event_ts_kst
FROM db_name.table_name
LIMIT 10;
```

`FROM_UTC_TIMESTAMP`는 Impala 공식 함수다. 다만 **event_ts가 변환 이전 UTC wall-clock이라는 것이 증명될 때**에만 적절하다. 이미 INT64 리더가 현지 시간으로 변환한 값을 다시 변환하면 9시간 중복된다. [I2]

### 7.4 Kudu Timestamp는 Parquet과 별도 정책

CDP 7.3.1 Impala 문서는 Kudu용 `write_kudu_utc_timestamps`와 `convert_kudu_utc_timestamps`도 별도로 설명한다. Parquet INT96 설정을 Kudu에 그대로 대입하지 않는다. 두 Kudu 옵션은 읽기·쓰기 대칭과 기존 데이터 호환성에 유의해야 한다. [I1]

---

## 8. NiFi 데이터 파이프라인 분석

### 8.1 단계별 진단 구조

```text
[Oracle / PostgreSQL / 다른 DB]
    | 컬럼 타입 + DB 세션 TZ + JDBC driver
    v
[ExecuteSQL / ExecuteSQLRecord / QueryDatabaseTableRecord]
    | JDBC type → Avro Record schema 변환
    | Use Avro Logical Types on/off (지원 프로세서 확인)
    v
[Avro payload 또는 NiFi Record]
    | timestamp-millis / local-timestamp / string?
    | 실제 epoch 수치와 schema 확인
    v
[AvroReader / 기타 Record Reader]
    | Reader가 타입을 어떻게 복원했는지 확인
    v
[PutParquet 또는 ParquetRecordSetWriter를 사용하는 경로]
    | Avro → Parquet schema, 물리 타입, 옵션
    v
[Parquet 파일 footer와 실제 record]
    | physical type / logical annotation / created_by / metadata
    +------------------------+
    |                        |
    v                        v
[Impala Reader]          [Spark Reader]
```

> PutParquet와 ParquetRecordSetWriter는 **서로 다른 배포/구성 경로**로 취급한다. 현재 NiFi/CFM 버전에서 사용 가능한 Processor·Controller Service 명칭과 속성을 UI/컴포넌트 문서에서 확인한다. 오래된 PutParquet 버전의 속성을 NiFi 2.x에 그대로 적용해서는 안 된다.

### 8.2 ExecuteSQL의 `Use Avro Logical Types`가 중요

Apache NiFi ExecuteSQL 설명은 다음과 같이 문서화한다. [N1]

| 설정 | TIMESTAMP 출력 | 주의 |
|---|---|---|
| `Use Avro Logical Types = true` | Avro logical `timestamp-millis` / `long` epoch millis | 입력 JDBC timestamp를 epoch로 만드는 시점의 TZ 가정 확인 |
| `Use Avro Logical Types = false` | 문자열 | 형식과 zone 정보 존재 여부 확인, 암묵적 parse 피하기 |

실제로 `true`라면 Avro schema의 `logicalType`만 보지 말고 **저장된 정수 epoch**를 검사한다. 날짜 문자열만 비교하면 Reader가 출력 과정에서 변환했는지를 분리할 수 없다.

### 8.3 NiFi RecordPath `format()`과 `toDate()`

```text
# eventDate가 날짜 타입일 때 표시 변환 예시
format(/eventDate, 'yyyy-MM-dd HH:mm:ss', 'Asia/Seoul')

# eventDate가 문자열이고 timezone 의미를 알고 있을 때는
# 먼저 toDate()의 format/zone 파싱 규칙을 해당 NiFi 버전에서 점검
```

RecordPath의 `format()`에서 timezone 인자를 지정한 예시는 Cloudera 문서에 있다. `format()`의 시간대 정책은 **표시/문자열화 단계**에 한정되므로 DB에서 잘못 변환된 epoch를 자동 복구하지 않는다. [N2]

**주의:** 포맷 패턴 `yyyy-MM-dd'T'HH:mm:ss'Z'`의 따옴표 속 `Z`는 문자 리터럴이므로, 입력 Instant가 실제 UTC인지 확인하지 않고 사용하면 허위 UTC 표기가 될 수 있다. 출력 zone을 명시하고 오프셋 패턴을 사용해야 오해를 줄인다.

### 8.4 Oracle 실전 유의사항

```sql
-- Oracle: 원본 컬럼의 타입을 먼저 확인
SELECT column_name, data_type, data_scale
FROM all_tab_columns
WHERE owner = UPPER('YOUR_SCHEMA')
  AND table_name = UPPER('YOUR_TABLE')
  AND column_name = UPPER('EVENT_TS');

-- DB session의 timezone과 DB timezone은 동일 개념이 아니다.
SELECT SESSIONTIMEZONE, DBTIMEZONE FROM dual;
```

- `TIMESTAMP`와 `TIMESTAMP WITH TIME ZONE`, `TIMESTAMP WITH LOCAL TIME ZONE`을 분리한다.
- `TO_CHAR`로 문자열을 만들면 Avro logical timestamp 경로를 우회할 수 있으나, 문자열에 `TZH:TZM`/zone 정보가 빠지면 의미 불명확성은 그대로 남는다.
- Oracle timestamp를 CSV 문자열로 중간 변환할 경우, 후단 `UpdateRecord`/Reader schema가 `STRING`인지 `TIMESTAMP`인지 확인한다. **단순 포맷 변경은 timezone 의미 변경과 다르다.**
- 날짜·시간 포맷 및 시간대 보정 문제와 ORA-01843 같은 형식 파싱 오류는 원인과 점검 순서가 다르다.

---

## 9. 9시간 차이의 실제 발생 시나리오

다음은 **개념적 예시**이며, 특정 CDP 빌드에서의 실측값이 아니다.

### 시나리오 A — Spark LTZ가 쓴 instant를 Impala INT96 기본 정책으로 읽음

- 원본 이벤트: `2026-10-01 09:00:00 Asia/Seoul` = `2026-10-01T00:00:00Z`.
- Spark session `Asia/Seoul`에서 일반 LTZ로 읽으면 `09:00`으로 표시될 수 있다.
- Spark가 UTC 정규화된 INT96을 썼고 Impala가 변환 없이 읽으면 `00:00`으로 보일 수 있다.
- 조치: footer=INT96 확인 → writer 식별 → Impala legacy 옵션 테스트 또는 필요한 조회 경로에서 명시 변환.

### 시나리오 B — Impala가 벽시계 09:00을 INT96에 기록 → Spark 기본 read

- Impala에 로컬 의미의 `09:00`을 저장했고 물리 값도 09:00으로 기록.
- Spark가 별도 보정 없이 이를 UTC instant로 취급한 다음 KST로 보여 주면 `18:00`이 될 수 있다.
- 조치: Impala 작성 파일로 확증된 경로에서 `spark.sql.parquet.int96TimestampConversion=true`를 테스트. 읽기·쓰기 동작 모두 확인. [C1]

### 시나리오 C — UTC Instant가 아닌 로컬 시간 문자열을 NiFi에서 epoch로 잘못 해석

- 원본 DB 컬럼: `'2026-10-01 09:00:00'` (time zone 없음; 업무 규정상 KST).
- JDBC/RecordReader가 이를 UTC 09:00으로 해석 → epoch가 9시간 뒤의 시점으로 만들어질 수 있다.
- 이후 Spark `Asia/Seoul`이 `18:00`으로 표시해도 **Spark 자체는 정합하게 표시 중**일 수 있다.
- 조치: 원본 컬럼 타입, JDBC 결과 매핑, Avro epoch 수치 비교. ‘Spark만 +9 문제’로 오인 금지.

### 시나리오 D — Parquet `isAdjustedToUTC=false`

- 파일에 `TIMESTAMP(MICROS,false)`로 `09:00` 로컬 일시 저장.
- 규격상 UTC/KST로 변환해서 18:00을 만들면 안 된다.
- 조치: Spark `TIMESTAMP_NTZ` 추론 및 Impala 버전별 reader 지원 확인. [P1], [S1]

### 시나리오 E — 두 번 변환

- Impala INT64 reader가 이미 UTC→현지를 적용.
- SQL에서 다시 `FROM_UTC_TIMESTAMP(col, 'Asia/Seoul')` 호출.
- 결과가 원본보다 9시간 앞서 보일 수 있다.
- 조치: 원시 컬럼과 변환 함수를 나란히 조회하고 원시 파일과 대조.

---

## 10. 진단 및 재현 테스트

### 10.1 실행 전 정보 수집

아래 필드를 반드시 기록한다.

| 구분 | 기록 내용 |
|---|---|
| 작성기 | NiFi Processor 및 버전 / Spark build 및 버전 / Impala 버전 |
| 입력 | DB 제품·버전, 컬럼 DDL, 예제 원시 값, 원본 기준 timezone |
| JDBC | driver 및 버전, DB session timezone, JVM `user.timezone` |
| NiFi | `Use Avro Logical Types`, Reader/Writer, schema, RecordPath 등 |
| Spark | `spark.sql.session.timeZone`, timestampType, outputTimestampType, int96TimestampConversion |
| Impala | `TIMEZONE`, daemon의 두 flag, coordinator OS TZ |
| Parquet | physical type, LogicalType, OriginalType, created_by, key-value metadata |
| 검사 | 기대 instant(`...Z`)와 기대 local(`...+09:00`), 실제 조회값 |

### 10.2 최소 테스트 벡터

| ID | 원본 의미 | 입력 문자열 | 기대 instant 또는 의미 |
|---|---|---|---|
| V01 | Asia/Seoul instant | `2026-10-01 09:00:00` | `2026-10-01T00:00:00Z` |
| V02 | UTC instant | `2026-10-01 00:00:00Z` | V01과 동일 instant |
| V03 | Local date-time | `2026-10-01 09:00:00` | **instant 미정**; 시간대 변환 금지 |
| V04 | KST 자정 경계 | `2026-10-01 00:30:00+09:00` | `2026-09-30T15:30:00Z` |
| V05 | Micros | `2026-10-01T09:00:00.123456+09:00` | `2026-10-01T00:00:00.123456Z` |
| V06 | 미국 DST 경계 | `2026-11-01 01:30:00 America/New_York` | **모호한 local 시각**; offset 지정 필요 |

### 10.3 Spark를 이용한 제어된 샘플 작성

```python
# spark-shell/pyspark - Spark 3.x 기준 예시
from pyspark.sql import functions as F

spark.conf.set("spark.sql.session.timeZone", "Asia/Seoul")
spark.conf.set("spark.sql.parquet.outputTimestampType", "INT96")

# 동일 instant의 입력을 명시적 +09:00 오프셋을 가진 문자열로 정의
base = spark.createDataFrame([(1, "2026-10-01T09:00:00+09:00")], ["id", "event_text"])
df = base.select("id", F.to_timestamp("event_text").alias("event_ts"))

df.printSchema()
df.show(truncate=False)
df.write.mode("overwrite").parquet("/tmp/parquet_tz_lab/spark_int96")

spark.conf.set("spark.sql.parquet.outputTimestampType", "TIMESTAMP_MICROS")
df.write.mode("overwrite").parquet("/tmp/parquet_tz_lab/spark_micros")

# 순수 로컬 일시의 테스트 (Spark 3.4+에서 NTZ 지원 확인)
local_df = spark.sql("SELECT CAST('2026-10-01 09:00:00' AS TIMESTAMP_NTZ) AS local_ts")
local_df.write.mode("overwrite").parquet("/tmp/parquet_tz_lab/spark_ntz")

# session timezone 비교: instant형 timestamp 출력만 변환되는지 확인
for tz in ["UTC", "Asia/Seoul"]:
    spark.conf.set("spark.sql.session.timeZone", tz)
    print("session timezone:", tz)
    spark.read.parquet("/tmp/parquet_tz_lab/spark_int96").show(truncate=False)
    spark.read.parquet("/tmp/parquet_tz_lab/spark_micros").show(truncate=False)
    spark.read.parquet("/tmp/parquet_tz_lab/spark_ntz").show(truncate=False)
```

**실행 메모:** 일부 Spark/배포판에서 `to_timestamp()`의 문자열 파싱 및 NTZ 동작이 차이가 날 수 있다. 예제는 실행 전 `spark.version`과 결과 `printSchema()`를 기록하며, 기준 instant가 맞는지는 SQL 출력 문자열뿐 아니라 epoch 값으로도 검증한다.

### 10.4 원본 CLI로 파일 footer 확인

```bash
# Spark output directory의 개별 part 파일 지정
parquet schema --parquet /tmp/parquet_tz_lab/spark_int96/part-*.parquet
parquet schema --parquet /tmp/parquet_tz_lab/spark_micros/part-*.parquet
parquet schema --parquet /tmp/parquet_tz_lab/spark_ntz/part-*.parquet
parquet meta /tmp/parquet_tz_lab/spark_micros/part-*.parquet
```

주의: `part-*`가 여러 파일로 확장되면 CLI가 실패하거나 원하는 파일 외에 다른 파일이 인자로 들어갈 수 있으므로 `find ... -name 'part-*.parquet' | head -1` 등의 방식으로 **한 개 파일을 선택**한다.

### 10.5 Impala 테이블로 교차 읽기

다음은 **HDFS에 파일이 준비되었고, 외부 테이블의 물리 schema가 Impala가 지원하는 Parquet Timestamp 타입과 호환된다는 전제**의 예시이다.

```sql
CREATE EXTERNAL TABLE IF NOT EXISTS tz_lab.spark_int96 (
    id BIGINT,
    event_ts TIMESTAMP
)
STORED AS PARQUET
LOCATION '/tmp/parquet_tz_lab/spark_int96';

CREATE EXTERNAL TABLE IF NOT EXISTS tz_lab.spark_micros (
    id BIGINT,
    event_ts TIMESTAMP
)
STORED AS PARQUET
LOCATION '/tmp/parquet_tz_lab/spark_micros';

INVALIDATE METADATA tz_lab.spark_int96;
INVALIDATE METADATA tz_lab.spark_micros;

SET TIMEZONE='Asia/Seoul';
SELECT id, event_ts FROM tz_lab.spark_int96;
SELECT id, event_ts FROM tz_lab.spark_micros;

-- 기본 Reader 결과가 UTC wall-clock임을 확인했을 때만 대조 검사
SELECT id, event_ts,
       FROM_UTC_TIMESTAMP(event_ts, 'Asia/Seoul') AS converted_candidate
FROM tz_lab.spark_int96;
```

**조건:** 운영 환경에서는 CREATE/ALTER 권한, HDFS 경로, table owner, 파티션 및 Impala의 LogicalType 매핑 지원을 확인한다. Spark 로컬 파일시스템(`/tmp/...`)에 만든 파일은 Impala가 접근하는 HDFS 경로로 복사해야 한다. 위 SQL은 클러스터에서 직접 실행해 확인한 결과가 아니다.

### 10.6 NiFi 검증 절차

1. 원본 DB에서 하나의 확정 기준 행을 조회한다. `event_id`, 원본 timestamp 문자열, timezone이 포함된 ISO 문자열 또는 별도 UTC 값도 함께 가져온다.
2. 동일 컬럼을 `Use Avro Logical Types` **false/true** 두 경로로 분리 처리한다. 각 FlowFile의 Avro schema와 실제 값(특히 epoch millis)을 저장한다.
3. Record Reader/Writer를 명시하고 Parquet로 저장한다. PutParquet 또는 대체 Record Writer 사용 여부를 기록한다.
4. 파일 footer의 physical/logical 타입을 비교한다.
5. Spark UTC, Spark KST, Impala 기본 정책, Impala 호환 정책을 **각각** 비교한다.
6. `09:00 → 00:00` 또는 `09:00 → 18:00` 차이가 **최초 등장하는 단계**를 원인 지점으로 표시한다.

### 10.7 검증 매트릭스: 직접 실행 후 채우는 표

| Case | Writer | Physical/Logical | Reader | 관련 TZ/Option | 관측값 | 기대 의미 일치? |
|---|---|---|---|---|---|---|
| T01 | Spark | INT96 | Impala | legacy OFF | 미실행 | TBD |
| T02 | Spark | INT96 | Impala | legacy ON | 미실행 | TBD |
| T03 | Spark | INT64(true) | Impala | Asia/Seoul | 미실행 | TBD |
| T04 | Spark | INT64(false) | Spark | UTC/KST | 미실행 | TBD |
| T05 | Impala | INT96 | Spark | conversion OFF | 미실행 | TBD |
| T06 | Impala | INT96 | Spark | conversion ON | 미실행 | TBD |
| T07 | NiFi | 확인 필요 | Impala | default | 미실행 | TBD |
| T08 | NiFi | 확인 필요 | Spark | UTC/KST | 미실행 | TBD |
| T09 | Oracle→NiFi | 확인 필요 | Impala | jdbc/TZ 조합 | 미실행 | TBD |
| T10 | NiFi CSV → Reader | 확인 필요 | Spark | parser TZ 조합 | 미실행 | TBD |

**통과 조건:** 단순 문자열 동일성이 아니라, instant 컬럼은 동일한 UTC epoch로 정합하고 local 컬럼은 원래 달력·시각을 변경하지 않아야 한다. 음수 시간 이동, 중복 변환, 밀리/마이크로초 손실도 함께 기록한다.

---

## 11. 권장 운영 아키텍처 및 이행 방안

### 11.1 데이터 계약(Data Contract)을 먼저 정의

권장 기본 정책은 다음과 같다.

| 데이터 성격 | 정규 표현 | 설명 |
|---|---|---|
| 이벤트 생성·수집 시각 | UTC instant | ISO-8601 UTC 표시 또는 epoch microseconds; 필요 시 Parquet `TIMESTAMP(MICROS,true)` |
| 업무 지역의 예약·영업 시작 시각 | local date-time + IANA zone | 지역 규칙을 별도 컬럼으로 보존; `TIMESTAMP(...,false)` 또는 호환 타입/스키마 사용 |
| source DB timezone 없는 값 | 원본 값을 그대로 보존 + timezone 계약 | 근거 없이 instant로 강제 변환 금지 |
| 원본 zone 감사 | `source_timezone`, `source_offset`, `raw_timestamp` | 재처리·포렌식 가능하도록 lineage/metadata 보존 |

### 11.2 단기 조치: 운영 쿼리를 깨지 않고 진단

1. 문제 파일 샘플 3~5개를 날짜 및 작성 프로세스별로 확보한다.
2. 원본 DB 값/Avro epoch/Parquet footer/Impala·Spark 조회를 같은 행으로 대조한다.
3. 수정 전·후 샘플 비교표를 생성하고 source 별 이질성(혼합 INT96 등)을 확인한다.
4. 별도 뷰(View)에서만 `FROM_UTC_TIMESTAMP` 후보 보정을 검증한다.
5. 변환이 성공했다고 판단되더라도 전역 flag 및 모든 테이블 변경은 아직 수행하지 않는다.

### 11.3 중기 조치: 쓰기 경로 표준화

1. 신규 instant 이벤트의 스키마를 명시적으로 UTC instant로 선언한다.
2. Spark가 주요 writer인 신규 데이터셋은 `TIMESTAMP_MICROS`를 **후보 표준**으로 검증한다.
3. NiFi는 JDBC source → Avro logical type → Parquet의 정규 의미를 테스트로 보증한다.
4. 레거시 INT96 경로는 source writer별 경로를 분리한다.
5. 기존 Parquet 파일을 rewrite할 경우 **원본 의미를 확정한 뒤** 한 번만 변환한다. 단순 형식 rewrite만으로 잘못된 instant가 교정되지는 않는다.
6. Iceberg 등 상위 테이블 형식을 도입해도 엔진의 timestamp with/without timezone 매핑을 별도 검증한다.

### 11.4 운영 변경 위험

- **전역 Impala flag:** 기존 Hive/Impala/NiFi 혼합 파일의 표시·분석 결과가 달라질 수 있다. 전환 전 테이블별 영향 분석 필요.
- **Spark 전역 INT96 conversion:** Impala writer 전용이 아닌 파일에도 영향을 줄 수 있다. 전역 사용 지양.
- **JVM timezone 변경:** 다른 Processor, 문자열 파싱, 스케줄링 동작에 파급될 수 있다.
- **UTC 데이터에 다시 `FROM_UTC_TIMESTAMP`:** 이미 변환된 데이터의 이중 보정.
- **INT64 마이그레이션:** `isAdjustedToUTC`를 포함한 metadata 확인 없이는 “표준”이라는 단어만으로 안전하지 않다.

---

## 12. FAQ 및 주의사항

**Q1. Spark는 09시, Impala는 00시가 보입니다. 어느 쪽이 틀렸나요?**  
A. 아직 결정할 수 없다. 파일 타입과 입력 의미, Impala 변환 옵션을 확인해야 한다. 실제 이벤트가 `09:00 KST`라는 근거가 있으면 같은 instant가 UTC `00:00`인 것은 맞지만, 업무 화면에서 UTC를 그대로 표현한 것이 오류인지는 출력 계약의 문제다.

**Q2. Parquet INT96에 찍힌 `Z`가 UTC 증거 아닌가요?**  
A. 아니다. INT96에는 timezone semantics 메타데이터가 없다. 원본 CLI는 INT96을 특정 방식으로 해석해 ISO 문자열로 출력한다.

**Q3. `SET TIMEZONE`을 KST로 바꿨는데 INT96 조회값이 그대로입니다.**  
A. 이상 현상이 아닐 수 있다. Impala INT96 기본 읽기는 시간대 변환을 하지 않는다. 해당 플래그와 작성 파일의 provenance를 점검한다.

**Q4. 모든 데이터에 `+9h` 하면 해결되지 않나요?**  
A. 아니다. 이미 KST인 값은 틀어지고, DST가 있는 지역은 계절에 따라 오프셋이 달라진다. 또한 데이터 값의 실제 오류와 표시 문제를 구분하지 못한다.

**Q5. NiFi에서 `format(..., 'Asia/Seoul')`만 추가하면 되나요?**  
A. Formatter 출력은 바뀔 수 있지만 JDBC에서 만든 epoch가 틀렸다면 원인을 해결하지 못한다. 먼저 Avro logical type/epoch를 검사한다.

**Q6. INT64 MICROS가 INT96보다 무조건 좋은가요?**  
A. 시간 의미 주석이 명시적이므로 신규 상호운용성에 유리하지만, 특정 Impala/NiFi reader가 해당 조합을 정확히 지원하는지 직접 확인해야 한다. 정밀도, NTZ/LTZ, 이전 데이터의 의미도 따져야 한다.

**Q7. Parquet `created_by=parquet-mr`이면 Spark인가요?**  
A. 아니다. parquet-mr를 사용하는 NiFi·Hive·자체 Java writer일 수 있다.

**Q8. NiFi에서 Timestamp 컬럼이 String 타입으로 남아 있습니다.**  
A. 단순 문자열이면 timezone 의미를 판단할 수 없다. `Avro schema`, Record Reader/Writer의 타입 인식, `Use Avro Logical Types`, parse 함수까지 추적한다.

---

## 13. 근거 자료와 검증 범위

다음 출처는 작성 시 직접 검토한 원본·공식 문서다. 버전별 차이가 있을 수 있으므로 운영 제품 패키지와 대조한다.

- **[R1] 원본 timezone.md:** https://github.com/TheOpenCloudEngine/parquet-avro-tools/blob/main/timezone.md
- **[R2] 원본 parquet CLI 확장 및 timezone 출력:** https://github.com/TheOpenCloudEngine/parquet-avro-tools/blob/main/parquet.md
- **[P1] Apache Parquet Logical Types:** https://github.com/apache/parquet-format/blob/master/LogicalTypes.md
- **[A1] Apache Avro 1.11.3 Specification:** https://avro.apache.org/docs/1.11.3/specification/
- **[S1] Apache Spark 3.5.7 Parquet configuration:** https://spark.apache.org/docs/3.5.7/sql-data-sources-parquet.html
- **[S2] Apache Spark 3.5.7 Data Types:** https://spark.apache.org/docs/3.5.7/sql-ref-datatypes.html
- **[I1] Cloudera CDP Private Cloud Base 7.3.1 Impala Timestamp:** https://docs.cloudera.com/cdp-private-cloud-base/7.3.1/impala-sql-reference/topics/impala-timestamp.html
- **[I2] Apache Impala Date and Time Functions:** https://impala.apache.org/docs/build/html/topics/impala_datetime_functions.html
- **[I3] Apache Impala Timestamp Data Type:** https://impala.apache.org/docs/build/html/topics/impala_timestamp.html
- **[C1] Cloudera Spark/Impala Parquet Timestamp compatibility:** https://docs.cloudera.com/runtime/latest/developing-spark-applications/topics/spark-timestamp-compatibility-parquet.html
- **[N1] Apache NiFi ExecuteSQL properties:** https://nifi.apache.org/components/org.apache.nifi.processors.standard.ExecuteSQL/
- **[N2] Cloudera CFM RecordPath `format()` documentation:** https://docs.cloudera.com/cfm/2.0.4/nifi-record-guide/topics/nifi-format.html
- **[N3] Historical NiFi PutParquet description (버전 한정):** https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-parquet-nar/1.9.2/org.apache.nifi.processors.parquet.PutParquet/

### 확인한 것과 확인하지 않은 것

**공식 문서·규격으로 확인한 사항**

- Parquet `isAdjustedToUTC=true/false` 의미와 legacy timestamp annotation 간 차이.
- Spark INT96 기본 기록 타입(3.5.x 기준), INT96 Impala 변환 플래그, NTZ 추론 플래그.
- Impala INT96와 INT64 timestamp 취급 및 관련 daemon flag.
- Impala `FROM_UTC_TIMESTAMP` 지원.
- NiFi ExecuteSQL `Use Avro Logical Types`의 timestamp-millis 매핑.
- 원본 parquet CLI의 ISO 출력/시간대 출력 정책과 INT96의 한계.

**본 문서에서 실측하지 않은 사항**

- 특정 CDP 7.3.1 유지보수 빌드에서 NiFi → Spark → Impala end-to-end 테스트 결과.
- CFM 4.12.0 / NiFi 2.6.0의 특정 Parquet writer 내부 타입 매핑 및 CFM 패치 차이.
- Oracle JDBC 각 버전별 `TIMESTAMP` timezone interpretation.
- 실제 사용자 Parquet 파일의 writer identity, epoch 값, `isAdjustedToUTC` 및 실제 9시간 offset 원인.

**운영 적용 판단:** 검증 테이블 T01~T10을 채우기 전까지 전역 옵션 변경·기존 파일 일괄 보정·시간 컬럼 재작성을 권장하지 않는다.

---

*문서 끝 — `timezone.md`의 대체 문서로 채택하거나, 기존 파일에는 변경된 부분을 선별 반영할 수 있다.*
