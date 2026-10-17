# MVP API 연결 예시

정확한 경로·필드·권한·오류는 [API 계약](api-endpoints.md)이 기준입니다. 기능별 전체 엔드포인트는 [부원](api-endpoints.md#동아리-부원-관리) · [공지](api-endpoints.md#공지-작성조회) · [활동](api-endpoints.md#동아리-활동-작성조회) · [활동 참가·출결](api-endpoints.md#활동-참가취소출결) · [소모임·회차](api-endpoints.md#소모임회차-작성조회) · [회차 참가·출결](api-endpoints.md#회차-참가취소출결)에 정리했습니다. 아래 ID와 날짜는 설명용입니다. 생성 시각은 실제 요청 시점보다 미래로 지정합니다. 변경 요청에는 로그인 세션 쿠키와 현재 CSRF 토큰을 함께 전달합니다.

## 동아리 기본정보 부분 수정

```http
PATCH /api/v1/clubs/1
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{"summary":"새 요약","description":"새 소개"}
```

ACTIVE 동아리의 현재 회장 또는 ADMIN이 보내면 204를 받습니다. 이름은 보낸 항목에 없으므로 유지합니다. 이름 변경은 ADMIN이 `{"name":"새 이름"}`으로 요청합니다. ARCHIVED에서는 세 필드 모두 ADMIN만 수정할 수 있습니다. 회장이 이름과 소개를 함께 보내면 전체 403이고 소개도 변경하지 않습니다. null·빈 객체·알 수 없는 필드는 400이며 상세 조건은 [수정 계약](api-endpoints.md#동아리-기본정보-수정--구현-계약)을 따릅니다.

## 부원 등록·현황·명단

```http
PUT /api/v1/admin/clubs/1/members/2
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...
```

ADMIN은 본문 없이 요청하고 204를 받습니다. `GET /api/v1/admin/users/2/clubs`는 다음과 같습니다.

```json
{"userId":2,"clubs":[{"id":1,"name":"예시 동아리","operationStatus":"ACTIVE"}],"activeClubCount":1}
```

등록된 사용자 2는 `GET /api/v1/clubs/1/members`로 명단을 조회합니다.

```json
{"content":[{"id":2,"intraLogin":"example","displayName":"예시"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
```

## 동아리 공지 생성 → 권한별 조회

```http
POST /api/v1/clubs/1/notices
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{"title":"정기 모임 안내","content":"모임 내용을 안내합니다.","visibility":"MEMBERS_ONLY"}
```

해당 ACTIVE 동아리 회장 또는 운영진의 요청은 201, `Location: /api/v1/clubs/1/notices/10`, `{"id":10}`을 반환합니다. 목록 `GET /api/v1/clubs/1/notices`와 상세 `GET /api/v1/clubs/1/notices/10`은 현재 사용자의 부원·관리 관계와 공개 범위를 검사합니다. 기본 비공개 공지는 ACTIVE 부원·해당 회장·운영진만 조회하며 외부 상세 요청은 404입니다.

```json
{"id":10,"clubId":1,"authorId":2,"title":"정기 모임 안내","content":"모임 내용을 안내합니다.","visibility":"MEMBERS_ONLY","createdAt":"2030-05-01T00:00:00Z"}
```

visibility를 생략하거나 null로 보내도 MEMBERS_ONLY입니다. 외부 공개하려면 PUBLIC을 지정합니다. 익명·외부인의 목록에는 PUBLIC만 포함되고 페이지 전체 개수에서도 비공개 글은 제외됩니다.

## 메인 화면 운영 공지

```http
POST /api/v1/announcements
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{"title":"동아리 서류 제출기간","content":"기간 내 활동 서류를 제출해 주세요."}
```

ADMIN만 작성하며 201·`Location: /api/v1/announcements/10`·`{"id":10}`을 반환합니다. 메인 화면은 `GET /api/v1/announcements?page=0&size=5`로 최신 공지를 조회합니다. 동아리 가입·로그인 없이 조회하며 동아리 공지는 이 목록에 섞이지 않습니다.

```json
{"content":[{"id":10,"title":"동아리 서류 제출기간","createdAt":"2030-05-01T00:00:00Z"}],"page":0,"size":5,"totalElements":1,"totalPages":1}
```

`GET /api/v1/announcements/10` 상세:

```json
{"id":10,"authorId":2,"title":"동아리 서류 제출기간","content":"기간 내 활동 서류를 제출해 주세요.","createdAt":"2030-05-01T00:00:00Z"}
```

## 활동 생성 → 공개 상세 → 참가·출결

사진 유무와 관계없이 multipart입니다. activity 파트의 Content-Type은 application/json으로 지정합니다. 브라우저 FormData 전송 시 요청 전체의 Content-Type은 직접 지정하지 않고 브라우저가 boundary와 함께 설정하도록 합니다.

```text
POST /api/v1/clubs/1/activities
Content-Type: multipart/form-data; boundary=...
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

activity (application/json):
{"title":"동아리 활동","content":"활동 소개","startsAt":"2030-05-02T09:00:00+09:00","recruitmentClosesAt":"2030-05-02T08:00:00+09:00","capacity":10,"allowExternalParticipants":true}
images (image/png): 첫 사진 파일
images (image/jpeg): 두 번째 사진 파일
```

allowExternalParticipants는 필수이며 false이면 주최자도 해당 동아리 ACTIVE 부원이어야 생성할 수 있습니다. false인 활동의 신규·재참가도 ACTIVE 부원만 가능하고 생성 후 설정은 변경할 수 없습니다.

사진 없이 생성할 때는 images 파트를 생략합니다. 201, `Location: /api/v1/clubs/1/activities/20`, `{"id":20}`을 받습니다. `GET /api/v1/clubs/1/activities/20`의 사진 없는 상세 예시는 다음과 같습니다.

```json
{"id":20,"clubId":1,"organizerId":2,"title":"동아리 활동","content":"활동 소개","startsAt":"2030-05-02T00:00:00Z","recruitmentClosesAt":"2030-05-01T23:00:00Z","capacity":10,"allowExternalParticipants":true,"currentParticipantCount":1,"presentParticipantCount":0,"remainingCapacity":9,"recruiting":true,"dDay":"D-1","representativeImageUrl":null,"images":[]}
```

사진이 있으면 representativeImageUrl과 images의 url을 img src에 사용합니다. images는 `[{"url":"https://...","sortOrder":0}]` 형태입니다. 목록은 같은 활동 DTO에서 content=null, images=[]이며 대표 이미지 URL을 제공합니다. 공개 응답에는 참가자 명단이 없습니다.

- 참가: `POST /api/v1/clubs/1/activities/20/participants` → 204. 본문·사용자 ID를 보내지 않습니다.
- 본인 상태: `GET /api/v1/clubs/1/activities/20/participants/me` → `{"status":"CONFIRMED","attendanceStatus":"UNCHECKED"}`. 미참가는 두 필드가 null입니다.
- 취소: `DELETE /api/v1/clubs/1/activities/20/participants/me` → 204. 주최자와 출결이 기록된 참가자는 취소할 수 없습니다.
- 주최자 명단: `GET /api/v1/clubs/1/activities/20/participants?page=0&size=20`.
- 주최자 출결: `PATCH /api/v1/clubs/1/activities/20/participants/3/attendance`, `{"attendanceStatus":"PRESENT"}` → 204. UNCHECKED로 되돌릴 수 있습니다.

명단의 content 항목은 다음과 같습니다.

```json
{"userId":3,"intraLogin":"participant","displayName":"참가자","status":"CONFIRMED","attendanceStatus":"PRESENT","attendanceCheckedBy":2,"attendanceCheckedAt":"2030-05-01T01:00:00Z"}
```

## 소모임 생성 → 다음 회차 → 회차 참가

```http
POST /api/v1/gatherings
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{"title":"독서 모임","content":"함께 책을 읽습니다.","session":{"title":"5월 책 이야기","startsAt":"2030-05-02T09:00:00+09:00","endsAt":"2030-05-02T11:00:00+09:00","recruitmentClosesAt":"2030-05-02T08:00:00+09:00","location":"캠퍼스","capacity":6}}
```

201, `Location: /api/v1/gatherings/30`, `{"gatheringId":30,"sessionId":40}`을 받습니다. 공개 소개는 `GET /api/v1/gatherings/30`, 회차는 `GET /api/v1/gatherings/30/sessions?filter=UPCOMING` 또는 `GET /api/v1/gatherings/30/sessions/40`으로 조회합니다.

다음 회차는 주최자가 `POST /api/v1/gatherings/30/sessions`에 아래 JSON을 보냅니다. session으로 한 번 더 감싸지 않습니다.

```json
{"title":"다음 책 이야기","startsAt":"2030-05-09T09:00:00+09:00","endsAt":"2030-05-09T11:00:00+09:00","recruitmentClosesAt":"2030-05-09T08:00:00+09:00","location":"캠퍼스","capacity":6}
```

201·Location과 `{"gatheringId":30,"sessionId":41}`을 반환합니다. 같은 gatheringId에 새 sessionId를 만들며 이전 참가자·출결은 복사하지 않습니다.

회차 참가·취소·본인 상태·명단·출결은 활동과 같은 계약이며 경로만 `/api/v1/gatherings/30/sessions/40/participants`를 사용합니다. 모임 ID 30을 회차 ID 대신 보내지 않습니다.

소모임 목록의 항목은 `{id, organizerId, title, content:null, representativeImageUrl:null, representativeSession}`이고, 상세는 content를 제공하며 representativeSession=null입니다. 상세 화면은 회차 목록을 함께 조회합니다. 회차 DTO는 다음과 같습니다.

```json
{"sessionId":40,"gatheringId":30,"title":"5월 책 이야기","startsAt":"2030-05-02T00:00:00Z","endsAt":"2030-05-02T02:00:00Z","recruitmentOpensAt":"2030-05-01T00:00:00Z","recruitmentClosesAt":"2030-05-01T23:00:00Z","location":"캠퍼스","capacity":6,"currentParticipantCount":1,"remainingCapacity":5,"recruiting":true,"dDay":"D-1","cancelled":false}
```

## 경로 전환 확인

- 공지 상세와 활동 상세·참가·출결 호출에 clubId를 포함합니다. 본문의 clubId를 바꾸는 것으로 경로의 소속 검사를 우회할 수 없습니다.
- 예를 들어 실제 동아리 1의 활동 20에 `/api/v1/clubs/2/activities/20`으로 접근하면 404입니다. 공지도 같은 소속 규칙을 적용합니다.
- 기존 `/api/v1/notices/10`, `/api/v1/activities/20` 및 그 참가 하위 경로는 제거되었습니다. 기존 링크를 새 중첩 경로로 변경합니다.

## 프론트 호출 시 공통 처리

- fetch는 인증이 필요한 요청뿐 아니라 로그인 상태로 동아리 공지를 조회할 때도 `credentials: 'include'`를 지정합니다. 쿠키가 없으면 비공개 공지가 목록에서 빠지고 상세는 404입니다. 변경 요청에는 현재 CSRF 헤더를 보냅니다. 인증·CSRF 흐름은 [인증 계약](api-endpoints.md#인증--구현-계약)을 따릅니다.
- JSON 본문이 있는 요청만 `Content-Type: application/json`으로 전송합니다. 참가·취소·부원 등록에는 본문을 보내지 않습니다.
- 204 응답에는 JSON 본문이 없습니다. 성공 후 상태·인원 표시가 필요하면 본인 상태와 활동/회차 상세를 다시 조회합니다.
- 회차 목록 filter는 ALL(기본) / UPCOMING / ONGOING / PAST입니다. 전체 목록과 필터 모두 page·size를 사용합니다. 예: `GET /api/v1/gatherings/30/sessions?filter=PAST&page=0&size=20`.

## 오류 처리

```json
{"code":"CAPACITY_FULL","message":"정원이 찼습니다."}
```

HTTP 409 상태에서 code로 정원·마감·중복 참가 등을 구분합니다. 405·415 등 프레임워크 오류나 프록시 응답은 `{code,message}` 형식이 아닐 수 있으므로 HTTP 상태를 먼저 확인하고 JSON 여부에 맞게 처리합니다. 로그인 없이 조회하는 공개 API와 인증이 필요한 본인 상태·명단 API를 구분합니다. 로컬 사진 URL은 만료되지 않습니다. S3 모드의 서명 이미지 URL은 만료되면 활동을 다시 조회해 갱신합니다.

## 내 동아리·권한·참가 내역

로그인 쿠키를 포함해 호출합니다. 조회 요청에는 CSRF 헤더가 필요하지 않습니다.

```http
GET /api/v1/me/clubs?page=0&size=20
GET /api/v1/clubs/1/permissions/me
GET /api/v1/me/participations?period=PAST&page=0&size=20
GET /api/v1/me/gatherings?scope=ACTIVE&page=0&size=20
```

내 동아리 항목 예시:

```json
{"clubId":1,"name":"42 Music","operationStatus":"ACTIVE","isMember":true,"isManager":false,"permissions":{"isMember":true,"isManager":false,"canCreateActivity":false,"canCreateNotice":false,"canViewMembers":true,"canReadPrivateNotices":true}}
```

참가 내역·내 소모임의 그룹 항목 예시(실제 응답은 공통 페이지 구조의 content 안에 포함):

```json
{"type":"GATHERING","groupId":30,"name":"한 달 한 권","items":[{"activityId":null,"sessionId":40,"title":"10월 책 이야기","startsAt":"2026-10-28T10:00:00Z","endsAt":"2026-10-28T12:00:00Z","status":"CONFIRMED","attendanceStatus":"UNCHECKED","isOrganizer":false}]}
```

가입한 동아리 영역은 isMember, 관리 동아리 영역은 isManager로 구분합니다. 참가 예정은 period=UPCOMING, 지난 활동은 period=PAST를 사용합니다. 같은 그룹 아래 items를 나열하고 type과 groupId로 그룹을 식별합니다. 일회성도 items 한 개로 표시합니다. 참가 확정과 실제 출석은 구분합니다. 상세 이동·필터·권한의 전체 계약은 [명세](api-endpoints.md#마이페이지내-권한--구현-계약)를 따릅니다. 캘린더 API는 이번 범위에서 제외합니다.
