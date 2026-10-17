# FE 연동 1단계 검증 기록

검증일: 2026-10-17. 판정: **기존 API의 자동 검증과 FE 함수의 실제 HTTP 연동 확인, 실제 42 로그인·브라우저 화면은 미검증**.

[구현 계획](fe-integration-plan.md#1단계-기존-api-연동-검증)의 1단계를 실행한 기록이다. 미검증 조건이 남아 있으므로 단계 전체 완료로 판정하지 않는다.

## 대상과 환경

- BE 기준 HEAD: `bcc3372`, develop. 이번에 추가한 검증 도구와 아래 기존 미커밋 작업을 포함한 작업본에서 실행했다.
- BE 기존 작업: SecurityConfig·FortyTwoOAuth2UserService, 로컬 OAuth 프로필 디버그 클래스/프로필/테스트, 인증·환경변수 문서와 `.env.example`, `.github/`. 이 작업은 이번 커밋에서 제외했다. 따라서 이 기록은 기준 HEAD 단독 검증 결과가 아니다.
- FE 기준 HEAD: `6fa799b`, main. README·실행 안내·라우트·main·앱 테스트·Vite 설정 및 `src/features/integration/`의 기존 미커밋 작업이 있는 작업본을 읽었다. FE 파일은 수정하지 않았다.
- Java: Microsoft OpenJDK 17.0.20.1. Node: 26.4.0. Docker Desktop 엔진: 29.4.0. PostgreSQL: Testcontainers의 `postgres:17.6-alpine`.
- HTTP 검증은 기존 Compose와 별도의 DB·Tomcat 포트·임시 사진 디렉터리를 사용한다. 기존 Compose DB에는 시험 데이터를 넣지 않는다. 정상 종료 시 테스트 DB·사진·Vite 캐시를 정리한다.
- FE 소스의 API 함수를 Vite SSR로 로드하고 실제 Axios HTTP 전송을 사용했다. Node에 브라우저 쿠키 저장소가 없어 테스트 도구가 사용자별 JSESSIONID 전송/저장만 담당한다. FE의 CSRF 인터셉터와 업무 API 호출은 그대로 실행한다.
- OAuth 제공자의 토큰·프로필 응답만 로컬 fixture로 대체한다. 실제 42 서비스나 사용자 자격증명을 사용하지 않으며, 이 도구는 운영 코드·기본 빌드 산출물에 포함되지 않는다.

## 실행 결과

| 검증 | 결과 | 증거 |
| --- | --- | --- |
| BE 전체 빌드·테스트 강제 재실행 | 통과: 161 tests, 실패/오류/skip 0 | `./gradlew build --offline --rerun-tasks`, Gradle `test` XML/HTML 보고서 |
| FE 기존 테스트 | 통과: 8 files, 59 tests | `fe-tests.mjs`, 콘솔 요약. 실제 화면 테스트의 HTTP 응답은 fixture이므로 실제 BE 화면 연결 증거와 구분 |
| FE 함수 → 실제 HTTP → BE → PostgreSQL | 통과: 9개 시나리오 묶음, FE API 요청 109건 | `feLiveTest`, `FE-LIVE PASS` 및 `FE-LIVE SUMMARY` 출력 |
| 실제 사진 업로드·저장·응답 URL 조회 | 통과: PNG multipart → 상세 이미지 URL → HTTP 200 이미지 | 위 HTTP 검증과 DB activity_images 1행 확인 |
| 실제 42 제공자 로그인·FE 복귀 | 미실행 | 로컬 fixture의 인가 코드 교환·리다이렉트만 확인 |
| 브라우저에서 실제 BE 화면 조작·새로고침·계정 캐시 분리 | 미실행 | Node의 API 함수 실행 및 jsdom의 fixture 화면 테스트와 별도 확인 필요 |

이번에 검증한 시나리오에서 BE 계약 불일치는 발견하지 못했다. 초기 검증 도구의 비공개 공지 403·주최자 취소 403 기대값은 기존 API 명세에 맞춰 각각 404·409로 바로잡고 재실행했다. BE 정책이나 기능을 변경한 사항이 아니다.

## 시나리오와 기대·실제 결과

아래 정상·거절 기대값은 현재 API 계약을 따른다. HTTP 묶음은 [FE 검증 코드](../scripts/verification/fe-live.mjs), 시간 경계·경쟁 조건 등 세부 검증은 기존 BE 통합 테스트에서 확인한다.

| 범위 | 입력·시나리오 | 기대 결과와 실제 결과 | 검증 증거 |
| --- | --- | --- | --- |
| 동아리 조회·페이지 | ACTIVE 105개·ARCHIVED 1개, 기본 size=20 및 FE 전체 조회 size=100 | 기본 20개/총105개, 전체 조회105개, 아카이브 포함106개. 없는 상세404. 모두 일치 | HTTP 묶음 `public pagination and authentication`; ClubQueryIntegrationTests |
| 공개·비공개 공지 | 운영 공지1개, PUBLIC/MEMBERS_ONLY 각1개; 익명·부원 | 익명 목록1개·비공개 상세404, 부원 목록2개, 소속 불일치404, 운영 공지 공개200. 일치 | HTTP `notices visibility and resource nesting`; NoticeAccessIntegrationTests |
| 생성·사진 | 회장의 유효 활동+PNG1장, 다른 동아리 회장 관계 없음, 빈 제목 | 정상 생성201·주최자 자동 참가1명·이미지 조회200, 다른 동아리 작성403, 빈 제목400. 일치 | HTTP `activity multipart and real photo URL`; EventIntegrationTests, LocalPhotoIntegrationTests |
| 활동 참가·취소 | 정원3명, 주최자1명+일반사용자, 취소·재참가·중복·초과 | 변경204, 본인 상태 CONFIRMED/CANCELLED, 중복/정원 초과409, 주최자 취소409. 일치 | HTTP `activity join cancel rejoin and organizer attendance`; EventIntegrationTests |
| 시간·동시성 | 마감 시각에 참가/취소, 마지막 한 자리 동시 신청, 같은 사용자 동시 신청 | 마감 요청409, 정원 초과 없음, 참가 관계 중복 없음. 기존 활동·회차 양쪽 테스트 통과 | EventIntegrationTests: exactDeadlineBlocksJoinAndCancel, lastSeatConcurrentRequestsNeverOverbook, concurrentDuplicateParticipantStaysOneAndRecordedCancelledCannotRejoin |
| 출결·명단 | 주최자가 PRESENT/UNCHECKED 변경, 일반사용자/비주최 ADMIN 명단 접근 | 주최자 변경204·본인 상태 반영, 출석 후 취소409, 비주최 명단403. 일치 | HTTP 활동/회차 묶음; EventIntegrationTests |
| 소모임·회차 | 일반사용자 주최, 첫 회차·다음 회차 생성, 첫 회차만 신청 | 모임 카드1개·회차2개, 다음 회차 본인 참가 null, 첫 회차 출결 독립, 비주최 추가403, 잘못된 상위 모임404. 일치 | HTTP `gathering first and next session independent participation`; EventIntegrationTests |
| 회원·권한 | SUPER_ADMIN의 회장 지정, ADMIN의 부원 등록2회, 회장/ADMIN이지만 비부원인 명단 조회 | 관리 관계·부원 관계 분리, 중복 등록 후 가입수1, 비부원403, 부원 명단200. 일치 | HTTP `role management and membership are independent`; MembershipNoticeIntegrationTests |
| 운영 권한 변경 | SUPER_ADMIN의 ADMIN 지정·회수, 기존 세션 조회, ADMIN의 권한관리 시도 | 기존 세션 role 즉시 반영, ADMIN 회장 지정/역할필터403, SUPER_ADMIN 역할 회수409. 일치 | 위 HTTP 묶음; UserAdministrationIntegrationTests, AuthIntegrationTests |
| 마이페이지·달력 데이터 | 활동과 첫 회차 참가, 다음 회차 미참가, 서울 자정 UTC 입력 | 내 동아리1개, CLUB/GATHERING 그룹, 참가 회차만1개, `2026-10-17T15:00:00Z`→한국 `2026-10-18`/00:00. 일치 | HTTP `mypage and Seoul calendar source data`; EventIntegrationTests의 내역/시작 시각 경계 테스트 |
| 세션·CSRF | fixture 계정5개 로그인, CSRF 없는 로그아웃, 정상 로그아웃, 이전 토큰 재사용 | 로그인 후 각 계정·기존 역할 유지, 성공 복귀 헤더 `/#/auth/callback`, 무토큰403·정상204·이후 me401(함수는 null)·이전 토큰403. 일치 | HTTP OAuth/CSRF 묶음; AuthIntegrationTests의 세션 회전·로그인 전 토큰 무효화·CORS 테스트 |

시간/정원/권한 테스트에서 어떤 조건을 먼저 검사하는지는 기존 명세를 따른다. 여러 위반 조건이 겹칠 때 단일 오류 코드의 우선순위를 새 정책으로 추가하지 않았다.

## 재실행

BE 루트에서 실행한다. Java 17, 실행 중인 Docker, 설치된 FE `node_modules`, Node 22.12 이상이 필요하다. FE 경로는 실제 checkout 경로로 바꾼다. 처음 실행하거나 캐시가 없으면 Gradle 의존성·Docker 이미지 다운로드가 필요하므로 `--offline`을 제거한다. `--offline`은 Gradle 옵션이며 Docker 이미지 다운로드까지 금지하지는 않는다.

```sh
./gradlew build --offline --rerun-tasks

node scripts/verification/fe-tests.mjs ../42-clubs-FE

./gradlew -I scripts/verification/fe-live.init.gradle \
  -PfeRepoPath=../42-clubs-FE feLiveTest --offline
```

`feRepoPath`는 이 검증용 Gradle 속성으로 기본값이 없으며 실행 시 지정해야 한다. 애플리케이션 환경변수나 FE 설정 변경은 필요 없다. `feLiveTest`는 외부 FE 변경을 캐시 성공으로 처리하지 않도록 매번 실행된다. FE 테스트의 생성 캐시는 임시 디렉터리로 지정한다. 테스트 도구는 쿠키·CSRF 토큰·전체 Axios 오류 객체를 로그에 출력하지 않는다.

검증용 Java 포맷 확인은 같은 init script로 `spotlessCheck`를 실행한다. 일반 `build`는 기본 BE 테스트를 유지하고, 외부 FE가 필요한 검증은 별도의 `feLiveTest` 태스크로 실행한다. DB와 사진 저장소의 HTTP 동작이 실물이지만 이 도구로 브라우저의 SameSite·CORS 적용이나 화면 레이아웃을 검증했다고 주장하지 않는다.

일반 BE 보고서는 `build/reports/tests/test/index.html`, 별도 연동 보고서는 `build/reports/tests/feLiveTest/index.html`과 `build/test-results/feLiveTest/`에 생성된다. 현재 실행의 로컬 로그는 `/private/tmp/clubs-fe-tests.log`, `/private/tmp/clubs-stage1-final.log`, `/private/tmp/clubs-fe-live-final.log`이며 저장소에 커밋하지 않는다. 최종 로그에는 Gradle 속성으로 FE 경로를 지정한 재실행과 검증 코드의 `spotlessCheck` 통과가 포함된다. 보고서와 로그는 후속 실행으로 덮어써질 수 있으며 이 문서가 이번 실행의 결과 요약이다.

## 남은 확인과 결정

| ID | 구분 | 남은 작업·완료 증거 |
| --- | --- | --- |
| VERIFY-01 | 실제 인증 미검증 | 실제 42 계정으로 로그인·성공/실패 복귀·세션·CSRF·로그아웃을 실행하고 환경·결과 기록. 제공자 등록 콜백과 FE 성공 복귀 주소를 구분 |
| VERIFY-02 | 실제 화면 미검증 | 실제 BE를 연결한 브라우저에서 조회·사진·작성·신청/취소·출결·마이페이지·새로고침·계정 변경 캐시 분리를 확인 |
| VERIFY-03 | 실행 환경 미검증 | 제출 환경의 Vite/정적 서빙 프록시, Nginx, CORS·쿠키·사진 호스트를 실제 브라우저에서 확인. 이번 검증은 별도 Tomcat 직접 연결 |

새로운 업무 정책의 결정이 필요한 불일치는 이번 검증에서 발견하지 못했다. DETAIL/AUTHZ/EXT 미정 항목은 그대로 남겨 두었으며 구현에 착수하지 않았다. 위 항목은 검증 미실행 기록이며, 미정 업무 정책을 임의로 확정한 항목이 아니다.
