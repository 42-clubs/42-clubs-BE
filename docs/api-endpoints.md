# API 명세

인증과 동아리 공개 조회는 구현 계약입니다. 활동·공지·소모임은 미구현 초안이며 구현 시 요청·응답 DTO를 확정합니다.
[현재 MVP 정책](domain-model.md#축소-mvp--2026-10-14-확정)과 [후속 API](future-design.md#후속-api-초안)를 구분해 사용합니다.

- [공통 원칙](#공통-원칙)
- [인증 — 구현 완료](#인증--구현-계약)
- [동아리 조회 — 구현 완료](#동아리-공개-조회--구현-계약)
- [활동·공지·소모임 — 설계 초안](#mvp-api--미구현)

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
- 인증 없는 보호 API는 401 `UNAUTHENTICATED`, ADMIN 권한 부족과 CSRF 오류는 403 `ACCESS_DENIED`입니다. OAuth state·코드·42 프로필 오류 및 경산 도메인 불일치는 401 `OAUTH_LOGIN_FAILED`이며 외부 오류 세부 내용은 응답하지 않습니다.
- `AUTH_ENABLED=false`이면 OAuth 시작·콜백 기능을 활성화하지 않습니다. 공개 동아리 조회·CSRF API는 유지하며 보호 API는 인증이 필요합니다.
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

## MVP API — 미구현

아래 경로는 `/api/v1` 접두사를 생략했습니다. 글 생성·조회와 본인 선착순 참가·취소를 우선하며, 글 수정·일정 변경과 나머지 운영 API는 [후속 API](future-design.md#후속-api-초안)에서 별도 관리합니다.

### 동아리 활동·공지

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /clubs/{clubId}/activities | 활동 목록 | 공개 |
| GET | /activities/{activityId} | 활동 상세 | 공개 |
| POST | /clubs/{clubId}/activities | 활동 작성·주최자 지정 | ACTIVE인 해당 동아리 ClubManager |
| POST | /activities/{activityId}/participants | 본인 선착순 참가·재참가 | 로그인 본인 |
| GET | /activities/{activityId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 |
| DELETE | /activities/{activityId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 예외 유지 |
| GET | /clubs/{clubId}/notices | 공지 목록 | 공개 |
| GET | /notices/{noticeId} | 공지 상세 | 공개 |
| POST | /clubs/{clubId}/notices | 공지 작성 | ACTIVE인 해당 동아리 ClubManager |

USER/ADMIN 모두 ClubManager 관계로 작성 권한을 검사합니다. ADMIN 역할만으로 공지를 작성할 수 없습니다.
활동 사진 업로드 경로와 DTO는 미정입니다. 주최자의 타 사용자 수동 참가 등록은 핵심 MVP에서 사용하지 않습니다.

### 소모임

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /gatherings | 소모임 목록 | 공개 |
| GET | /gatherings/{gatheringId} | 소모임 상세 | 공개 |
| POST | /gatherings | 모집 글·첫 회차·주최자 참가 함께 생성, FIRST_COME 고정 | 로그인 |
| GET | /gatherings/{gatheringId}/sessions | 실제 회차 목록 | 공개 |
| GET | /gathering-sessions/{sessionId} | 회차 상세·모집 상태·잔여 정원 | 공개 |
| POST | /gathering-sessions/{sessionId}/participants | 본인 선착순 참가·재참가 | 로그인 본인 |
| GET | /gathering-sessions/{sessionId}/participants/me | 본인 참가·출결 상태 | 로그인 본인 |
| DELETE | /gathering-sessions/{sessionId}/participants/me | 본인 참가 취소 | 로그인 본인, 주최자 제외 |

POST /gatherings는 첫 회차의 개최·종료·모집 마감 일시·장소·정원을 함께 받아 글·회차·주최자 참가를 원자적으로 생성합니다. 실패하면 모두 롤백합니다.
기본 이미지·개최일·모집 종료일·정원과 현재 인원을 표시하며 반복·고정 멤버·승인 API는 현재 범위에 포함하지 않습니다.

### 동아리 부원 관리

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /admin/users?intraLogin=... | 부원 등록 대상 사용자 검색 | ADMIN |
| GET | /admin/users/{userId}/clubs | 현재 가입 동아리·한도 집계 조회 | ADMIN |
| PUT | /admin/clubs/{clubId}/members/{userId} | 기존 부원 직접 등록·재등록 | ADMIN, 대상 동아리 ACTIVE |
| GET | /clubs/{clubId}/members | 현재 ACTIVE 부원 명단 | 해당 동아리 ACTIVE 부원 |

별도 본인 가입 신청·승인 대기 API는 만들지 않습니다. 명단 접근은 [회원 정책](domain-model.md#동아리-회원-명단-조회--확정)을 유지합니다. ADMIN·ClubManager만으로 전체 부원 명단을 열람할 수는 없습니다. 사용자 검색 결과는 내부 ID·Intra 로그인명·표시 이름으로 제한합니다.

### 기본 출결

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /activities/{activityId}/participants | 참가자·출결 명단 | 해당 활동 주최자 |
| PATCH | /activities/{activityId}/participants/{userId}/attendance | UNCHECKED / PRESENT 기록 | 해당 활동 주최자 |
| GET | /gathering-sessions/{sessionId}/participants | 회차 참가자·출결 명단 | 해당 소모임 주최자 |
| PATCH | /gathering-sessions/{sessionId}/participants/{userId}/attendance | UNCHECKED / PRESENT 기록 | 해당 소모임 주최자 |

체크 사용자·시각은 서버에서 기록합니다. 미확인으로 되돌리면 비우며 본인 출결은 각 participants/me 응답에 포함합니다. 결석·지각 값은 허용하지 않습니다. 글 수정 제외 정책은 출결 기록 API에는 적용하지 않습니다.

### 일정·참가·목록 계약

- 활동과 소모임 첫 회차는 startsAt·recruitmentClosesAt에 오프셋을 포함한 ISO 8601 날짜·시각을 필수로 받습니다. 표시·D-Day의 날짜 기준은 Asia/Seoul이며 모집 마감은 개최보다 이전이어야 합니다.
- 참가·재참가·취소는 [시각 조건](domain-model.md#일정과-표시)을 검사합니다. 정확히 마감 시각이거나 마감 이후인 참가·취소는 409로 처리합니다.
- 활동·소모임 목록은 모집 중 우선 → 개최 시각 오름차순 → ID 오름차순으로 정렬합니다. 마감·종료 항목도 뒤쪽에 포함하며 검색 API는 후속입니다. 정렬은 페이지네이션 전에 적용합니다.
- 글·일정 수정 API는 MVP에 제공하지 않습니다. 기존 동아리 조회와 그 정렬·응답 계약은 유지합니다.

## 구현 전 확정할 계약

- 활동·소모임 목록/상세: D-Day·잔여 정원 등 나머지 DTO 이름과 상세 응답 구성
- 사진 업로드 경로·요청/응답, 기본 이미지 제공 방식
- 목록 필터·페이지네이션, 생성 응답과 참가·취소·부원 등록 응답
- 마감·정원·중복·동시 참가 충돌의 세부 오류 코드; 기본 HTTP 상태는 409
- 참가자 명단은 공개 응답에서 제외하고 본인 상태와 관리 조회를 구분

글에 deleted_at이 있으면 일반 목록/상세에서 제외하는 조건을 준비합니다. 글 삭제 API는 후속이며 본인 참가 취소 DELETE는 MVP에 포함합니다.
