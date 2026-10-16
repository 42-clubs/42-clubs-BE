# 환경변수

개발 과정에서 추가·변경하는 환경변수의 용도·기본값·필수 조건을 관리합니다. 설정 원본은 [application.yml](../src/main/resources/application.yml)과 [compose.yaml](../compose.yaml), 값 입력 예시는 [.env.example](../.env.example)입니다. 변수를 변경할 때 세 파일과 이 문서의 해당 항목을 동기화합니다. 실제 클라이언트 시크릿·운영 DB 비밀번호는 문서와 저장소에 기록하지 않습니다.

## 설정 방법

- Spring Boot는 프로젝트의 `.env`를 자동으로 읽지 않습니다. 터미널에서 export하거나 IntelliJ 실행 설정의 Environment variables에 입력합니다.
- Docker Compose는 프로젝트의 `.env`를 읽습니다. Compose가 읽은 값이 IDE에서 실행하는 백엔드에 자동 전달되지는 않습니다.
- 기본 로컬 DB 설정은 별도 입력 없이 실행할 수 있습니다. 실제 42 로그인을 테스트하려면 아래 인증 활성화·자격증명 설정이 필요합니다.
- 환경변수를 변경한 뒤 백엔드를 다시 실행합니다. Compose DB의 기존 볼륨에 저장된 DB 이름·계정은 환경변수 변경만으로 재생성되지 않습니다.

## 데이터베이스

| 변수 | 사용 대상 | 기본값 | 필수 조건·용도 |
| --- | --- | --- | --- |
| POSTGRES_DB | Docker Compose | clubs | 생성할 DB 이름. 바꾸면 DB_URL의 DB 이름도 맞춥니다. |
| POSTGRES_PORT | Docker Compose | 5432 | 호스트에서 접속할 PostgreSQL 포트. 바꾸면 DB_URL의 포트도 맞춥니다. |
| DB_URL | Spring Boot | jdbc:postgresql://localhost:5432/clubs | 백엔드 JDBC 접속 주소. 운영에서는 명시적으로 설정합니다. |
| DB_USERNAME | Spring Boot·Docker Compose | clubs | 백엔드 접속 계정·컨테이너 초기 계정. 운영에서는 명시적으로 설정합니다. |
| DB_PASSWORD | Spring Boot·Docker Compose | clubs_local | 백엔드 접속 비밀번호·컨테이너 초기 비밀번호. 기본값은 로컬 전용입니다. |

## 인증

| 변수 | 기본값 | 필수 조건·용도 |
| --- | --- | --- |
| AUTH_ENABLED | false | true일 때 42 OAuth 로그인 기능을 활성화합니다. false여도 공개 조회·CSRF API는 사용할 수 있습니다. |
| FT_CLIENT_ID | 빈 값 | AUTH_ENABLED=true일 때 필수. 42 OAuth 앱에서 발급한 UID입니다. |
| FT_CLIENT_SECRET | 빈 값 | AUTH_ENABLED=true일 때 필수. 같은 OAuth 앱에서 발급한 Secret입니다. |
| FT_REDIRECT_URI | http://localhost:8080/login/oauth2/code/42 | 42가 인가 결과의 code·state를 돌려보낼 백엔드 콜백. 42 앱의 Redirection URI와 정확히 같아야 합니다. |
| AUTH_SUCCESS_URL | http://localhost:5173/auth/callback | 백엔드가 로그인 처리를 끝낸 뒤 브라우저를 보낼 주소. 프론트엔드 없이 테스트할 때는 http://localhost:8080/api/v1/me로 설정합니다. |
| AUTH_ALLOWED_ORIGINS | http://localhost:5173 | 쿠키를 포함한 CORS 요청을 허용할 정확한 프론트엔드 origin. 여러 값은 쉼표로 구분하며 경로를 포함하지 않습니다. |
| AUTH_COOKIE_SECURE | false | 로컬 HTTP는 false, HTTPS 배포는 true. true이면 브라우저는 HTTP 요청에 세션 쿠키를 보내지 않습니다. |
| AUTH_COOKIE_SAME_SITE | lax | lax / strict / none. 로컬은 lax. 서로 다른 사이트의 프론트엔드에 쿠키를 보내려면 none과 Secure=true를 함께 설정합니다. 브라우저의 타사 쿠키 제한도 확인해야 합니다. |

인증 변수는 모두 Spring Boot에서 읽습니다. 경산 제한은 42 응답 이메일의 정확한 `student.42gyeongsan.kr` 또는 `42gyeongsan.kr` 도메인을 검사하며 환경변수로 완화하지 않습니다. API 권한과 응답 계약은 [인증 명세](api-endpoints.md#인증--구현-계약)를 따릅니다.

## 프론트엔드 없이 로컬 로그인 테스트

1. 42 OAuth 앱 설정의 Redirection URI를 `http://localhost:8080/login/oauth2/code/42`로 변경합니다. GitHub 저장소 주소는 이 프로젝트의 OAuth 콜백을 처리하지 않습니다.
2. 백엔드 실행 환경에 다음 값을 설정합니다. UID·Secret은 직접 입력하고 저장소에 커밋하지 않습니다.

```sh
export AUTH_ENABLED=true
export FT_CLIENT_ID='발급받은 UID'
export FT_CLIENT_SECRET='발급받은 Secret'
export FT_REDIRECT_URI='http://localhost:8080/login/oauth2/code/42'
export AUTH_SUCCESS_URL='http://localhost:8080/api/v1/me'
export AUTH_COOKIE_SECURE=false
export AUTH_COOKIE_SAME_SITE=lax
docker compose up -d --wait
./gradlew bootRun
```

3. 백엔드를 실행한 컴퓨터의 브라우저에서 `http://localhost:8080/oauth2/authorization/42`로 접속하여 42경산 계정으로 인증합니다. 콜백 주소에 직접 접속하지 않습니다.
4. 성공하면 같은 브라우저가 `/api/v1/me`로 이동하고 `id`·`intraLogin`·`displayName`·`role` JSON이 표시됩니다. 프론트엔드는 필요하지 않습니다. 같은 브라우저에서 `/api/v1/me`를 다시 열어도 200으로 내 정보가 표시되면 세션 인증도 확인할 수 있습니다.

```text
브라우저 → 백엔드 로그인 시작 → 42 인증 화면
  → FT_REDIRECT_URI: 백엔드가 code·state 확인, 42 프로필 조회, 사용자 저장, 세션 생성
  → AUTH_SUCCESS_URL: 로그인 완료 후 내 정보 표시 또는 프론트엔드로 이동
```

로컬 콜백은 브라우저가 해당 컴퓨터의 백엔드에 접속하는 방식입니다. 42 서버가 로컬 DB에 직접 접근할 필요는 없습니다. 시작·콜백·확인 주소를 모두 `localhost`로 통일합니다. `127.0.0.1`을 사용하려면 등록 URI와 설정·접속 주소도 모두 같이 변경해야 합니다.

## 테스트 중 확인할 사항

| 증상 | 확인할 설정·조건 |
| --- | --- |
| 로그인 시작에서 401 | AUTH_ENABLED=true인지, 실행 중인 백엔드에 환경변수가 전달됐는지 확인합니다. |
| 백엔드가 자격증명 오류로 시작하지 않음 | FT_CLIENT_ID·FT_CLIENT_SECRET이 비어 있지 않은지 확인합니다. |
| 42에서 redirect URI 오류 | 42 앱 등록값과 FT_REDIRECT_URI의 스킴·호스트·포트·경로가 같은지 확인합니다. |
| 인증 뒤 GitHub로 이동 | 42 앱의 Redirection URI를 백엔드 콜백으로 변경했는지 확인합니다. |
| 인증 뒤 localhost:5173 접속 실패 | 기본 AUTH_SUCCESS_URL입니다. 프론트엔드 없이 테스트할 때는 백엔드 `/api/v1/me` 주소로 변경합니다. |
| 콜백에서 OAUTH_LOGIN_FAILED | 같은 앱의 UID·Secret, code·state 처리, 42 응답 이메일의 경산 도메인을 확인합니다. 로그인 시작 주소에서 다시 시도합니다. |
| 성공 뒤 /me가 401 | 같은 브라우저·호스트를 사용하는지, 로컬 HTTP에서 AUTH_COOKIE_SECURE=false인지, 서버가 재시작되지 않았는지 확인합니다. |

배포 환경의 콜백과 로그인 성공 주소는 실제 HTTPS 서비스 주소로 설정합니다. 배포 주소가 확정되면 해당 값을 이 문서에 추가합니다.

## 프론트엔드와 배포 연결

- 프론트엔드 fetch는 `credentials: 'include'`를 사용합니다. 로그인·CSRF·로그아웃 순서는 [인증 API](api-endpoints.md#인증--구현-계약)를 따릅니다.
- `AUTH_ALLOWED_ORIGINS`에는 정확한 프론트엔드 origin을 설정합니다. HTTPS 배포에서는 `AUTH_COOKIE_SECURE=true`가 필요합니다.
- 서로 다른 사이트에서 쿠키를 사용할 때는 SameSite=none과 Secure가 필요하며 브라우저의 타사 쿠키 제한도 확인합니다. 가능하면 같은 사이트에 배포합니다.
- 세션은 서버 메모리에 있어 재시작 시 재로그인이 필요합니다. 다중 서버 운영이 필요하면 공유 세션 저장소를 별도 도입합니다.

## 로그인 실패 서버 로그

브라우저에는 공통 `OAUTH_LOGIN_FAILED` 응답을 반환합니다. `bootRun` 터미널의 WARN 로그는
`42 OAuth login failed: reason=... hint=...` 형식으로 실패 분류와 확인할 설정을 표시합니다.

| reason | 확인할 사항 |
| --- | --- |
| campus_not_allowed | 42 응답 이메일이 정확한 `student.42gyeongsan.kr` 또는 `42gyeongsan.kr` 도메인인지 |
| invalid_42_profile | 이메일·ID·로그인명 등 필수 프로필의 누락·형식 오류 |
| authorization_request_not_found | state에 일치하는 인가 요청 없음. 세션 누락·state 불일치·쿠키·호스트 변경·서버 재시작 |
| invalid_state_parameter | OAuth state 불일치. 시작 주소에서 다시 로그인 |
| invalid_client | 같은 앱에서 발급한 UID·Secret인지 |
| invalid_grant | 인가 코드 만료·재사용, 콜백 URI 불일치 |
| invalid_token_response | 토큰 교환 오류. 자격증명·콜백 URI·42 서버 연결 확인 |
| invalid_user_info_response | 42 프로필 조회 오류. 42 서버 연결·응답 확인 |
| access_denied | 사용자가 인가를 거부·취소하거나 제공자가 거부 |
| invalid_request / invalid_scope | OAuth 요청 매개변수·허용 scope |
| server_error / temporarily_unavailable | 42 인증 서버 오류·일시적 사용 불가 |
| unknown_oauth_error | 알려진 코드로 분류되지 않은 인증 오류 |

토큰 교환 예외에 더 구체적인 제공자 오류가 있으면 해당 코드를 사용합니다.
각 hint는 확인할 후보이며 원인을 확정하는 메시지는 아닙니다. 제공자가 알려주지 않은 세부 원인은 구분할 수 없습니다.
예외 메시지·스택 트레이스·제공자 응답 원문·요청 URL/매개변수·쿠키·이메일·프로필·Secret·토큰은 이 로그에 기록하지 않습니다.
알 수 없는 외부 오류 코드도 원문 대신 `unknown_oauth_error`로 기록합니다.

## 활동 사진 저장소

| 이름 | 용도 | 기본값 | 필수 조건 | 사용 대상 |
| --- | --- | --- | --- | --- |
| PHOTO_STORAGE_MODE | 저장 방식 local 또는 s3 | local | 항상; 다른 값은 시작 실패 | 백엔드 |
| PHOTO_LOCAL_DIRECTORY | 로컬 사진 저장 폴더 | ./data/photos | local 모드; 실행 계정 쓰기 권한 필요 | 백엔드 |
| PHOTO_LOCAL_BASE_URL | 브라우저에서 접근하는 백엔드 HTTP(S) 기본 주소 | http://localhost:8080 | local 모드; 실제 호스트·포트와 일치 | 백엔드 응답·프론트 이미지 조회 |
| PHOTO_S3_BUCKET | 활동 사진 S3 버킷 이름 | 빈 값 | s3 모드에서 사진 첨부 시 필수 | 백엔드 |
| PHOTO_S3_REGION | 버킷 리전 | ap-northeast-2 | s3 모드에서 버킷 리전과 일치 | 백엔드 |
| PHOTO_PUBLIC_BASE_URL | 사진 조회용 HTTPS 기본 URL | 빈 값 | s3 모드에서 선택; 공개 이미지 도메인 사용 시 | 백엔드 응답·프론트 이미지 조회 |

로컬 평가는 별도 환경변수 없이 `./gradlew bootRun`으로 실행합니다. 사진은 실행 작업 디렉터리 기준 `./data/photos`에 자동 생성하며 Git 추적에서 제외합니다. 프론트는 응답의 절대 URL을 그대로 사용합니다. DB 저장이 완료된 미삭제 활동 사진만 비로그인 조회할 수 있습니다. 업로드·DB 실패 파일 정리는 로컬 모드에도 동일하게 적용합니다.

다른 포트·호스트에서 실행할 때 `PHOTO_LOCAL_BASE_URL=http://localhost:8081`처럼 셸 또는 IDE에 설정합니다. 저장 위치는 `PHOTO_LOCAL_DIRECTORY=/절대/경로/photos`로 변경할 수 있습니다. `.env`는 자동으로 읽지 않습니다. 재시작 후에도 같은 디렉터리와 DB를 유지하면 사진이 보존됩니다. 컨테이너 실행 시 해당 디렉터리를 볼륨으로 보존합니다. 이 설정은 사진만 로컬화하며 PostgreSQL·42 로그인 설정은 기존과 같습니다.

기존 S3 사진은 자동으로 로컬로 복사하지 않습니다. 기존 DB를 재사용하려면 `storage_key`와 같은 상대 경로로 파일을 로컬 디렉터리에 복사하거나 기존 S3 모드를 유지해야 합니다. 저장소 변경 시 정리 대기 저널도 기존 저장소에서 처리한 뒤 전환합니다.

S3를 사용할 때만 `PHOTO_STORAGE_MODE=s3`와 기존 S3 버킷 이름·리전을 셸 또는 IDE에 설정합니다. 자격증명은 AWS SDK 기본 자격증명 체인(AWS 프로필·환경변수·배포 실행 역할)을 사용하며 저장소에 비밀값을 기록하지 않습니다. S3 버킷 미설정이어도 사진 없는 활동 등록은 가능합니다. S3 사진 첨부는 버킷 미설정 시 503 STORAGE_UNAVAILABLE입니다. 로컬 모드는 디스크 저장·조회 장애 시 503입니다.
기본 URL이 없으면 비공개 버킷의 15분 만료 서명 GET URL을 반환합니다. 프론트는 만료 시 활동을 재조회합니다. 기본 URL이 있으면 해당 도메인이 storage_key 경로의 사진을 실제로 제공하도록 배포 환경에서 구성해야 합니다. 버킷 생성·공개 권한 변경은 이 애플리케이션에서 수행하지 않습니다.
배포 시 HTTPS·저장 암호화를 사용하고 실행 주체에는 해당 버킷 activities/ 경로의 PutObject/GetObject/DeleteObject 권한만 부여합니다. 접근 로그·CloudTrail 데이터 이벤트·CloudWatch 지표 활성화를 권장하며 저장·요청·전송·관측 비용이 발생할 수 있습니다. [S3 모니터링](https://docs.aws.amazon.com/AmazonS3/latest/userguide/monitoring-overview.html), [S3 요금](https://aws.amazon.com/s3/pricing/)을 참고합니다.
전체 multipart 요청은 27MB, 파일은 5MB로 제한하며 최대 5장입니다. 정리 대기 파일 키는 image_uploads에 먼저 기록하고 성공한 DB 트랜잭션에서 제거합니다. 실패 요청은 즉시 삭제를 시도하고, 삭제 실패 또는 프로세스 중단으로 남은 키는 10분 후부터 1분 간격으로 최대 100건씩 재시도합니다.
