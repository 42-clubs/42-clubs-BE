# API 명세

인증·동아리 조회·사용자 검색·권한 관리, 공지·활동·소모임 및 마이페이지 API를 구현했습니다. 전체 46개 엔드포인트(기존 인증·동아리 조회 7개 + 업무 API 38개 + 로컬 사진 조회 1개)를 아래 표에 정리했습니다. 아래 경로·DTO·상태·오류 계약과 [요청·응답 예시](mvp-api-examples.md)를 기준으로 연동합니다.
[현재 MVP 정책](domain-model.md#축소-mvp--2026-10-14-확정)과 [후속 API](future-design.md#후속-api-초안)를 구분해 사용합니다. 46개는 HTTP 메서드·경로 조합의 정의 수이며 OAuth 2개는 AUTH_ENABLED=true, 로컬 사진 조회 1개는 PHOTO_STORAGE_MODE=local일 때만 등록됩니다.

- [마이페이지·내 권한](#마이페이지내-권한--구현-계약)
- [구현 대조 기준](#구현-대조-기준--2026-10-17)
- [공통 원칙](#공통-원칙)
- [먼저 구현할 순서](#먼저-구현할-순서)
- [인증 — 구현 완료](#인증--구현-계약)
- [동아리 조회 — 구현 완료](#동아리-공개-조회--구현-계약)
- [사용자 검색·권한 관리 — 구현 완료](#사용자-검색권한-관리--구현-계약)
- [부원 관리 3개](#동아리-부원-관리)
- [동아리 공지 3개](#공지-작성조회)
- [메인 화면 운영 공지 3개](#운영-공지-작성조회)
- [활동·사진 3개](#동아리-활동-작성조회)
- [활동 참가·취소·출결 5개](#활동-참가취소출결)
- [소모임·회차 6개](#소모임회차-작성조회)
- [회차 참가·취소·출결 5개](#회차-참가취소출결)
- [페이지·날짜 공통 계약](#dto와-프론트-연동-계약)
- [참가·출결 DTO와 오류](#활동소모임-구현-계약)

## 먼저 구현할 순서

기능별 DB 작업·의존성·검증 조건은 [MVP 구현 계획](mvp-implementation-plan.md)에서 관리합니다. 계획에서는 권한 관리 화면의 선행 의존성인 사용자 검색을 부원 관리보다 먼저 구현합니다.

현재 구현된 인증 5개(OAuth 시작·콜백 포함)와 동아리 조회 2개를 활용합니다. 업무 엔드포인트는 총 **38개**이며 모두 구현했습니다. 활동 사진은 작성 요청에 포함하므로 별도 업로드 API를 추가하지 않습니다. 아래 순서는 개발 권장 순서이며 각 단계의 경로·권한은 뒤의 표가 기준입니다.

| 순서 | 구현 묶음 | API 수 | 상태 |
| --- | --- | --- | --- |
| 0 | 역할·권한 기반·초기 DB 입력 절차 | 0 | 완료 |
| 1 | 사용자 검색·최고 관리자 권한 관리 | 6 | 완료 |
| 2 | 부원 등록·현황·명단 | 3 | 완료 |
| 3 | 동아리·운영 공지 작성·조회 | 6 | 완료 |
| 4 | 동아리 활동 작성·조회 | 3 | 완료 |
| 5 | 활동 참가·취소·기본 출결 | 5 | 완료 |
| 6 | 소모임·회차 작성·조회 | 6 | 완료 |
| 7 | 회차 참가·취소·기본 출결 | 5 | 완료 |
| 8 | 마이페이지·동아리별 내 권한 | 4 | 완료 |

초기 동아리·회장·최고 관리자는 전달받은 정보로 [초기 DB 입력 절차](initial-data-setup.md)에 따라 입력합니다. 이후 권한 관리는 아래 최고 관리자 API를 사용합니다. 동아리 생성 API는 MVP에 추가하지 않습니다. 0단계에서 SUPER_ADMIN 역할과 ClubManager 관계·작성 권한 검사를 구현했습니다. ClubMember는 V4와 2단계 API로 구현했습니다.

동아리 활동은 해당 회장이 작성하며 작성자가 주최자입니다. 주최자 선택 UI·후보 검색 API·organizerId 요청 필드는 필요하지 않습니다.

글 수정·삭제, 일정 변경, 탈퇴, 가입 신청·승인, 자동 반복 회차, 블랙홀 처리, 출결 통계·지각·결석은 이 구현 목록에 포함하지 않습니다. 기록 보존 기간은 팀 합의 전까지 보류합니다.

## 공통 원칙

- 업무 API 기본 경로는 `/api/v1`이며 모든 엔드포인트 표에 전체 경로를 표시합니다. OAuth 시작·콜백만 예외입니다.
- JSON 본문은 `Content-Type: application/json`으로 전송합니다. 활동 생성만 `multipart/form-data`이며 activity 파트는 application/json입니다. GET과 본문 없는 변경 요청에는 JSON 본문을 붙이지 않습니다.
- 본인 식별자는 인증 정보로 결정합니다. 신청 요청에서 임의 userId를 받아 다른 사람으로 신청할 수 없습니다.
- 변경 요청은 세션 쿠키와 CSRF 헤더가 필요합니다. CSRF 토큰 누락·불일치는 403입니다.
- 운영진은 ADMIN·SUPER_ADMIN, 동아리 회장은 해당 ClubManager 관계, 활동·소모임 주최자는 해당 organizer_id로 검사합니다. 동아리 공지 작성·비공개 관리 조회는 운영진 권한으로도 허용합니다.
- 중첩 경로는 자식의 소속 관계도 검사합니다. 경로의 clubId/gatheringId만 검증하고 다른 부모의 자식을 수정할 수 없습니다.
- 공개 동아리 공지 조회도 로그인 쿠키를 포함해야 본인의 비공개 열람 권한이 반영됩니다. 익명 요청으로 보내면 PUBLIC만 조회됩니다.
- 참가자 명단과 출결은 공개 상세 응답에 포함하지 않습니다. 주최자는 관리 명단, 참가자는 본인 상태를 조회합니다.
- 목록 조회 API는 기본적으로 page=0, size=20(최대 100), `{content, page, size, totalElements, totalPages}` 응답을 사용합니다. 예외로 [사용자 가입 현황](#동아리-부원-관리)의 MembershipResponse는 전체 가입 동아리 배열과 집계를 반환하며 페이지네이션하지 않습니다.
- 생성은 201, 조회는 200, 부원 등록·참가·취소·출결 기록은 본문 없는 204입니다.
- 인증 실패 401, 권한 부족 403, 대상 없음 404, 입력 오류 400, 정원·상태 충돌 409를 기본으로 합니다.
- DELETE 참여 요청은 관계 물리 삭제가 아닌 취소 상태 변경입니다. 글 삭제 API는 현재 MVP에서 제외합니다.

## 인증 — 구현 계약

로그인 정책은 [도메인 기준](domain-model.md#로그인-및-인증--확정), 환경 설정은 [실행 안내](../README.md#42-로그인-실행)를 따릅니다. 아래 OAuth 경로 두 개는 `/api/v1` 접두사를 사용하지 않습니다.

| 메서드 | 경로 | 동작 | 권한 |
| --- | --- | --- | --- |
| GET | /oauth2/authorization/42 | 42 인가 화면으로 302 이동, 세션에 OAuth state 저장 | 공개, AUTH_ENABLED=true |
| GET | /login/oauth2/code/42 | 코드·state 검증 및 사용자 생성·갱신, 세션 ID 교체, 성공 URL로 302 이동 | OAuth 콜백 |
| GET | /api/v1/auth/csrf | 현재 세션의 CSRF headerName·token, 200 | 공개 |
| POST | /api/v1/auth/logout | 세션 무효화·JSESSIONID 제거, 204 | 현재 세션 CSRF 필요 |
| GET | /api/v1/me | id·intraLogin·displayName·role, 200 | 로그인 |

- 프론트엔드는 API 호출에 세션 쿠키를 포함하고 변경 요청에 `/api/v1/auth/csrf`의 `headerName`을 헤더 이름으로, `token`을 값으로 사용합니다. 로그인·로그아웃 시 CSRF 토큰이 초기화되므로 다시 조회해야 합니다.
- 세션 쿠키는 HttpOnly이며 로컬은 SameSite=Lax, Secure=false입니다. 배포 설정은 환경 변수로 변경합니다. 로그인 상태는 서버 재시작 시 사라집니다.
- 인증 없는 보호 API는 401 `UNAUTHENTICATED`, ADMIN 권한 부족과 CSRF 오류는 403 `ACCESS_DENIED`입니다. OAuth state·코드·42 프로필 오류 및 경산 도메인 불일치는 401 `OAUTH_LOGIN_FAILED`이며 외부 오류 세부 내용은 응답하지 않습니다. 서버 WARN 로그에는 안전하게 분류한 실패 사유와 확인 안내를 기록하며, [로그 확인 안내](environment-variables.md#로그인-실패-서버-로그)를 따릅니다.
- `AUTH_ENABLED=false`이면 OAuth 시작·콜백 기능을 활성화하지 않습니다. 공개 동아리 조회·CSRF API는 유지하며 보호 API는 인증이 필요합니다.
- `/me`의 role은 USER / ADMIN / SUPER_ADMIN입니다. 기존 세션에서도 매 요청 DB 역할을 반영합니다. SUPER_ADMIN은 ADMIN 운영 권한을 포함하고 동아리별 회장·주최자·부원 조건은 별도로 검사합니다. 권한 관리는 아래 구현 계약을 따릅니다.
- `/me`에 이메일·42 API 토큰·42 전체 프로필은 포함하지 않습니다. 사용자 계정은 42 ID 기준으로 중복을 방지하고 역할은 DB 기준입니다.
- JWT 재발급·다른 소셜 계정 연동·사이트 계정 탈퇴는 제공하지 않습니다. 블랙홀 확인과 기존 세션 이용 차단은 MVP에서 제외하며 추후 확장 시 검토합니다.

## 동아리 공개 조회 — 구현 계약

두 API는 로그인 없이 조회할 수 있으며 성공 시 200과 JSON을 반환합니다.
활동도는 현재 MVP에서 제외하며 응답에 포함하지 않습니다. 후속 도입 시 [집계 정책](future-design.md#활동도-계산)을 적용하고 응답 계약을 갱신합니다.
동아리 생성·수정 API와 개발용 초기 데이터는 없으므로 새 DB의 동아리 목록은 비어 있습니다.

| 메서드 | 전체 경로 | 기능 | 권한 | 성공·응답 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/clubs | 동아리 목록 | 공개 | 200, 페이지 응답 |
| GET | /api/v1/clubs/{clubId} | 동아리 상세 | 공개 | 200, ClubDetailResponse |

### 목록 요청

`GET /api/v1/clubs`

| 매개변수 | 기본값 | 허용 범위 |
| --- | --- | --- |
| page | 0 | 0 이상의 정수, Java int 범위. 첫 페이지는 0 |
| size | 20 | 1~100의 정수. 초과하면 자동 축소하지 않고 400 |
| operationStatus | 생략 | ACTIVE / ARCHIVED, 대문자 값 |

`operationStatus=DORMANT`는 지원하지 않으며 400 `INVALID_REQUEST`를 반환합니다.

operationStatus 생략 시 ACTIVE만 조회합니다. 빈 매개변수는 page·size의 기본값 또는 필터 생략으로 처리합니다.
정렬은 ID 오름차순으로 고정하며 사용자 지정 정렬은 지원하지 않습니다.
필터는 페이지네이션 전에 적용하며 `totalElements`는 필터 적용 후 전체 개수입니다.
데이터가 없거나 마지막 페이지를 넘으면 200과 빈 `content`를 반환합니다.

```json
{
  "content": [
    {
      "id": 1,
      "name": "코딩 동아리",
      "summary": "함께 프로젝트를 만드는 동아리",
      "operationStatus": "ACTIVE",
      "recruitmentStatus": "NOT_RECRUITING"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 상세 요청

`GET /api/v1/clubs/{clubId}` — clubId는 1 이상의 Java long 범위 정수입니다.
아카이브된 동아리도 ID로 조회할 수 있습니다.

```json
{
  "id": 1,
  "name": "코딩 동아리",
  "summary": "함께 프로젝트를 만드는 동아리",
  "description": "동아리 상세 소개",
  "operationStatus": "ACTIVE",
  "managers": [
    {"intraLogin": "chair", "displayName": "동아리 회장"}
  ],
  "recruitment": {
    "planned": false,
    "status": "NOT_RECRUITING",
    "startDate": null,
    "endDate": null,
    "closedEarly": false,
    "applicationMethod": null,
    "applicationUrl": null
  },
  "createdAt": "2026-10-11T00:00:00Z",
  "updatedAt": "2026-10-11T00:00:00Z"
}
```

날짜는 `YYYY-MM-DD`, 생성·수정 시각은 UTC ISO 8601 형식입니다.
모집 상태의 응답 값은 `NOT_RECRUITING`(모집 안 함), `SCHEDULED`(모집 예정), `OPEN`(모집 중), `CLOSED`(마감)입니다.
계산 정책과 날짜 기준은 [도메인 모집 정책](domain-model.md#모집-정보와-상태--확정)을 따릅니다.
상세의 `managers`는 현재 회장 관계의 사용자 정보를 공개용 형태로 반환합니다. 항목은 `intraLogin`·`displayName` 두 문자열만 포함하며 내부 사용자 ID 오름차순으로 정렬합니다. 회장이 없으면 `[]`이며 복수 회장을 모두 반환합니다. 사용자 내부 ID·42 고유 ID·이메일·연락처·전역 역할·권한 부여 시각은 노출하지 않습니다. 회장 지정·회수와 표시 정보 변경은 이후 조회에 반영됩니다.
ACTIVE·ARCHIVED 동아리 모두 같은 회장 정보 계약을 적용합니다. 회원 가입 여부는 회장 표시 조건이 아니며, 회장 공개 정보로 부원 명단·최고 관리자 전용 회장 API의 접근 권한이 확대되지는 않습니다. 목록에는 `managers`를 추가하지 않습니다.
회원 수·공개 연락처·최근 참여는 아직 상세 응답에 포함하지 않습니다. 미정 사항은 [2단계 검토 기록](fe-integration-stage2-review.md)에서 관리합니다.

### 오류 응답

| 조건 | HTTP | code |
| --- | --- | --- |
| 허용 범위를 벗어난 페이지·크기·ID, 숫자 변환 실패, 잘못된 운영 상태 | 400 | INVALID_REQUEST |
| 유효한 ID이지만 해당 동아리가 없음 | 404 | RESOURCE_NOT_FOUND |

400 응답의 message는 `요청 값의 형식 또는 범위를 확인해 주세요.`입니다.
404 응답은 다음과 같습니다.

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "동아리를 찾을 수 없습니다."
}
```

위 오류는 동아리 조회 계약입니다. 인증 오류는 위 인증 계약을 따르며 쓰기 API의 충돌 오류는 아래 활동·소모임 구현 계약을 따릅니다.

## 사용자 검색·권한 관리 — 구현 계약

세션 인증을 사용하며 아래 경로는 `/api/v1` 접두사를 포함합니다. 권한은 매 요청 DB 기준으로 검사합니다.

| 메서드 | 경로 | 기능 | 권한 | 성공 |
| --- | --- | --- | --- | --- |
| GET | /api/v1/admin/users | 사용자 검색, 선택적 role=ADMIN 필터 | ADMIN·SUPER_ADMIN; 역할 필터는 SUPER_ADMIN 전용 | 200 |
| PUT | /api/v1/admin/users/{userId}/admin-role | ADMIN 부여 | SUPER_ADMIN | 204 |
| DELETE | /api/v1/admin/users/{userId}/admin-role | ADMIN 회수, USER로 변경 | SUPER_ADMIN | 204 |
| GET | /api/v1/admin/clubs/{clubId}/managers | 해당 동아리 회장 목록 | SUPER_ADMIN | 200 |
| PUT | /api/v1/admin/clubs/{clubId}/managers/{userId} | 회장 권한 부여 | SUPER_ADMIN | 204 |
| DELETE | /api/v1/admin/clubs/{clubId}/managers/{userId} | 회장 권한 회수 | SUPER_ADMIN | 204 |

### 검색·목록 요청과 응답

- 사용자 검색의 `intraLogin`은 생략 가능하며 생략 시 전체 사용자를 조회합니다. 지정하면 1~100자이며 공백만 있는 값·빈 값은 400입니다. 앞뒤 공백을 제거한 뒤 대소문자를 구분하지 않고 로그인명에 부분 일치합니다. `%`·`_`·역슬래시는 검색 와일드카드가 아닌 일반 문자입니다.
- `role`은 생략하거나 정확히 `ADMIN`만 지정합니다. 빈 값·다른 역할은 400입니다. `role=ADMIN`은 SUPER_ADMIN을 포함하지 않는 정확한 ADMIN 목록이며 `intraLogin`과 AND로 조합합니다. ADMIN이 유효한 역할 필터를 지정하면 403입니다.
- 사용자 검색과 회장 목록은 `page=0`, `size=20`이 기본입니다. page는 0 이상, size는 1~100이며 빈 page·size는 기본값입니다. 범위 초과·형식 오류는 400입니다. 사용자 검색은 사용자 ID, 회장 목록은 회장 사용자 ID 오름차순으로 고정합니다.
- 응답은 `content`, `page`, `size`, `totalElements`, `totalPages`입니다. 결과가 없거나 페이지를 벗어나면 200과 빈 content입니다. 회장 목록은 해당 clubId 관계만 포함합니다.
- ADMIN의 사용자 검색 항목은 `id`·`intraLogin`·`displayName`만 제공합니다. SUPER_ADMIN의 사용자 검색에는 `role`을 추가합니다. 회장 목록 항목은 역할을 제외한 사용자 요약입니다. 42 고유 ID·원본 프로필·이메일은 노출하지 않습니다.

SUPER_ADMIN의 `GET /api/v1/admin/users?intraLogin=chair&page=0&size=20` 응답 예시:

```json
{
  "content": [{"id": 42, "intraLogin": "chair", "displayName": "Chair", "role": "USER"}],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

ADMIN에게는 위 항목의 `role` 필드가 없으며, 회장 목록도 같은 방식으로 role을 제외합니다.

### 권한 변경·오류 계약

- 경로 ID는 1 이상의 Java long 범위 정수이며 잘못된 값은 400 `INVALID_REQUEST`입니다. 모든 변경 요청은 본문 없이 세션 쿠키와 CSRF 헤더를 사용합니다.
- 반복 부여·회수는 204입니다. 이미 USER인 사용자의 ADMIN 회수와 존재하지 않는 회장 관계의 회수도 성공합니다. 단, 대상 사용자·동아리가 실제로 없으면 404 `RESOURCE_NOT_FOUND`입니다.
- SUPER_ADMIN을 대상으로 ADMIN 부여·회수를 시도하면 본인·타인 모두 409 `SUPER_ADMIN_ROLE_PROTECTED`입니다. 최고 관리자 자체 지정·회수는 별도 운영 DB 절차를 유지합니다.
- 인증 없는 요청은 401 `UNAUTHENTICATED`, 권한 부족·CSRF 누락/불일치는 403 `ACCESS_DENIED`입니다. CSRF 검사가 먼저 적용되므로 인증 없이 CSRF도 빠진 변경 요청은 403일 수 있습니다.
- ADMIN 변경은 대상 사용자 행 잠금, 회장 변경은 대상 동아리 행 잠금으로 직렬화합니다. 회장 유니크 제약도 유지하며 동시 반복 요청에서 중복 관계를 만들지 않습니다.
- 동아리 운영 상태와 관계없이 회장 관계를 부여·회수할 수 있습니다. 활동 작성에는 ACTIVE 동아리의 현재 회장 관계가 필요합니다. 공지는 ACTIVE 동아리 회장 또는 운영진이 작성합니다. 다른 동아리 관계는 변경하지 않습니다.
- 역할 회수는 기존 세션의 다음 요청부터, 회장 회수는 다음 권한 검사부터 반영됩니다. 회장 지정으로 부원 관계를 생성하지 않으며 권한 변경으로 계정·부원·게시물·참가 기록을 삭제하지 않습니다.

## MVP API — 구현 계약

아래 28개 API는 2~7단계와 운영 공지 확장으로 구현했습니다. 경로는 `/api/v1`을 포함한 전체 경로입니다. 요청·응답을 연결하는 예시는 [MVP API 연결 예시](mvp-api-examples.md)를 참고합니다. 별도 사진 업로드, 글 수정·삭제, 가입 신청·승인·탈퇴, 자동 반복 회차 API는 제공하지 않습니다.

### 동아리 부원 관리

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| GET | /api/v1/admin/users/{userId}/clubs | 대상 사용자의 가입 현황 | ADMIN·SUPER_ADMIN | 없음 | 200, MembershipResponse |
| PUT | /api/v1/admin/clubs/{clubId}/members/{userId} | 부원 등록·재등록 | ADMIN·SUPER_ADMIN, 대상 동아리 ACTIVE | 없음 | 204, 본문 없음 |
| GET | /api/v1/clubs/{clubId}/members | ACTIVE 부원 명단 | 해당 동아리 ACTIVE 부원 | 없음 | 200, Page&lt;UserSummary&gt; |

- 명단 조회만 page·size를 받으며 사용자 ID 오름차순입니다. 사용자 검색은 기존 `GET /api/v1/admin/users`를 사용합니다.
- ADMIN·SUPER_ADMIN·회장도 해당 동아리의 ACTIVE 부원 관계가 있어야 명단을 조회할 수 있습니다.
- ACTIVE 반복 등록은 관계·등록자·가입 시각을 유지합니다. LEFT 재등록은 기존 관계를 갱신합니다. 가입 수에 따른 자동 등록 거절은 없습니다.
- 없는 사용자·동아리는 404, 비활성 동아리 등록은 409 `CLUB_INACTIVE`입니다.

**MembershipResponse**

| 필드 | JSON 타입 | 의미 |
| --- | --- | --- |
| userId | integer | 가입 현황 대상 사용자 ID |
| clubs | array | ACTIVE 부원 관계의 동아리, 동아리 ID 오름차순; LEFT 관계 제외 |
| clubs[].id | integer | 동아리 ID |
| clubs[].name | string | 동아리 이름 |
| clubs[].operationStatus | string | ACTIVE / ARCHIVED |
| activeClubCount | integer | clubs 중 운영 상태가 ACTIVE인 동아리 수 |

가입 현황 응답은 페이지네이션하지 않습니다. 부원 명단의 **UserSummary**는 `{id, intraLogin, displayName}`이며 모두 null이 아닌 값입니다. role·이메일·42 고유 ID는 포함하지 않습니다.

### 공지 작성·조회

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/clubs/{clubId}/notices | 공지 작성 | ACTIVE 동아리의 회장 또는 ADMIN·SUPER_ADMIN | NoticeRequest JSON | 201, CreatedResponse |
| GET | /api/v1/clubs/{clubId}/notices | 공지 목록 | 공개 범위·현재 부원/관리 권한 필터 | 없음 | 200, Page&lt;NoticeSummary&gt; |
| GET | /api/v1/clubs/{clubId}/notices/{noticeId} | 공지 상세 | 공개 범위·현재 부원/관리 권한 검사 | 없음 | 200, NoticeResponse |

**NoticeRequest**

| 필드 | JSON 타입 | 필수·검증 |
| --- | --- | --- |
| title | string | 필수, 공백만 불가, 최대 200자 |
| content | string | 필수, 공백만 불가, 최대 50,000자 |
| visibility | string 또는 null | 선택, MEMBERS_ONLY / PUBLIC; 생략·null이면 MEMBERS_ONLY |

작성자는 세션 사용자로 결정합니다. 생성 응답은 `{"id":10}`이며 `Location: /api/v1/clubs/1/notices/10`을 제공합니다. 목록은 page·size를 받고 createdAt 내림차순 → ID 내림차순으로 정렬합니다.

| 응답 필드 | JSON 타입 | 목록 항목 | 상세 |
| --- | --- | --- | --- |
| id | integer | 공지 ID | 동일 |
| clubId | integer | 소속 동아리 ID | 동일 |
| title | string | 제목 | 동일 |
| visibility | string | MEMBERS_ONLY / PUBLIC | 동일 |
| createdAt | string | UTC ISO 8601 작성 시각 | 동일 |
| authorId | integer | 필드 없음 | 작성자 사용자 ID |
| content | string | 필드 없음 | 본문 |

- PUBLIC은 비로그인 방문자를 포함해 조회할 수 있습니다. MEMBERS_ONLY는 해당 동아리 ACTIVE 부원·현재 회장·ADMIN·SUPER_ADMIN만 조회할 수 있습니다. 운영진·회장에는 부원 관계를 요구하지 않으며 다른 동아리 회장·LEFT 부원은 외부인으로 처리합니다.
- 목록은 조회 가능한 공지만 DB에서 필터링한 뒤 페이지네이션·totalElements·totalPages를 계산합니다. 익명·외부인의 비공개 상세 요청은 401/403 대신 404입니다.
- 상세는 noticeId와 clubId의 소속 일치를 확인합니다. 다른 동아리 조합·삭제된 공지는 404입니다. 없는 동아리의 목록도 404입니다.
- 일반 부원·다른 동아리 회장의 작성은 403이며 운영진도 ARCHIVED 동아리에는 작성할 수 없습니다. 기존 공지는 V8에서 MEMBERS_ONLY로 전환됩니다.

### 운영 공지 작성·조회

메인 화면에서 플랫폼 운영진이 전체 사용자에게 전달하는 공지입니다. 동아리 공지를 모은 피드가 아니며 별도 announcements 테이블·ID를 사용합니다.

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/announcements | 운영 공지 작성 | ADMIN·SUPER_ADMIN | AnnouncementRequest JSON | 201, CreatedResponse |
| GET | /api/v1/announcements | 메인 화면 운영 공지 목록 | 비로그인 포함 공개 | 없음 | 200, Page&lt;AnnouncementSummary&gt; |
| GET | /api/v1/announcements/{announcementId} | 운영 공지 상세 | 비로그인 포함 공개 | 없음 | 200, AnnouncementResponse |

AnnouncementRequest는 필수 title(공백만 불가·최대 200자), content(공백만 불가·최대 50,000자)만 받습니다. clubId·visibility를 사용하지 않고 작성자는 세션에서 결정합니다. 생성은 `{"id":10}`, `Location: /api/v1/announcements/10`을 반환합니다.

| 응답 필드 | JSON 타입 | 목록 항목 | 상세 |
| --- | --- | --- | --- |
| id | integer | 운영 공지 ID | 동일 |
| title | string | 제목 | 동일 |
| createdAt | string | UTC ISO 8601 작성 시각 | 동일 |
| authorId | integer | 필드 없음 | 작성한 운영진 ID |
| content | string | 필드 없음 | 본문 |

목록은 page·size를 받으며 createdAt 내림차순 → ID 내림차순입니다. 가입 여부를 검사하지 않습니다. 삭제된 글은 목록에서 제외하고 상세에서 404입니다. USER·회장 관계만 가진 사용자의 작성은 403이며 변경 요청에는 CSRF가 필요합니다.

### 동아리 활동 작성·조회

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/clubs/{clubId}/activities | 활동·사진·주최자 참가 생성 | ACTIVE인 해당 동아리 회장 | multipart: activity + 선택 images | 201, CreatedResponse |
| GET | /api/v1/clubs/{clubId}/activities | 활동 목록 | 공개 | 없음 | 200, Page&lt;ActivityResponse&gt; |
| GET | /api/v1/clubs/{clubId}/activities/{activityId} | 활동 상세 | 공개 | 없음 | 200, ActivityResponse |

생성 응답은 `{"id":20}`이며 `Location: /api/v1/clubs/1/activities/20`을 제공합니다. authorId·organizerId를 요청받지 않고 작성한 회장을 작성자·주최자로 지정합니다. 활동·주최자의 CONFIRMED 참가·사진 연결은 함께 저장하며 주최자도 정원에 포함됩니다.

**ActivityRequest — activity JSON 파트**

| 필드 | JSON 타입 | 필수·검증 |
| --- | --- | --- |
| title | string | 필수, 공백만 불가, 최대 200자 |
| content | string | 필수, 공백만 불가, 최대 50,000자 |
| startsAt | string | 필수, 오프셋 포함 ISO 8601, 생성 시각 이후 |
| recruitmentClosesAt | string | 필수, 오프셋 포함 ISO 8601, 생성 시각 이후·startsAt 이전 |
| capacity | integer | 필수, 1~2,147,483,647, 주최자 포함 |

**ActivityResponse**

| 필드 | JSON 타입 | 의미·목록과 상세 차이 |
| --- | --- | --- |
| id | integer | 활동 ID |
| clubId | integer | 소속 동아리 ID |
| organizerId | integer | 주최자 사용자 ID |
| title | string | 제목 |
| content | string 또는 null | 목록은 null, 상세는 본문 |
| startsAt | string | UTC ISO 8601 개최 시각 |
| recruitmentClosesAt | string | UTC ISO 8601 모집 마감 시각 |
| capacity | integer | 주최자 포함 전체 정원 |
| currentParticipantCount | integer | CONFIRMED 참가 수, 주최자 포함 |
| remainingCapacity | integer | 잔여 정원, 최소 0 |
| recruiting | boolean | 마감·개최 전이며 잔여 정원이 있으면 true |
| dDay | string | 한국 날짜 기준 D-N / D-Day / 종료 |
| representativeImageUrl | string 또는 null | 첫 첨부 사진 URL, 사진 없으면 null |
| images | array | 목록은 항상 [], 상세는 첨부 순서의 사진 |
| images[].url | string | 프론트에서 조회할 이미지 URL |
| images[].sortOrder | integer | 0부터 시작하는 첨부 순서 |

목록은 page·size를 받고 모집 중 우선 → startsAt 오름차순 → ID 오름차순으로 정렬한 뒤 페이지네이션합니다. 마감·종료 활동도 뒤에 표시합니다. 없는 동아리의 목록은 404이며 삭제된 활동은 목록에서 제외하고 상세에서 404입니다. 공개 응답에는 참가 명단·출결을 포함하지 않습니다.

### 로컬 활동 사진 조회

| 메서드 | 전체 경로 | 권한 | 응답 |
| --- | --- | --- | --- |
| GET | /api/v1/photos/activities/{filename} | 공개, local 모드만 | 200, JPEG·PNG·WebP 이미지 바이트 |

서버가 생성한 소문자 UUID 파일명과 jpeg·png·webp 확장자를 사용하며 프론트는 활동 응답 URL을 그대로 사용합니다. 컨트롤러의 filename 경로 패턴은 `[0-9a-f-]{36}\.(?:jpeg|png|webp)`이며 jpg 확장자·다른 형식의 경로는 매핑되지 않습니다. 성공 응답은 `Content-Type: image/jpeg|image/png|image/webp`, `Cache-Control: no-store`입니다. DB에 연결되지 않은 파일·삭제된 활동 사진·디스크에 없는 사진은 404입니다. JSON 응답이 아닙니다. 로컬 URL은 만료되지 않으며 S3 모드에는 이 컨트롤러를 등록하지 않습니다. 스키마 변경 없이 기존 storage_key를 사용합니다.

### 활동 사진 요청·응답 계약

- 사진 유무와 관계없이 `multipart/form-data`를 사용합니다. `activity` 파트의 Content-Type은 `application/json`입니다. `images` 파일 파트를 첨부 순서대로 반복 전달하며, 사진이 없으면 해당 파트를 생략합니다.
- 최대 5장, 파일당 5MB(5,242,880바이트), 전체 multipart 요청 27MB입니다. JPEG·PNG·WebP만 허용하며 실제 형식·디코딩 가능 여부를 검사합니다. 최대 픽셀 수는 25,000,000입니다. 빈 파일은 첨부 없음으로 취급하지 않고 400입니다.
- 첫 사진을 대표 이미지로 사용합니다. 사진이 없으면 representativeImageUrl=null, 상세 images=[]이며 프론트가 공통 기본 이미지를 표시합니다. 기본 이미지를 첨부 목록에 넣지 않습니다.
- 사진은 기본적으로 백엔드의 로컬 디스크에 저장합니다. `PHOTO_STORAGE_MODE=s3`일 때만 S3를 사용합니다. 프론트는 응답 URL을 img src로 사용하며 저장소 키로 URL을 조합하지 않습니다. URL·배포 설정은 [활동 사진 저장소](environment-variables.md#활동-사진-저장소)를 따릅니다.
- 로컬 모드는 `PHOTO_LOCAL_BASE_URL` 아래의 만료 없는 사진 조회 URL을 제공합니다. S3 모드는 이미지 기본 URL이 설정되면 해당 주소를 사용하고, 없으면 15분 만료 서명 GET URL을 제공합니다. 만료 시 활동을 재조회합니다. 사진 없는 등록은 저장소 설정 없이 가능합니다.
- 회장 권한·활동 입력 검증 후 사진을 검사하며 인증·CSRF도 적용합니다. 사진 하나라도 검증·업로드·DB 저장에 실패하면 활동 등록 전체를 실패 처리하고 해당 요청의 파일을 정리합니다. 정리 실패는 DB 저널로 재시도합니다.
- 형식·개수·손상·빈 파일·픽셀 제한 오류는 400 `INVALID_REQUEST`, 용량 초과는 413 `PAYLOAD_TOO_LARGE`, 저장소 미설정·장애는 503 `STORAGE_UNAVAILABLE`입니다.

### 활동 참가·취소·출결

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/clubs/{clubId}/activities/{activityId}/participants | 본인 참가·재참가 | 로그인 본인 | 없음 | 204, 본문 없음 |
| DELETE | /api/v1/clubs/{clubId}/activities/{activityId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 제외 | 없음 | 204, 본문 없음 |
| GET | /api/v1/clubs/{clubId}/activities/{activityId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 | 없음 | 200, ParticipationResponse |
| GET | /api/v1/clubs/{clubId}/activities/{activityId}/participants | 참가자·출결 명단 | 해당 활동 주최자 | 없음 | 200, Page&lt;ParticipantResponse&gt; |
| PATCH | /api/v1/clubs/{clubId}/activities/{activityId}/participants/{userId}/attendance | 참가자 출결 기록 | 해당 활동 주최자 | AttendanceRequest JSON | 204, 본문 없음 |

명단만 page·size를 받습니다. 요청·상태 전이·응답 필드·오류는 아래 [참가·출결 공통 계약](#활동소모임-구현-계약)을 따릅니다. 활동의 다른 회장·ADMIN·SUPER_ADMIN도 주최자가 아니면 명단·출결에 접근할 수 없습니다.

### 소모임·회차 작성·조회

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/gatherings | 소모임·첫 회차·주최자 참가 생성 | 로그인 | GatheringRequest JSON | 201, GatheringCreated |
| GET | /api/v1/gatherings | 대표 회차를 포함한 소모임 목록 | 공개 | 없음 | 200, Page&lt;GatheringResponse&gt; |
| GET | /api/v1/gatherings/{gatheringId} | 소모임 소개·기본 정보 | 공개 | 없음 | 200, GatheringResponse |
| POST | /api/v1/gatherings/{gatheringId}/sessions | 다음 회차·주최자 참가 생성 | 해당 소모임 주최자 | SessionRequest JSON | 201, GatheringCreated |
| GET | /api/v1/gatherings/{gatheringId}/sessions | 회차 목록 | 공개 | 없음 | 200, Page&lt;SessionResponse&gt; |
| GET | /api/v1/gatherings/{gatheringId}/sessions/{sessionId} | 회차 상세 | 공개 | 없음 | 200, SessionResponse |

**GatheringRequest**

| 필드 | JSON 타입 | 필수·검증 |
| --- | --- | --- |
| title | string | 필수, 공백만 불가, 최대 200자 |
| content | string | 필수, 공백만 불가, 최대 50,000자 |
| session | object | 필수, 아래 SessionRequest 전체 |

**SessionRequest — 첫 회차와 다음 회차에 공통 사용**

| 필드 | JSON 타입 | 필수·검증 |
| --- | --- | --- |
| title | string | 필수, 공백만 불가, 최대 200자; 소모임 제목과 독립적인 회차 이름 |
| startsAt | string | 필수, 오프셋 포함 ISO 8601, 생성 시각에서 최소 2시간 이후 |
| endsAt | string | 필수, 오프셋 포함 ISO 8601, startsAt 이후 |
| recruitmentClosesAt | string | 필수, 오프셋 포함 ISO 8601, 생성 시각 이후·startsAt 이전 |
| location | string | 필수, 공백만 불가, 최대 255자 |
| capacity | integer | 필수, 1~2,147,483,647, 주최자 포함 |

첫 회차는 session.title, 다음 회차는 최상위 title을 필수로 보냅니다. 기존 클라이언트도 제목을 추가해야 하며 생략 시 400입니다. 회차 제목은 회차 목록·상세와 소모임 목록의 representativeSession에 포함됩니다.

모집 공개는 회차 생성 즉시입니다. recruitmentOpensAt·organizerId는 서버가 결정하며 요청 필드가 아닙니다. 다음 회차 생성에는 session으로 감싸지 않은 SessionRequest JSON을 보냅니다. 이전 참가자·출결은 복사하지 않으며 각 회차에 주최자만 자동 참가합니다. 생성 중 실패하면 모임·회차·주최자 참가가 일부만 남지 않도록 롤백합니다.

**GatheringCreated**는 `{"gatheringId":30,"sessionId":40}`입니다. 소모임 생성의 Location은 `/api/v1/gatherings/30`, 다음 회차 생성의 Location은 `/api/v1/gatherings/30/sessions/40`입니다.

**GatheringResponse**

| 필드 | JSON 타입 | 목록 | 상세 |
| --- | --- | --- | --- |
| id | integer | 소모임 ID | 동일 |
| organizerId | integer | 주최자 사용자 ID | 동일 |
| title | string | 제목 | 동일 |
| content | string 또는 null | null | 본문 |
| representativeImageUrl | null | 프론트 공통 기본 이미지 사용 | 동일 |
| representativeSession | object 또는 null | 대표 회차의 SessionResponse | null; 회차 목록을 별도 조회 |

소모임 목록은 page·size를 받습니다. 대표 회차는 취소되지 않은 회차 중 진행 중(가장 이른 시작) → 가장 가까운 예정 → 가장 최근 지난 회차 순입니다. 목록은 대표 회차의 모집 중 우선 → startsAt 오름차순 → 소모임 ID 오름차순입니다. 대표할 미취소 회차가 없는 모임은 목록에서 제외합니다. 삭제된 모임은 목록에서 제외하고 상세·하위 회차·참가·출결 접근에서 404입니다.

**회차 목록 쿼리**

| 매개변수 | 기본값 | 의미·정렬 |
| --- | --- | --- |
| page / size | 0 / 20 | 공통 페이지 범위 적용 |
| filter | ALL | ALL / UPCOMING / ONGOING / PAST만 허용; 빈 값은 기본값 |
| filter=ALL | — | 전체 회차, startsAt → ID 오름차순 |
| filter=UPCOMING | — | 현재 시각 < startsAt, startsAt → ID 오름차순 |
| filter=ONGOING | — | startsAt <= 현재 시각 < endsAt, startsAt → ID 오름차순 |
| filter=PAST | — | endsAt <= 현재 시각, startsAt → ID 내림차순 |

회차 목록·상세는 취소된 회차도 cancelled=true로 반환합니다. 취소 API는 현재 제공하지 않습니다. 없는 소모임의 회차 목록은 404입니다.

**SessionResponse**

| 필드 | JSON 타입 | 의미 |
| --- | --- | --- |
| sessionId | integer | 회차 ID; 참가·출결 요청에 사용 |
| gatheringId | integer | 소속 소모임 ID |
| title | string | 회차 제목 |
| startsAt / endsAt | string | UTC ISO 8601 개최·종료 시각 |
| recruitmentOpensAt | string | UTC ISO 8601 공개 시각, 회차 생성 시각 |
| recruitmentClosesAt | string | UTC ISO 8601 모집 마감 시각 |
| location | string | 장소 |
| capacity | integer | 주최자 포함 전체 정원 |
| currentParticipantCount / remainingCapacity | integer | CONFIRMED 참가 수 / 잔여 정원 |
| recruiting | boolean | 미취소이고 공개 시각 이상·마감 및 개최 전이며 잔여 정원이 있으면 true |
| dDay | string | 한국 날짜 기준 D-N / D-Day / 종료 |
| cancelled | boolean | 회차 취소 여부 |

### 회차 참가·취소·출결

| 메서드 | 전체 경로 | 기능 | 권한 | 요청 본문 | 성공·응답 |
| --- | --- | --- | --- | --- | --- |
| POST | /api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants | 본인 참가·재참가 | 로그인 본인 | 없음 | 204, 본문 없음 |
| DELETE | /api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 제외 | 없음 | 204, 본문 없음 |
| GET | /api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 | 없음 | 200, ParticipationResponse |
| GET | /api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants | 참가자·출결 명단 | 해당 소모임 주최자 | 없음 | 200, Page&lt;ParticipantResponse&gt; |
| PATCH | /api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants/{userId}/attendance | 참가자 출결 기록 | 해당 소모임 주최자 | AttendanceRequest JSON | 204, 본문 없음 |

명단만 page·size를 받습니다. 아래 공통 계약을 회차별로 적용하며 같은 사용자도 여러 회차에 독립적으로 참가합니다. 한 회차의 취소·출결은 다른 회차에 영향을 주지 않습니다. sessionId를 사용하는 경로에 gatheringId를 대신 보내지 않습니다.

## DTO와 프론트 연동 계약

위 표의 `Page<T>`는 실제 JSON에서 아래 구조이며 content 항목이 해당 DTO입니다. 숫자 리소스 ID는 1 이상의 Java long 범위 정수입니다. 로컬 사진의 filename은 별도 파일명 규칙을 따릅니다. 잘못된 숫자·범위·필터·본문은 400입니다.

| 공통 필드 | JSON 타입 | 의미 |
| --- | --- | --- |
| content | array | 요청 페이지 항목, 없으면 [] |
| page | integer | 요청 페이지, 기본 0, 허용 0~2,147,483,647 |
| size | integer | 페이지 크기, 기본 20, 허용 1~100 |
| totalElements | integer | 필터 적용 후 전체 항목 수 |
| totalPages | integer | 전체 페이지 수 |

빈 page·size는 기본값으로 처리하며 페이지를 벗어나도 200·빈 content입니다. 크기 초과를 자동 축소하지 않습니다. 사용자 검색 API의 intraLogin·role 외에 사용자 지정 sort·검색 기능은 제공하지 않습니다. 각 컨트롤러에 선언한 필터만 동작하며 선언하지 않은 쿼리 매개변수로 정렬·검색·본인 ID를 변경할 수 없습니다. 요청 시각은 `2030-05-02T09:00:00+09:00`처럼 오프셋을 포함하고, 시각 응답은 UTC ISO 8601입니다. dDay는 Asia/Seoul 날짜 차이이며 개최 당일은 D-Day, 개최일이 지난 뒤에는 종료입니다. 당일 개최 시각 경과만으로 종료로 바꾸지 않습니다.

- 인증은 서버 세션이며 변경 요청에는 CSRF 토큰을 보냅니다. 204에는 JSON 본문이 없으므로 프론트에서 response.json()을 호출하지 않습니다.
- ADMIN 운영 기능은 SUPER_ADMIN도 사용합니다. 비공개 공지의 관리 조회에는 운영 권한을 인정하며, 부원 명단·활동 작성·참가 명단·출결의 관계 조건은 유지합니다.
- 사진 URL은 별도 저장소의 이미지 조회 주소입니다. 프론트의 기본 이미지·만료 URL 갱신을 [연결 예시](mvp-api-examples.md)와 함께 구현합니다.

## 활동·소모임 구현 계약

### 본인 상태·참가자·출결 DTO

**ParticipationResponse**

| 필드 | JSON 타입 | 값 |
| --- | --- | --- |
| status | string 또는 null | CONFIRMED / CANCELLED; 미참가는 null |
| attendanceStatus | string 또는 null | UNCHECKED / PRESENT; 미참가는 null |

참가 관계가 없는 사용자의 본인 조회도 200과 `{"status":null,"attendanceStatus":null}`입니다. 활동·회차 자체가 없거나 삭제된 경우는 404입니다.

**ParticipantResponse — 주최자 명단 content 항목**

| 필드 | JSON 타입 | 값 |
| --- | --- | --- |
| userId | integer | 참가자 사용자 ID |
| intraLogin | string | Intra 로그인명 |
| displayName | string | 표시 이름 |
| status | string | CONFIRMED / CANCELLED |
| attendanceStatus | string | UNCHECKED / PRESENT |
| attendanceCheckedBy | integer 또는 null | 마지막 체크 주최자 ID; UNCHECKED이면 null |
| attendanceCheckedAt | string 또는 null | UTC ISO 8601 마지막 체크 시각; UNCHECKED이면 null |

명단은 사용자 ID 오름차순이며 주최자와 CANCELLED 관계도 포함합니다. 공개 목록·상세나 부원 명단에 이 DTO를 노출하지 않습니다.

**AttendanceRequest**는 필수 문자열 attendanceStatus 하나를 받습니다.

```json
{"attendanceStatus":"PRESENT"}
```

UNCHECKED로 되돌리면 체크 사용자·시각을 비웁니다. PRESENT 기록자는 인증된 주최자로 서버가 결정합니다. CONFIRMED 참가자만 변경할 수 있고 주최자 자신의 출결도 기록할 수 있습니다. 출결 입력의 개최 전·후 시각 제한은 없습니다. 취소된 회차에는 기록할 수 없습니다.

### 참가·취소 상태 전이

- 참가·재참가·본인 취소는 모두 마감·개최 전만 가능합니다. 동아리 활동 참가에는 ClubMember 부원 관계를 요구하지 않습니다. 회차는 공개 시각 이상·미취소 조건도 검사합니다. 정확히 마감 시각부터 참가·취소 모두 409입니다.
- 참가자는 요청 본문이 아닌 세션 사용자입니다. 처음 참가하면 CONFIRMED·UNCHECKED, 취소하면 CANCELLED, 재참가하면 기존 행을 CONFIRMED로 갱신합니다.
- 이미 CONFIRMED인 중복 참가와 이미 CANCELLED인 취소는 409입니다. 참가하지 않은 사용자의 취소는 404입니다. 주최자는 취소할 수 없습니다.
- PRESENT 관계의 취소·재참가는 거절하며 출결을 임의로 초기화하지 않습니다. 주최자가 UNCHECKED로 되돌린 뒤 정상 시각·정원 조건에서 처리합니다.
- 활동·회차별 잠금으로 동시 요청에도 정원과 사용자별 관계 하나를 보장합니다. 정원이 찼으면 409이며 대기열·승인제·타인 수동 등록은 없습니다.

### 오류 코드

아래 표의 애플리케이션·보안 처리기가 반환하는 오류 본문은 `{code, message}`입니다. 프론트는 HTTP 상태와 code를 기준으로 처리하며 message는 사용자 안내입니다. 입력 바인딩·형식 오류는 공통 메시지, 업무 검증 오류는 검증 사유를 반환할 수 있으므로 message 문자열을 분기 기준으로 사용하지 않습니다.

지원하지 않는 HTTP 메서드(405)·Content-Type(415)·응답 미디어 타입(406), 미등록 경로 등은 Spring 기본 오류 처리 대상이며 이 공통 JSON 형식을 보장하지 않습니다. 예를 들어 활동 생성에 JSON 본문만 보내거나 activity 파트를 지원하지 않는 Content-Type으로 보내면 400 대신 415가 될 수 있습니다. 프록시에서 차단한 용량 초과 응답도 애플리케이션의 413 본문과 다를 수 있습니다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | ID·페이지·필터·필수 필드·문자열 길이·일정·정원·출결 값·사진 검증 오류 |
| 401 | UNAUTHENTICATED | 보호 API의 세션 인증 없음 |
| 403 | ACCESS_DENIED | 역할·회장·부원·주최자 권한 부족 또는 CSRF 누락·불일치 |
| 404 | RESOURCE_NOT_FOUND | 없는 리소스·삭제된 글·동아리와 글의 소속 불일치·접근 불가 비공개 공지·삭제된 모임 하위 회차 |
| 409 | CLUB_INACTIVE | 아카이브 동아리의 부원 등록·재등록 |
| 409 | RECRUITMENT_CLOSED | 참가·취소의 미공개·마감·개최 시각 도달, 취소 회차의 참가·취소·출결 |
| 409 | CAPACITY_FULL | 참가·재참가 시 잔여 정원 없음 |
| 409 | ALREADY_JOINED | 이미 CONFIRMED인 사용자의 참가 |
| 409 | ALREADY_CANCELLED | 이미 CANCELLED인 사용자의 취소 |
| 409 | ORGANIZER_CANNOT_CANCEL | 주최자의 본인 참가 취소 |
| 409 | ATTENDANCE_RECORDED | 출결이 기록된 관계의 취소·재참가 |
| 409 | PARTICIPANT_NOT_CONFIRMED | CANCELLED 참가자에 대한 출결 기록 |
| 413 | PAYLOAD_TOO_LARGE | 사진 파일 또는 전체 multipart 요청 용량 초과 |
| 503 | STORAGE_UNAVAILABLE | 사진 업로드·로컬 파일 조회 장애·S3 설정 누락·이미지 URL 생성 실패 등; 사진이 있는 활동 목록·상세 조회에서도 발생 가능 |

인증 없이 CSRF도 빠진 변경 요청은 인증보다 CSRF 검사에 의해 403일 수 있습니다. 여러 충돌 조건을 동시에 만족하면 먼저 검사한 조건의 오류가 반환됩니다. 예를 들어 마감 후 중복 참가·취소는 모집 마감 오류가 우선합니다. 기존 권한 관리의 `SUPER_ADMIN_ROLE_PROTECTED`는 해당 권한 관리 계약을 따릅니다.

## 공지·활동 경로 전환

- 동아리 공지 상세는 `/api/v1/clubs/{clubId}/notices/{noticeId}`, 활동 상세와 참가·취소·명단·출결은 `/api/v1/clubs/{clubId}/activities/{activityId}` 아래로 통일했습니다.
- 기존 `/api/v1/notices/{noticeId}`, `/api/v1/activities/{activityId}`와 기존 활동 참가 하위 경로는 제거했습니다. 리다이렉트·호환 별칭이 없으므로 프론트 링크·API 호출을 함께 변경해야 합니다. 미인증 요청은 공통 보안 규칙에 따라 401/403이 먼저 반환될 수 있습니다.
- 모든 중첩 활동 요청은 조회 쿼리에서 clubId·activityId·미삭제 조건을 함께 검사합니다. 잘못된 동아리 조합은 404이며 다른 활동의 참가·출결을 변경하지 않습니다. 활동 생성 Location도 중첩 경로입니다.
- 공지의 기본 비공개 정책은 활동에 적용하지 않습니다. 활동의 공개 조회·본인 참가·주최자 출결 정책은 유지합니다.

## 소모임 회차 경로 전환

- 회차 상세·참가·취소·본인 상태·명단·출결은 `/api/v1/gatherings/{gatheringId}/sessions/{sessionId}` 아래로 통일했습니다. 회차 생성 Location도 이 경로를 반환합니다.
- 경로의 gatheringId와 실제 회차의 소모임이 다르거나 소모임이 삭제되었으면 404입니다. 기존 회차별 참가·권한 정책은 유지합니다.
- 기존 `/api/v1/gathering-sessions/{sessionId}` 및 하위 경로는 제거했습니다. 리다이렉트·호환 별칭이 없으므로 프론트 호출과 링크를 함께 변경해야 합니다. 미인증 요청은 공통 보안 규칙에 따라 401/403이 먼저 반환될 수 있습니다.

## 마이페이지·내 권한 — 구현 계약

모두 로그인 필수이며 인증 정보의 사용자 ID만 사용합니다. userId 요청 매개변수로 타인의 데이터를 조회하지 않습니다. 성공은 200이며 기존 공개 조회·등록·참가 계약은 유지합니다.

| 메서드 | 전체 경로 | 응답 |
| --- | --- | --- |
| GET | /api/v1/me/clubs | Page&lt;MyClubResponse&gt;, 가입·관리 동아리 통합 |
| GET | /api/v1/clubs/{clubId}/permissions/me | ClubPermissions, 해당 동아리에서 가능한 행동 |
| GET | /api/v1/me/participations | Page&lt;MyParticipationGroup&gt;, 동아리 활동·소모임 회차 통합 |
| GET | /api/v1/me/gatherings | Page&lt;MyParticipationGroup&gt;, 소모임별 본인 참가 회차 |

세 목록은 공통 page=0, size=20(1~100)을 사용합니다. 내 동아리는 동아리 ID 오름차순입니다. 가입·관리 양쪽에 해당해도 한 행이며, 플랫폼 운영자라는 이유로 모든 동아리를 포함하지 않습니다. 남아 있는 아카이브 관계도 포함합니다.

**MyClubResponse**: `{clubId, name, operationStatus, isMember, isManager, permissions}`. isMember는 ACTIVE 부원 관계, isManager는 현재 회장 관계입니다. 두 값은 독립적입니다.

**ClubPermissions**는 아래 boolean 필드를 제공합니다. 내 동아리의 permissions와 단독 권한 응답은 같은 정책을 사용합니다.

| 필드 | 조건 |
| --- | --- |
| isMember | 해당 동아리 ACTIVE 부원 관계 |
| isManager | 해당 동아리 회장 관계 |
| canCreateActivity | ACTIVE 동아리의 회장 |
| canCreateNotice | ACTIVE 동아리의 회장 또는 ADMIN·SUPER_ADMIN |
| canViewMembers | 해당 동아리 ACTIVE 부원 관계 |
| canReadPrivateNotices | 해당 동아리 ACTIVE 부원·회장 또는 ADMIN·SUPER_ADMIN |

내 동아리 응답의 최상위 isMember·isManager와 permissions 안의 같은 필드는 동일한 값입니다.

단독 권한 조회는 관계가 없는 로그인 사용자도 가능하며 부족한 권한을 false로 반환합니다. 없는 동아리는 404입니다. 권한은 조회 시점의 값이므로 실제 변경 요청에서도 재검사하며, 주최자별 명단·출결 권한을 부여하는 필드는 아닙니다.

### 참가 그룹 필터와 정렬

- `/me/participations?period=UPCOMING|PAST|ALL`: 기본 UPCOMING. UPCOMING은 startsAt > 현재 시각, PAST는 startsAt <= 현재 시각, ALL은 둘 다 포함합니다. 빈 값은 기본값입니다.
- `/me/gatherings?scope=ACTIVE|ALL`: 기본 ACTIVE. ACTIVE는 본인의 미취소 CONFIRMED 회차 중 endsAt > 현재 시각인 회차가 있는 모임만 선택합니다. 선택된 모임에는 본인이 참가한 지난 회차도 포함합니다. ALL은 지난 회차만 있는 모임도 포함합니다. 빈 값은 기본값입니다.
- CONFIRMED 관계만 포함하며 주최자의 자동 참가도 포함합니다. 본인 취소, 삭제된 활동·소모임, 취소된 회차는 제외합니다. 다른 사용자의 참가·출결은 반환하지 않습니다. 불참·승인 대기 상태는 추가하지 않습니다.
- 그룹 안 items는 시작 시각 → 활동/회차 ID 오름차순입니다. period=PAST일 때는 둘 다 내림차순입니다. 그룹은 정렬된 첫 항목의 시작 시각으로 정렬하며 동률은 type → groupId 오름차순입니다. PAST의 그룹 시작 시각은 내림차순이고 나머지는 오름차순입니다.
- 페이지네이션은 **그룹 단위**입니다. totalElements는 그룹 수이며 각 그룹의 items 전체를 반환합니다. 소모임 회차가 페이지 사이에서 잘리지 않습니다.

**MyParticipationGroup**

| 필드 | 타입·의미 |
| --- | --- |
| type | CLUB / GATHERING |
| groupId | 동아리 ID 또는 소모임 ID; type과 함께 식별 |
| name | 동아리 또는 소모임 이름 |
| items | 본인의 참가 항목 배열 |
| items[].activityId | 동아리 활동 ID, 소모임 회차는 null |
| items[].sessionId | 소모임 회차 ID, 동아리 활동은 null |
| items[].title | 활동 제목 또는 독립적인 회차 제목 |
| items[].startsAt | UTC ISO 8601 시작 시각 |
| items[].endsAt | 소모임 회차 종료 시각; 동아리 활동은 null |
| items[].status | CONFIRMED |
| items[].attendanceStatus | UNCHECKED / PRESENT |
| items[].isOrganizer | 본인이 해당 활동·소모임 주최자인지 |

상세 이동은 CLUB이면 `/clubs/{groupId}/activities/{activityId}`, GATHERING이면 `/gatherings/{groupId}/sessions/{sessionId}`를 사용하며 API 호출에는 `/api/v1`을 붙입니다. 일회성과 정기모임은 같은 그룹 구조입니다. 소모임 자체의 가입 상태는 없으며 items의 status는 회차 참가 상태입니다. 두 그룹 API 모두 응답 필드명은 items이며 sessions가 아닙니다.

잘못된 ID·필터·page·size는 400 INVALID_REQUEST, 미인증은 401 UNAUTHENTICATED입니다. 빈 목록이나 범위 밖 페이지는 200·빈 content입니다. 캘린더 전용 엔드포인트·월별 필터는 이번 범위에 포함하지 않습니다.

## 구현 대조 기준 — 2026-10-17

- 전체 Controller의 클래스·메서드 매핑과 [SecurityConfig](../src/main/java/com/clubplatform/auth/config/SecurityConfig.java)의 OAuth·로그아웃 경로를 합쳐 46개 HTTP 메서드·경로 조합을 대조했습니다. 명세 표의 누락·미구현 경로는 없습니다. 조건부 등록은 문서 상단의 환경 조건을 따릅니다.
- 요청·응답 record의 외부 필드, Bean Validation, 생성 Location·201·204 응답, 서비스 권한·참가 상태 전이, Repository의 공개 범위·정렬, 마이페이지 그룹 필터를 대조했습니다. 내부 협력 DTO인 UserIdentity·MyActivity·MySession은 독립된 외부 API 응답이 아닙니다.
- 업무·입력 오류는 [GlobalExceptionHandler](../src/main/java/com/clubplatform/global/exception/GlobalExceptionHandler.java), 사진 저장소 오류는 [PhotoExceptionHandler](../src/main/java/com/clubplatform/activity/controller/PhotoExceptionHandler.java), 미인증·CSRF 오류는 SecurityConfig를 기준으로 합니다. 405·415 등 기본 프레임워크 오류는 위 공통 오류 본문의 보장 범위에서 제외합니다.
- 이번 대조는 저장소 코드 기준입니다. 실제 42 제공자·프론트엔드·배포 프록시의 통합 동작은 별도 연동 확인 대상입니다. 캘린더 API는 구현·명세 범위에 포함하지 않습니다.
