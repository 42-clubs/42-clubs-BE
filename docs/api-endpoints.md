# API 엔드포인트 초안

도메인·ERD 기준의 메서드, 경로, 접근 권한을 기록합니다.
동아리 공개 목록·상세 조회는 구현했으며 아래 구현 계약을 따릅니다. 다른 API는 구현 전 초안입니다.
다른 API의 요청·응답 DTO, 오류 코드와 상세 페이지네이션은 해당 기능 구현 시 확정합니다.
인증의 로그인 시작·콜백·로그아웃 경로는 세션/토큰 및 42 연동 설계 후 확정하며 일반 회원가입 API는 없습니다.

## 공통 원칙

- 업무 API 기본 경로는 `/api/v1`입니다. 아래 경로는 이를 생략했습니다.
- 본인 식별자는 인증 정보로 결정합니다. 신청 요청에서 임의 userId를 받아 다른 사람으로 신청할 수 없습니다.
- 운영진은 ADMIN, 동아리 관리자는 해당 ClubManager 관계, 활동·소모임 주최자는 해당 organizer_id로 검사합니다.
- 중첩 경로는 자식의 소속 관계도 검사합니다. 경로의 clubId/gatheringId만 검증하고 다른 부모의 자식을 수정할 수 없습니다.
- 참가자 명단과 출결은 공개 상세 응답에 포함하지 않습니다. 주최자는 관리 명단, 참가자는 본인 상태를 조회합니다.
- 목록은 페이지네이션을 적용하며 상세 매개변수는 후속 합의합니다.
- 생성은 201, 일반 조회·수정은 200 또는 본문 없는 204를 사용합니다. 구체적인 응답은 DTO 단계에서 고정합니다.
- 인증 실패 401, 권한 부족 403, 대상 없음 404, 입력 오류 400, 정원·상태 충돌 409를 기본으로 합니다.
- DELETE 참여 요청은 관계 물리 삭제가 아닌 취소 상태 변경입니다. 삭제 정책 미정인 게시물 삭제 경로는 보류합니다.

## 내 정보와 사용자 선택

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /me | 내 정보 | 로그인 |
| GET | /me/clubs | 관리하는 동아리 | 로그인 |
| GET | /me/gatherings | 주최 소모임 및 고정 멤버 신청 내역, 필터 구분 | 로그인 |
| GET | /me/gathering-sessions | 회차별 참여 내역·본인 출결 | 로그인 |
| GET | /me/activities | 주최·참여 활동 및 본인 출결 | 로그인 |
| GET | /admin/users?intraLogin=... | 동아리 관리자 지정 대상 검색 | 운영진 |
| GET | /clubs/{clubId}/organizer-candidates?intraLogin=... | 활동 주최자 지정 대상 검색 | 해당 동아리 관리자 |
| GET | /activities/{activityId}/participant-candidates?intraLogin=... | 활동 참가자 등록 대상 검색 | 해당 활동 주최자 |

검색 결과는 내부 ID·Intra 로그인명·표시 이름으로 제한하는 초안입니다.

## 동아리와 운영진 관리

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /clubs | 동아리 목록, operationStatus 필터 | 공개 |
| GET | /clubs/{clubId} | 동아리 기본 정보·모집 안내 구현, 활동도 후속 | 공개 |
| POST | /clubs | 동아리 생성 | 운영진 |
| PATCH | /clubs/{clubId} | 소개·기본 정보 수정 | ACTIVE인 해당 동아리 관리자 |
| PUT | /clubs/{clubId}/recruitment | 모집 정보 전체 설정·갱신 | ACTIVE인 해당 동아리 관리자 |
| PATCH | /clubs/{clubId}/operation-status | 운영·휴면·아카이브·재활성화 | 운영진 |
| GET | /clubs/{clubId}/managers | 관리자 목록 | 운영진 또는 해당 동아리 관리자 |
| PUT | /clubs/{clubId}/managers/{userId} | 관리 권한 부여 | 운영진 |
| DELETE | /clubs/{clubId}/managers/{userId} | 관리 권한 해제 | 운영진 |

기본 목록에서는 아카이브를 제외하고 `operationStatus=ARCHIVED`로 아카이브를 조회합니다.
동아리 물리 삭제 API는 없습니다. 상태 전이의 효과는 [도메인 정책](domain-model.md#게시-및-아카이브-정책--확정)을 따릅니다.
운영진도 소개·모집 정보·게시물을 관리하려면 해당 동아리의 ClubManager 관계가 필요합니다.
상태에 따른 쓰기 허용 범위는 [확정 정책](domain-model.md#상태별-변경-허용-범위--확정)을 따릅니다. 아카이브 상태로 차단되는 쓰기 요청은 상태 충돌(409)로 처리합니다.

### 동아리 공개 조회 — 구현 계약

두 API는 로그인 없이 조회할 수 있으며 성공 시 200과 JSON을 반환합니다.
활동도는 활동 도메인과 집계 경계 구현 후 추가하며 현재 응답에 포함하지 않습니다.
생성·수정 API와 개발용 초기 데이터는 아직 없으므로 새 DB의 목록은 비어 있습니다.

#### 목록 요청

`GET /api/v1/clubs`

| 매개변수 | 기본값 | 허용 범위 |
| --- | --- | --- |
| page | 0 | 0 이상의 정수, Java int 범위. 첫 페이지는 0 |
| size | 20 | 1~100의 정수. 초과하면 자동 축소하지 않고 400 |
| operationStatus | 생략 | ACTIVE / DORMANT / ARCHIVED, 대문자 값 |

빈 매개변수는 page·size의 기본값 또는 필터 생략으로 처리합니다.
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

#### 상세 요청

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

#### 오류 응답

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

이번에 정한 오류 형식은 두 조회 API의 400·404 계약입니다. 인증·충돌 등 다른 오류 계약은 후속 구현에서 정합니다.

## 활동·공지와 활동 출결

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /clubs/{clubId}/activities | 활동 목록 | 공개 |
| GET | /activities/{activityId} | 활동 상세 | 공개 |
| POST | /clubs/{clubId}/activities | 활동 등록, 주최자 지정 | ACTIVE인 해당 동아리 관리자 |
| PATCH | /activities/{activityId} | 활동 내용·주최자 변경 | ACTIVE인 소속 동아리 관리자 |
| GET | /activities/{activityId}/participants | 참가자·출결 관리 명단 | 해당 활동 주최자 |
| PUT | /activities/{activityId}/participants/{userId} | 참가자 등록 | 해당 활동 주최자 |
| PATCH | /activities/{activityId}/participants/{userId}/attendance | 출결 체크·정정 | 해당 활동 주최자 |
| GET | /clubs/{clubId}/notices | 공지 목록 | 공개 |
| GET | /notices/{noticeId} | 공지 상세 | 공개 |
| POST | /clubs/{clubId}/notices | 공지 등록 | ACTIVE인 해당 동아리 관리자 |
| PATCH | /notices/{noticeId} | 공지 수정 | ACTIVE인 소속 동아리 관리자 |

참가자 제거와 게시물 삭제는 기록 보존 정책 확정 후 추가합니다.
Activity는 날짜만 있으므로 시작 시각 이후 출결 제한을 적용하려면 시간 필드 또는 날짜 기준 정책을 먼저 정해야 합니다.
아카이브 중 활동·공지 쓰기와 기존 활동의 참가자·출결 정정은 [확정 정책](domain-model.md#상태별-변경-허용-범위--확정)을 따릅니다. 기존 참가자 등록·출결 API의 주최자 권한은 유지하며, 참가자 제거 경로는 계속 보류합니다.

## 소모임과 개최 회차

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /gatherings | 소모임 목록, 진행 방식·참여 단위·목적 필터 | 공개 |
| GET | /gatherings/{gatheringId} | 소모임 상세 | 공개 |
| POST | /gatherings | 소모임 생성, 유형·참여 단위·확정 방식 지정 | 로그인 |
| PATCH | /gatherings/{gatheringId} | 기본 정보·반복 규칙 수정 | 해당 소모임 주최자 |
| PUT | /gatherings/{gatheringId}/cancellation | 전체 모임 취소 | 해당 소모임 주최자 |
| GET | /gatherings/{gatheringId}/sessions | 실제 회차 목록 | 공개 |
| POST | /gatherings/{gatheringId}/sessions | 회차 생성 | 해당 소모임 주최자 |
| GET | /gathering-sessions/{sessionId} | 회차 상세·모집 상태·잔여 정원 | 공개 |
| PATCH | /gathering-sessions/{sessionId} | 회차 일시·장소·정원 수정 | 해당 소모임 주최자 |
| PUT | /gathering-sessions/{sessionId}/cancellation | 해당 회차 취소 | 해당 소모임 주최자 |
| PUT | /gatherings/{gatheringId}/recruitment | 고정 멤버 모집 공개·마감 설정 | 해당 소모임 주최자 |
| PUT | /gathering-sessions/{sessionId}/recruitment | 회차별 모집 공개·마감 설정 | 해당 소모임 주최자 |

일정·정원 변경과 공개 검증은 [소모임 일정 정책](domain-model.md#모집-공개와-일정)을 따릅니다.
고정 멤버 모집은 FIXED_MEMBERS, 회차별 모집은 PER_SESSION에만 허용합니다.
반복 규칙의 자동 회차 생성 범위·중복 방지와 예약 공개 방식은 후속 설계입니다.
취소 복원 API는 우선 제외합니다.

## 고정 멤버 가입 — FIXED_MEMBERS 전용

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| POST | /gatherings/{gatheringId}/participants | 본인 가입 신청·재신청 | 로그인 |
| GET | /gatherings/{gatheringId}/participants/me | 본인 가입 상태 | 로그인 본인 |
| GET | /gatherings/{gatheringId}/participants | 신청자·멤버 관리 목록 | 해당 소모임 주최자 |
| PATCH | /gatherings/{gatheringId}/participants/{userId}/decision | PENDING 승인·거절 | 해당 소모임 주최자 |

참여 취소·탈퇴 경로는 중도 탈퇴가 미래 회차에 미치는 영향과 허용 시점을 정한 뒤 추가합니다.
최초 모집 취소·재신청 허용 원칙은 유지합니다. 주최자 개별 탈퇴는 허용하지 않습니다.
회차 명단 반영은 서버에서 처리하며 고정 멤버의 회차별 별도 신청·승인은 받지 않습니다.

## 회차별 신청 — PER_SESSION 전용

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| POST | /gathering-sessions/{sessionId}/participants | 본인 신청·재신청 | 로그인 |
| GET | /gathering-sessions/{sessionId}/participants/me | 본인 참여·출결 상태 | 로그인 본인 |
| DELETE | /gathering-sessions/{sessionId}/participants/me | 본인 신청·참여 취소 | 본인, 주최자 제외 |
| PATCH | /gathering-sessions/{sessionId}/participants/{userId}/decision | PENDING 승인·거절 | 해당 소모임 주최자 |

신청·재신청·취소는 [도메인 신청 규칙](domain-model.md#신청-상태와-재신청)을 따릅니다. 중복 활성 신청은 409입니다.

## 회차 출결 — 두 참여 단위 공통

| 메서드 | 경로 | 기능 | 권한 |
| --- | --- | --- | --- |
| GET | /gathering-sessions/{sessionId}/participants | 회차 참가자·출결 명단 | 해당 소모임 주최자 |
| PATCH | /gathering-sessions/{sessionId}/participants/{userId}/attendance | 출결 체크·정정 | 해당 소모임 주최자 |

FIXED_MEMBERS의 본인 회차 상태도 위 `GET .../participants/me`를 공통 허용합니다.
출결 대상·상태·입력 시점은 [도메인 출결 규칙](domain-model.md#출결--공통-설계)을 따릅니다. 체크 사용자·시각은 서버에서 기록합니다.

## 다음 검토

업무상 미정 정책은 [도메인 문서](domain-model.md#남은-결정-사항)에서만 관리합니다.
API 후속 작업은 미구현 API의 요청·응답 DTO, 필터·정렬·페이지네이션, 추가 오류 코드와 42 인증 경로 확정입니다.
