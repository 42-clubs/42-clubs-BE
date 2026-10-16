# API 명세

인증·동아리 공개 조회·사용자 검색·권한 관리는 구현 계약입니다. 부원·공지 API도 구현했으며 활동·소모임 API는 아직 미구현입니다. 요청·응답의 최소 구성은 아래에 정리하고 세부 DTO는 구현 시 확정합니다.
[현재 MVP 정책](domain-model.md#축소-mvp--2026-10-14-확정)과 [후속 API](future-design.md#후속-api-초안)를 구분해 사용합니다.

- [공통 원칙](#공통-원칙)
- [먼저 구현할 순서](#먼저-구현할-순서)
- [인증 — 구현 완료](#인증--구현-계약)
- [동아리 조회 — 구현 완료](#동아리-공개-조회--구현-계약)
- [사용자 검색·권한 관리 — 구현 완료](#사용자-검색권한-관리--구현-계약)
- [최소 MVP 엔드포인트](#mvp-api--미구현)

## 먼저 구현할 순서

기능별 DB 작업·의존성·검증 조건은 [MVP 구현 계획](mvp-implementation-plan.md)에서 관리합니다. 계획에서는 권한 관리 화면의 선행 의존성인 사용자 검색을 부원 관리보다 먼저 구현합니다.

현재 구현된 인증 5개(OAuth 시작·콜백 포함)와 동아리 조회 2개를 활용합니다. 업무 엔드포인트는 총 **31개**로 사용자 검색·권한 관리·부원·공지 12개를 구현했고 나머지 **19개**는 미구현입니다. 활동 사진은 작성 요청에 포함하므로 별도 업로드 API를 추가하지 않습니다. 아래 순서는 개발 권장 순서이며 각 단계의 경로·권한은 뒤의 표가 기준입니다.

| 순서 | 구현 묶음 | API 수 | 상태 |
| --- | --- | --- | --- |
| 0 | 역할·권한 기반·초기 DB 입력 절차 | 0 | 완료 |
| 1 | 사용자 검색·최고 관리자 권한 관리 | 6 | 완료 |
| 2 | 부원 등록·현황·명단 | 3 | 완료 |
| 3 | 공지 작성·조회 | 3 | 완료 |
| 4 | 동아리 활동 작성·조회 | 3 | 미구현 |
| 5 | 활동 참가·취소·기본 출결 | 5 | 미구현 |
| 6 | 소모임·회차 작성·조회 | 6 | 미구현 |
| 7 | 회차 참가·취소·기본 출결 | 5 | 미구현 |

초기 동아리·회장·최고 관리자는 전달받은 정보로 [초기 DB 입력 절차](initial-data-setup.md)에 따라 입력합니다. 이후 권한 관리는 아래 최고 관리자 API를 사용합니다. 동아리 생성 API는 MVP에 추가하지 않습니다. 0단계에서 SUPER_ADMIN 역할과 ClubManager 관계·작성 권한 검사를 구현했습니다. ClubMember는 2단계에서 구현합니다.

동아리 활동은 해당 회장이 작성하며 작성자가 주최자입니다. 주최자 선택 UI·후보 검색 API·organizerId 요청 필드는 필요하지 않습니다.

글 수정·삭제, 일정 변경, 탈퇴, 가입 신청·승인, 자동 반복 회차, 블랙홀 처리, 출결 통계·지각·결석은 이 구현 목록에 포함하지 않습니다. 기록 보존 기간은 팀 합의 전까지 보류합니다.

## 공통 원칙

- 업무 API 기본 경로는 `/api/v1`입니다. 구현 계약은 전체 경로를, 미구현 API 표는 접두사를 생략한 경로를 사용합니다.
- 본인 식별자는 인증 정보로 결정합니다. 신청 요청에서 임의 userId를 받아 다른 사람으로 신청할 수 없습니다.
- 변경 요청은 세션 쿠키와 CSRF 헤더가 필요합니다. CSRF 토큰 누락·불일치는 403입니다.
- 운영진은 ADMIN, 동아리 관리자는 해당 ClubManager 관계, 활동·소모임 주최자는 해당 organizer_id로 검사합니다.
- 중첩 경로는 자식의 소속 관계도 검사합니다. 경로의 clubId/gatheringId만 검증하고 다른 부모의 자식을 수정할 수 없습니다.
- 참가자 명단과 출결은 공개 상세 응답에 포함하지 않습니다. 주최자는 관리 명단, 참가자는 본인 상태를 조회합니다.
- 목록은 페이지네이션을 적용하며 상세 매개변수는 후속 합의합니다.
- 생성은 201, 일반 조회·수정은 200 또는 본문 없는 204를 사용합니다. 구체적인 응답은 DTO 단계에서 고정합니다.
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
활동도는 활동 도메인과 [확정된 집계 정책](future-design.md#활동도-계산) 구현 후 추가하며 현재 응답에 포함하지 않습니다.
생성·수정 API와 개발용 초기 데이터는 아직 없으므로 새 DB의 목록은 비어 있습니다.

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
관리자·사용자 정보는 공개 응답에 포함하지 않습니다.

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

위 오류는 동아리 조회 계약입니다. 인증 오류는 위 인증 계약을 따르며 새 쓰기 API의 충돌 오류는 구현 시 확정합니다.

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
- 동아리 운영 상태와 관계없이 회장 관계를 부여·회수할 수 있습니다. 활동·공지 작성에는 별도로 ACTIVE 동아리의 현재 회장 관계가 필요합니다. 다른 동아리 관계는 변경하지 않습니다.
- 역할 회수는 기존 세션의 다음 요청부터, 회장 회수는 다음 권한 검사부터 반영됩니다. 회장 지정으로 부원 관계를 생성하지 않으며 권한 변경으로 계정·부원·게시물·참가 기록을 삭제하지 않습니다.

## MVP API — 미구현

아래 경로는 `/api/v1` 접두사를 생략했습니다. 글 생성·조회와 본인 선착순 참가·취소를 우선하며, 글 수정·일정 변경과 나머지 운영 API는 [후속 API](future-design.md#후속-api-초안)에서 별도 관리합니다.

### 동아리 활동·공지

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /clubs/{clubId}/activities | 활동 목록 | 공개 |
| GET | /activities/{activityId} | 활동 상세 | 공개 |
| POST | /clubs/{clubId}/activities | 활동 작성·작성자 자동 주최 | ACTIVE인 해당 동아리 ClubManager |
| POST | /activities/{activityId}/participants | 본인 선착순 참가·재참가 | 로그인 본인 |
| GET | /activities/{activityId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 |
| DELETE | /activities/{activityId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 예외 유지 |
| GET | /clubs/{clubId}/notices | 공지 목록 | 공개 |
| GET | /notices/{noticeId} | 공지 상세 | 공개 |
| POST | /clubs/{clubId}/notices | 공지 작성 | ACTIVE인 해당 동아리 ClubManager |

USER/ADMIN/SUPER_ADMIN 모두 ClubManager 관계로 작성 권한을 검사합니다. ADMIN/SUPER_ADMIN 역할만으로 공지를 작성할 수 없습니다.
활동 사진은 아래 [사진 요청·응답 계약](#활동-사진-요청응답-계약)을 따릅니다. 주최자의 타 사용자 수동 참가 등록은 핵심 MVP에서 사용하지 않습니다.

### 소모임

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /gatherings | 소모임 목록 | 공개 |
| GET | /gatherings/{gatheringId} | 소모임 소개·기본 정보 | 공개 |
| POST | /gatherings | 모집 글·첫 회차·주최자 참가 함께 생성, FIRST_COME 고정 | 로그인 |
| GET | /gatherings/{gatheringId}/sessions | 예정·지난 회차 목록 | 공개 |
| POST | /gatherings/{gatheringId}/sessions | 같은 소모임에 다음 회차 추가·주최자 참가 생성 | 해당 소모임 주최자 |
| GET | /gathering-sessions/{sessionId} | 회차 상세·모집 상태·잔여 정원 | 공개 |
| POST | /gathering-sessions/{sessionId}/participants | 본인 선착순 참가·재참가 | 로그인 본인 |
| GET | /gathering-sessions/{sessionId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 |
| DELETE | /gathering-sessions/{sessionId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 제외 |

POST /gatherings는 첫 회차의 개최·종료·모집 마감 일시·장소·정원을 함께 받아 글·회차·주최자 참가를 원자적으로 생성합니다. 실패하면 모두 롤백합니다.
POST /gatherings/{gatheringId}/sessions는 새 회차의 일시·장소·정원·모집 마감을 받아 회차와 주최자 참가를 함께 생성합니다. 저장된 organizer_id로 권한을 검사하고 이전 회차의 참가자를 복사하지 않습니다.
소모임 상세 화면은 기본 정보와 회차 목록을 조합하며 참가·취소·출결은 sessionId 단위입니다. 회차 목록의 예정·지난 구분과 페이지네이션 DTO는 구현 시 확정합니다. 자동 반복·고정 멤버·참가 승인 API는 후속입니다.

### 동아리 부원 관리

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /admin/users/{userId}/clubs | 현재 가입 동아리·한도 집계 조회 | ADMIN |
| PUT | /admin/clubs/{clubId}/members/{userId} | 기존 부원 직접 등록·재등록 | ADMIN, 대상 동아리 ACTIVE |
| GET | /clubs/{clubId}/members | 현재 ACTIVE 부원 명단 | 해당 동아리 ACTIVE 부원 |

부원 등록 대상 검색은 위 구현된 `/api/v1/admin/users`를 재사용합니다. 별도 본인 가입 신청·승인 대기 API는 만들지 않습니다. 명단 접근은 [회원 정책](domain-model.md#동아리-회원-명단-조회--확정)을 유지합니다. ADMIN·ClubManager만으로 전체 부원 명단을 열람할 수는 없습니다. ADMIN의 사용자 검색 결과는 내부 ID·Intra 로그인명·표시 이름으로 제한합니다. SUPER_ADMIN의 권한 관리용 응답에는 현재 역할을 추가합니다.

### 기본 출결

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /activities/{activityId}/participants | 참가자·출결 명단 | 해당 활동 주최자 |
| PATCH | /activities/{activityId}/participants/{userId}/attendance | UNCHECKED / PRESENT 기록 | 해당 활동 주최자 |
| GET | /gathering-sessions/{sessionId}/participants | 회차 참가자·출결 명단 | 해당 소모임 주최자 |
| PATCH | /gathering-sessions/{sessionId}/participants/{userId}/attendance | UNCHECKED / PRESENT 기록 | 해당 소모임 주최자 |

체크 사용자·시각은 서버에서 기록합니다. 미확인으로 되돌리면 비우며 본인 출결은 각 participants/me 응답에 포함합니다. 결석·지각 값은 허용하지 않습니다. 글 수정 제외 정책은 출결 기록 API에는 적용하지 않습니다.

### 일정·참가·목록 계약

- 활동과 소모임의 모든 회차는 startsAt·recruitmentClosesAt에 오프셋을 포함한 ISO 8601 날짜·시각을 필수로 받습니다. 표시·D-Day의 날짜 기준은 Asia/Seoul이며 모집 마감은 개최보다 이전이어야 합니다.
- 참가·재참가·취소는 [시각 조건](domain-model.md#일정과-표시)을 검사합니다. 정확히 마감 시각이거나 마감 이후인 참가·취소는 409로 처리합니다.
- 활동·소모임 목록은 모집 중 우선 → 개최 시각 오름차순 → ID 오름차순으로 정렬합니다. 마감·종료 항목도 뒤쪽에 포함하며 검색 API는 후속입니다. 정렬은 페이지네이션 전에 적용합니다.
- 글·일정 수정 API는 MVP에 제공하지 않습니다. 기존 동아리 조회와 그 정렬·응답 계약은 유지합니다.

### 활동 사진 요청·응답 계약

아래는 확정된 설계 계약이며 활동 API는 아직 미구현입니다. 개수·용량·형식·실패 처리의 기준은 [활동 사진 정책](domain-model.md#활동-사진--확정)입니다.

- `POST /api/v1/clubs/{clubId}/activities`는 사진 유무와 관계없이 `multipart/form-data`로 받습니다. `activity` 파트는 `application/json` 활동 정보이고, 선택적인 `images` 파트에 첨부 순서대로 파일을 반복 전달합니다. 사진이 없으면 `images` 파트를 생략합니다. 빈 파일 파트는 사진 없음으로 취급하지 않고 검증 오류로 처리합니다.
- 활동 목록에는 `representativeImageUrl`을 제공합니다. 사진이 없으면 `null`이며 프론트엔드가 공통 기본 이미지를 표시합니다.
- 상세에는 같은 대표 이미지 필드와 첨부 순서의 `images` 배열을 제공합니다. 배열 항목은 `url`·`sortOrder`를 가지며, 사진이 없으면 빈 배열입니다. 기본 이미지 URL을 실제 첨부 목록에 넣지 않습니다.
- `representativeImageUrl`과 `images[].url`은 별도 파일 저장소의 사진을 프론트엔드에서 조회·표시할 수 있는 이미지 URL입니다. 백엔드는 DB의 `storage_key`를 바탕으로 URL을 구성하여 응답하며, 프론트엔드에 저장소 키로 URL을 조합하도록 요구하지 않습니다. 구체적인 저장소 제품·연결 설정·URL 구성은 구현 시 확정합니다.
- 파일 검증은 해당 동아리 회장 권한과 활동 입력 검증 후 수행합니다. 기존 세션 인증·CSRF 검사를 적용하며 파일 업로드를 이유로 예외 처리하지 않습니다.
- 허용 개수 초과·빈 파일·지원하지 않거나 손상된 이미지는 400, 파일 또는 요청 용량 제한 초과는 413으로 처리합니다. 전체 요청 용량과 multipart 오버헤드 한도, 저장소 장애의 세부 오류 코드는 구현 시 확정합니다.

## 구현 전 확정할 계약

### 요청·응답 최소 구성

아래는 프론트 연동용 최소 구성안입니다. 새 API의 정확한 필드명·필수 여부·응답 상태는 구현 전에 DTO로 고정하며, 기존 구현 계약에는 적용하지 않습니다.

| 기능 | 요청에 필요한 정보 | 응답에 필요한 정보 |
| --- | --- | --- |
| 공지 작성·상세 | 작성: 제목·본문, clubId는 경로에서 결정 | 공지 ID·동아리 ID·제목·본문·작성 시각 |
| 활동 작성·상세 | 작성: 제목·본문·개최 일시·모집 마감 일시·정원 + 선택 사진, multipart 구성은 위 계약 참조 | 활동 ID·내용·대표 이미지·사진·개최/마감 일시·정원·현재 참가 수·모집 상태 |
| 소모임 생성 | 제목·본문 + 첫 회차의 개최/종료/마감 일시·장소·정원, 주최자는 인증 정보에서 결정 | gatheringId·첫 sessionId; 상세에서는 소개·주최자 표시 정보 |
| 다음 회차 생성·회차 상세 | 생성: 개최/종료/마감 일시·장소·정원 | sessionId·gatheringId·일정·장소·정원·현재 참가 수·모집 상태 |
| 본인 참가·취소 | 경로의 활동/회차 ID, 사용자 ID는 요청받지 않음 | 처리 결과; 본인 상태 조회에서 참가 상태·출결 확인 |
| 주최자 참가자 조회 | 경로의 활동/회차 ID, 페이지 정보 | 참가자 식별자·표시 이름·참가 상태·출결·최종 체크 정보 |
| 출결 기록 | `{"attendanceStatus":"PRESENT"}` 또는 `{"attendanceStatus":"UNCHECKED"}` | 변경된 출결 또는 본문 없는 성공 응답; 체크 사용자·시각은 서버 결정 |
| 부원 등록·현황·명단 | 등록: 경로의 clubId·userId, 검색: intraLogin | 등록 결과; 현황: 현재 가입 동아리·개수; 명단: ACTIVE 부원 식별자·표시 이름 |

참가 생성은 선착순 정원 검사와 관계 저장을 하나의 트랜잭션으로 처리합니다. 중복 요청·동시 신청에도 같은 사용자의 중복 참가와 정원 초과를 막아야 합니다. 부원 등록은 PUT의 반복 요청으로 중복 관계를 만들지 않습니다.

### 프론트 연결 시 확인할 항목

- 활동·소모임 목록/상세: D-Day·잔여 정원 등 나머지 DTO 이름과 상세 응답 구성
- 활동 사진: 위 multipart·대표 이미지 계약을 기준으로 별도 파일 저장소의 구체적인 제품·이미지 URL 구성·전체 요청 용량 한도·저장소 장애 오류 코드와 프론트엔드 기본 이미지 자산 확정
- 소모임 기본 이미지 제공 방식, 모집 시작을 즉시 처리할지 예약 입력을 받을지
- 목록 필터·페이지네이션, 소모임 대표 회차 선택·진행 중 회차 표시, 회차 생성 응답과 참가·취소·부원 등록 응답
- 마감·정원·중복·동시 참가 충돌의 세부 오류 코드; 기본 HTTP 상태는 409
- 참가자 명단은 공개 응답에서 제외하고 본인 상태와 관리 조회를 구분

글에 deleted_at이 있으면 일반 목록/상세에서 제외하는 조건을 준비합니다. 글 삭제 API는 후속이며 본인 참가 취소 DELETE는 MVP에 포함합니다.

## 2·3단계 구현 계약

- 부원 등록 PUT은 본문 없이 204를 반환하며 반복 등록은 기존 등록 정보도 유지합니다. LEFT 재등록만 등록자·가입 시각을 갱신합니다.
- 가입 현황 GET은 `{userId, clubs: [{id, name, operationStatus}], activeClubCount}`를 반환합니다. LEFT는 제외하고 ACTIVE 동아리만 activeClubCount에 포함합니다.
- 부원 명단 GET은 page=0, size=20(최대 100), 사용자 ID 오름차순이며 `{content: [{id, intraLogin, displayName}], page, size, totalElements, totalPages}`를 반환합니다.
- 공지 생성은 `{title, content}`를 받고 201·Location과 `{id}`를 반환합니다. 제목 1~200자, 본문 1~50,000자이며 공백만 있는 값은 거절합니다.
- 공지 목록은 page=0, size=20(최대 100), 생성 시각 내림차순·ID 내림차순이며 `{content: [{id, clubId, title, createdAt}], page, size, totalElements, totalPages}`입니다. 상세는 content·authorId를 추가합니다.
- 없는 리소스는 404, 권한 부족은 403, 비활성 동아리의 부원 등록은 409 `CLUB_INACTIVE`, 입력 오류는 400입니다. 공지 목록·상세는 비로그인 공개이며 삭제된 공지는 제외합니다.
