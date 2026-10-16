# MVP API 연결 예시

정확한 경로·필드·권한·오류는 [API 계약](api-endpoints.md)이 기준입니다. 아래 ID와 날짜는 설명용입니다. 생성 시각은 실제 요청 시점보다 미래로 지정합니다. 변경 요청에는 로그인 세션 쿠키와 현재 CSRF 토큰을 함께 전달합니다.

## 부원 등록·현황·명단

```http
PUT /api/v1/admin/clubs/1/members/2
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...
```

ADMIN·SUPER_ADMIN은 본문 없이 요청하고 204를 받습니다. `GET /api/v1/admin/users/2/clubs`는 다음과 같습니다.

```json
{"userId":2,"clubs":[{"id":1,"name":"예시 동아리","operationStatus":"ACTIVE"}],"activeClubCount":1}
```

등록된 사용자 2는 `GET /api/v1/clubs/1/members`로 명단을 조회합니다.

```json
{"content":[{"id":2,"intraLogin":"example","displayName":"예시"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
```

## 공지 생성 → 공개 조회

```http
POST /api/v1/clubs/1/notices
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{"title":"정기 모임 안내","content":"모임 내용을 안내합니다."}
```

해당 동아리 회장의 요청은 201, `Location: /api/v1/notices/10`, `{"id":10}`을 반환합니다. 목록 `GET /api/v1/clubs/1/notices`와 상세 `GET /api/v1/notices/10`은 로그인 없이 조회합니다.

```json
{"id":10,"clubId":1,"authorId":2,"title":"정기 모임 안내","content":"모임 내용을 안내합니다.","createdAt":"2030-05-01T00:00:00Z"}
```

## 활동 생성 → 공개 상세 → 참가·출결

사진 유무와 관계없이 multipart입니다. activity 파트의 Content-Type은 application/json으로 지정합니다.

```text
POST /api/v1/clubs/1/activities
Content-Type: multipart/form-data; boundary=...
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

activity (application/json):
{"title":"동아리 활동","content":"활동 소개","startsAt":"2030-05-02T09:00:00+09:00","recruitmentClosesAt":"2030-05-02T08:00:00+09:00","capacity":10}
images (image/png): 첫 사진 파일
images (image/jpeg): 두 번째 사진 파일
```

사진 없이 생성할 때는 images 파트를 생략합니다. 201, `Location: /api/v1/activities/20`, `{"id":20}`을 받습니다. `GET /api/v1/activities/20`의 사진 없는 상세 예시는 다음과 같습니다.

```json
{"id":20,"clubId":1,"organizerId":2,"title":"동아리 활동","content":"활동 소개","startsAt":"2030-05-02T00:00:00Z","recruitmentClosesAt":"2030-05-01T23:00:00Z","capacity":10,"currentParticipantCount":1,"remainingCapacity":9,"recruiting":true,"dDay":"D-1","representativeImageUrl":null,"images":[]}
```

사진이 있으면 representativeImageUrl과 images의 url을 img src에 사용합니다. images는 `[{"url":"https://...","sortOrder":0}]` 형태입니다. 목록은 같은 활동 DTO에서 content=null, images=[]이며 대표 이미지 URL을 제공합니다. 공개 응답에는 참가자 명단이 없습니다.

- 참가: `POST /api/v1/activities/20/participants` → 204. 본문·사용자 ID를 보내지 않습니다.
- 본인 상태: `GET /api/v1/activities/20/participants/me` → `{"status":"CONFIRMED","attendanceStatus":"UNCHECKED"}`. 미참가는 두 필드가 null입니다.
- 취소: `DELETE /api/v1/activities/20/participants/me` → 204. 주최자와 출결이 기록된 참가자는 취소할 수 없습니다.
- 주최자 명단: `GET /api/v1/activities/20/participants?page=0&size=20`.
- 주최자 출결: `PATCH /api/v1/activities/20/participants/3/attendance`, `{"attendanceStatus":"PRESENT"}` → 204. UNCHECKED로 되돌릴 수 있습니다.

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

{"title":"독서 모임","content":"함께 책을 읽습니다.","session":{"startsAt":"2030-05-02T09:00:00+09:00","endsAt":"2030-05-02T11:00:00+09:00","recruitmentClosesAt":"2030-05-02T08:00:00+09:00","location":"캠퍼스","capacity":6}}
```

201, `Location: /api/v1/gatherings/30`, `{"gatheringId":30,"sessionId":40}`을 받습니다. 공개 소개는 `GET /api/v1/gatherings/30`, 회차는 `GET /api/v1/gatherings/30/sessions?filter=UPCOMING` 또는 `GET /api/v1/gathering-sessions/40`으로 조회합니다.

다음 회차는 주최자가 `POST /api/v1/gatherings/30/sessions`에 위 session 내부 JSON만 보내 생성합니다. 같은 gatheringId와 새 sessionId를 반환하며 이전 참가자·출결은 복사하지 않습니다.

회차 참가·취소·본인 상태·명단·출결은 활동과 같은 계약이며 경로만 `/api/v1/gathering-sessions/40/participants`를 사용합니다. 모임 ID 30을 회차 ID 대신 보내지 않습니다.

소모임 목록의 항목은 `{id, organizerId, title, content:null, representativeImageUrl:null, representativeSession}`이고, 상세는 content를 제공하며 representativeSession=null입니다. 상세 화면은 회차 목록을 함께 조회합니다. 회차 DTO는 다음과 같습니다.

```json
{"sessionId":40,"gatheringId":30,"startsAt":"2030-05-02T00:00:00Z","endsAt":"2030-05-02T02:00:00Z","recruitmentOpensAt":"2030-05-01T00:00:00Z","recruitmentClosesAt":"2030-05-01T23:00:00Z","location":"캠퍼스","capacity":6,"currentParticipantCount":1,"remainingCapacity":5,"recruiting":true,"dDay":"D-1","cancelled":false}
```

## 오류 처리

```json
{"code":"CAPACITY_FULL","message":"정원이 찼습니다."}
```

HTTP 409 상태에서 code로 정원·마감·중복 참가 등을 구분합니다. 로그인 없이 조회하는 공개 API와 인증이 필요한 본인 상태·명단 API를 구분합니다. 서명 이미지 URL은 만료되면 활동을 다시 조회해 갱신합니다.
