# FE 연동 3단계: ADMIN 권한 통합

2026-10-17 사용자 합의에 따른 백엔드 변경 기록이다. 기준 정책은 [도메인](domain-model.md#초기-등록과-admin-권한-관리--2026-10-17-확정), HTTP 계약은 [API 명세](api-endpoints.md#사용자-검색권한-관리--구현-계약)다. 사용자 요청으로 **프론트엔드는 일체 수정하지 않는다**.

## 확정 사항과 구현 범위

- AUTHZ-01: 전역 역할은 USER/ADMIN 두 개로 통합한다. 최초 ADMIN은 실제 42 숫자 ID를 확인해 운영 DB 절차로 지정한다. 일반 로그인은 USER로 생성한다.
- AUTHZ-02: 모든 ADMIN이 사용자·회장 조회 및 회장 지정·회수를 수행한다. 사용자 검색은 id·intraLogin·displayName·role을 제공하며 role=ADMIN 필터를 허용한다. 회장 목록은 역할을 제외한 사용자 요약을 유지한다.
- AUTHZ-03: ADMIN이 본인·타인의 ADMIN을 부여·회수한다. 다른 ADMIN이 있을 때 본인 회수를 허용하며 마지막 ADMIN은 409 `LAST_ADMIN_PROTECTED`로 보호한다. 거절된 회수는 역할을 변경하지 않는다.
- 회원 수·공개 회장·최근 활동 등 2단계 계약과 업무 조건은 유지한다. 회장·부원 관계는 독립적이며 권한 변경으로 자동 가입·탈퇴나 콘텐츠 삭제를 수행하지 않는다.

## 허용·거절 기준

| 행위 | 비로그인 | USER | ADMIN |
| --- | --- | --- | --- |
| 사용자 검색·역할 조회·ADMIN 필터 | 401 | 403 | 200 |
| 회장 목록 조회 | 401 | 403 | 200 |
| ADMIN·회장 부여·회수 | 401* | 403 | 204, 아래 보호 규칙 적용 |
| 본인 ADMIN 회수, 다른 ADMIN 존재 | 401* | 403 | 204, 다음 요청부터 USER |
| 마지막 ADMIN 회수 | 401* | 403 | 409 LAST_ADMIN_PROTECTED |
| ACTIVE 동아리 활동 작성 | 401* | 해당 동아리 회장 필요 | 해당 동아리 회장 필요 |
| 부원 명단 조회 | 401 | 해당 동아리 ACTIVE 부원 필요 | 해당 동아리 ACTIVE 부원 필요 |
| 활동·회차 참가 명단 및 출결 | 401* | 해당 주최자 필요 | 해당 주최자 필요 |
| 운영 공지 작성·부원 직접 등록 | 401* | 403 | 허용, 기존 대상·입력 조건 적용 |

`*` 변경 요청에서 CSRF까지 누락하면 인증보다 CSRF 검사에 의해 403일 수 있다. 공개 동아리 상세의 회장 표시와 관리용 회장 목록은 별도 계약이다. 공개 조회는 기존 익명 접근을 유지한다. 회장 관계 부여·회수는 ACTIVE/ARCHIVED 모두 허용하며 다른 동아리의 관계는 변경하지 않는다.

## DB·동시성·초기 운영

- [V11](../src/main/resources/db/migration/V11__merge_super_admin_into_admin.sql)이 기존 SUPER_ADMIN을 ADMIN으로 전환하고 역할 CHECK를 USER/ADMIN으로 제한한다. 이미 적용된 V1·V3는 수정하지 않는다. 사용자 ID·42 ID·생성 시각·관계 참조는 보존한다.
- 역할 변경은 PostgreSQL 트랜잭션 advisory lock 키 `42420001`을 공통으로 획득한다. 잠금 이후 요청자 ADMIN 여부를 다시 검사하고 대상 행을 잠근 뒤 ADMIN 수를 확인해 변경한다. 한 트랜잭션이 끝난 뒤 다음 변경이 진행하므로 서로 다른 두 대상의 동시 회수에도 마지막 ADMIN을 보호한다. 애플리케이션 인스턴스가 여러 개여도 DB에서 같은 잠금을 공유한다.
- 이미 USER인 대상의 회수·ADMIN 대상의 부여는 204를 유지한다. 대기 중 권한을 잃은 ADMIN의 요청은 재검사에서 403으로 거절한다. 기존 세션의 다음 요청은 현재 DB 역할을 반영한다.
- 회장 변경은 기존 대상 동아리 행 잠금·복합 유니크 제약을 유지한다. 마지막 동아리 회장 회수는 허용하며 마지막 전역 ADMIN 보호와 구분한다.
- [초기 입력 SQL](../scripts/db/bootstrap-initial-data.sql)의 psql 변수는 `admin_intra_id`·`admin_login`·`admin_name`으로 바꾼다. 기존 `super_*` 변수 파일은 이름을 변경해야 한다. 애플리케이션 환경변수 변경은 없다.
- 첫 ADMIN이 없는 빈 DB는 운영 초기 입력이 필요하다. 직접 SQL은 API 보호 규칙을 우회할 수 있다. 원래 실행 중인 Compose 서비스·운영 DB는 이번 테스트에서 재시작·변경하지 않는다. 새 코드 기동 시 V11을 적용한다.

2026-10-17 후속 변경: ARCHIVED의 회장 지정·회수는 ADMIN도 금지하도록 사용자 합의가 변경됐다. 아래 검증은 당시 기록이며 현재 제한은 [아카이브 변경 금지](domain-model.md#아카이브-변경-금지--2026-10-17-추가-확정)를 따른다.

## 검증 기록

검증 대상은 BE `4322e15` 이후 이번 변경 작업본이다. 기존 미커밋 OAuth 디버깅·환경변수 변경은 보존하며 기능 커밋에서 제외한다. 테스트는 별도 Testcontainers PostgreSQL에서 수행하고 원래 실행 DB에는 테스트 데이터를 넣지 않는다.

- 전체 빌드: `./gradlew -I scripts/verification/fe-live.init.gradle -PfeRepoPath=../42-clubs-FE spotlessApply build feLiveTest --offline` 성공. 기본 테스트 170개, 실패·오류·생략 0개(기존 미커밋 디버깅 테스트 포함). 커밋 대상만의 격리 빌드는 아래에 별도 기록한다.
- 커밋 대상 격리 빌드: 기존 미커밋 작업을 제외한 index 스냅샷에서 `./gradlew build --offline` 성공. 기본 테스트 169개, 실패·오류·생략 0개. 로그 `/private/tmp/clubs-admin-only-isolated-build.log`. 소스는 `/private/tmp/42-clubs-admin-only-xg5ijt7w`, 상세 결과는 해당 경로의 `build/reports/tests/test/index.html`.
- PostgreSQL V10→V11 이관: SUPER_ADMIN/ADMIN/USER의 역할 전환·ID·생성 시각·회장·부원 참조 보존과 SUPER_ADMIN 재입력 차단 검증.
- HTTP·서비스 권한 검증: ADMIN의 여섯 관리 API, USER·비로그인 거절, 역할 검색·필터, CSRF·입력·대상 없음·반복 변경, 회장 동아리 격리, 기존 세션의 본인 회수 반영 및 마지막 ADMIN 409 검증.
- 동시성: 두 ADMIN의 동시 본인 회수에서는 한 건 성공·한 건 보호 오류, 상호 회수에서는 한 건 성공·대기 후 권한 상실한 요청 거절, 결과 ADMIN 한 명 유지 검증.
- FE API 경계의 실제 HTTP 검증: 10개 시나리오 그룹·147개 API 요청 통과. FE 작업본의 실제 API 모듈을 읽기 전용으로 로드하고 Axios HTTP·세션·CSRF·DB·사진 조회를 통과시켰다. OAuth 제공자만 테스트 대역이며 실제 42·브라우저·화면의 권한 분기·버튼·캐시는 이번 검증 범위에 포함하지 않는다.
- 결과: `build/reports/tests/test/index.html`, `build/reports/tests/feLiveTest/index.html`. 로컬 실행 로그: `/private/tmp/clubs-admin-only-verified.log`. FE 경로 `../42-clubs-FE`의 기존 미커밋 작업본을 읽었으며 파일은 수정하지 않았다.

## FE 인계 사항 — 구현 제외

이번 작업은 백엔드만 완료한다. FE 코드·문서·설정·작업 기록은 수정하지 않는다. 현재 프론트의 SUPER_ADMIN 전용 분기 때문에 새 ADMIN 계정은 권한 관리 기능을 화면에서 조작하지 못할 수 있으며 이를 연동 완료로 표시하지 않는다.

| FE 위치 | 이후 반영할 사항 |
| --- | --- |
| src/features/auth/api.ts, src/shared/types.ts | 서버 역할을 USER/ADMIN으로 정리하고 기존 최고 관리자 전용 설명 수정 |
| src/features/auth/queries.ts | 운영자·회장 관리 쿼리 활성 조건을 ADMIN으로 변경 |
| src/features/auth/AccessManagePage.tsx | 관리 조작을 ADMIN에 허용하고 SUPER_ADMIN 보호 UI 제거. 본인 회수 성공 시 내 정보 재조회·관리 캐시 정리 및 화면 권한 반영 |
| src/shared/api/errors.ts | LAST_ADMIN_PROTECTED 충돌의 사용자 안내 |
| 공지·부원 명단 등의 역할 검사와 관리 화면 테스트 | SUPER_ADMIN 분기 제거, ADMIN 운영 권한과 독립된 회장·부원·주최자 조건 유지 |
| docs/BE_REQUIREMENTS.md, docs/FE_BE_API_GAP.md, docs/FEATURE_BACKLOG.md | 과거 요구·현재 확정 정책을 구분해 ADMIN 통합과 FE 미연결 상태 반영 |

프론트 화면 변경·브라우저 조작 확인은 별도 FE 작업으로 남긴다. 백엔드 정책 결정이나 기능 구현의 미정 사항은 없으며 4단계 확장 후보는 이 합의로 자동 채택하지 않는다.
