#!/bin/bash

# 1. 결과물을 모을 통합 디렉토리 설정
ROOT_OUTPUT="$(pwd)/dist"
JAR_OUT="$ROOT_OUTPUT/modules"
LIB_OUT="$ROOT_OUTPUT/lib"

# 기존 디렉토리 초기화
rm -rf "$ROOT_OUTPUT"
mkdir -p "$JAR_OUT" "$LIB_OUT"

echo ">>> 빌드 및 의존성 추출 시작..."

# 2. 전체 프로젝트 빌드 (테스트 제외로 속도 향상)
mvn clean package -DskipTests

# 3. 모든 서브 모듈에서 의존성 복사 실행
# -DoutputDirectory를 절대 경로로 지정하여 한 곳으로 모음
mvn dependency:copy-dependencies -DoutputDirectory="$LIB_OUT" -DincludeScope=runtime

# 4. 각 서브 모듈의 결과물(JAR)만 골라서 복사
# target 폴더 내의 파일 중 'sources', 'javadoc'이 아닌 일반 jar만 추출
find . -mindepth 2 -maxdepth 3 -name "target" -type d | while read target_dir; do
    find "$target_dir" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -exec cp {} "$JAR_OUT" \;
done

echo "------------------------------------------"
echo ">>> 수집 완료!"
echo ">>> 모듈 위치: $JAR_OUT"
echo ">>> 의존성 위치: $LIB_OUT"
echo "------------------------------------------"
