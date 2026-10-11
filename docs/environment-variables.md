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

인증 변수는 모두 Spring Boot에서 읽습니다. 경산 제한은 42 응답 이메일의 정확한 `42gyeongsan.kr` 도메인을 검사하며 환경변수로 완화하지 않습니다. API 권한과 응답 계약은 [인증 명세](api-endpoints.md#인증--구현-계약)를 따릅니다.

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
