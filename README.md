# Spring 회원·게시판·댓글 REST API

Java 17, Spring Boot 3.5.16, Spring Data JPA, Spring Security, H2로 구현한 게시판 서버입니다. 공개 읽기, 세션 로그인, 작성자 권한, 페이지 조회, 제목·본문 검색을 지원합니다.

## 실행 방법

JDK 17 이상과 인터넷 연결이 필요합니다. Maven은 Wrapper가 내려받으므로 별도로 설치하지 않습니다. DB 설치·계정 준비도 필요 없습니다. 저장소 루트에서 실행합니다.

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

```bash
# macOS / Linux
./mvnw spring-boot:run
```

또는 Docker와 Compose가 설치되어 있다면 JDK 설치 없이 한 번에 실행합니다.

```bash
docker compose up --build
```

주소는 `http://localhost:8080`입니다. 로컬 실행은 `data/`에 H2 파일을, Compose는 `board-data` 볼륨에 저장하여 재시작 후에도 데이터가 남습니다. H2의 `sa` 계정은 로컬 개발용 무암호 계정이며 외부 DB 서버나 H2 콘솔을 열지 않습니다. `target/`, `data/`, 세션 쿠키, 개인 설정·비밀 파일은 Git 및 제출 zip에서 제외합니다.

테스트 및 패키징:

```powershell
.\mvnw.cmd verify
```

```bash
./mvnw verify
```

실제 HTTP 시나리오 재실행은 서버를 켜둔 상태에서 다른 PowerShell 창에서 가능합니다. 실행할 때마다 별도 예제 회원을 가입하고, 예제로 작성한 글과 댓글은 마지막에 삭제합니다.

```powershell
.\scripts\smoke.ps1
# 요청·응답 파일까지 저장하려면
.\scripts\smoke.ps1 -OutputPath .\docs\execution.md
```

Docker Compose 설정 검증은 통과했으나 이 환경의 Docker 엔진이 실행되지 않아 컨테이너 구동은 직접 검증하지 않았습니다. Maven Wrapper의 빌드·테스트 및 실제 Java 서버의 HTTP 시나리오는 검증했습니다.

## 로그인과 CSRF

로그인은 서버 세션을 사용합니다. 요청 쿠키의 `JSESSIONID`로 인증하므로 HTTP 클라이언트는 쿠키를 보관하고 다음 요청에 전송해야 합니다. 먼저 `GET /api/auth/csrf`로 토큰과 세션 쿠키를 받습니다. 모든 POST·PUT·DELETE 요청에 발급된 `X-CSRF-TOKEN` 헤더를 보냅니다. **로그인 성공 시 세션 ID와 CSRF 토큰이 바뀌므로 CSRF를 다시 조회**합니다. 로그아웃 후에도 새 토큰이 필요합니다.

CSRF는 가입·로그인에도 적용됩니다. 비로그인 쓰기 요청은 토큰 유무와 관계없이 401입니다. 인증된 요청에 CSRF가 빠지거나 잘못되면 403입니다. 따라서 입력 오류 400 검증은 먼저 필요한 인증·CSRF 조건을 갖춘 요청으로 합니다. 인증·CSRF 검증이 DTO 검증보다 먼저 실행됩니다.

세션 제한 시간은 마지막 요청 이후 30분이며, 서버 재시작 시 로그인은 해제됩니다. 쿠키는 HttpOnly 및 SameSite=Strict입니다. 로컬 HTTP에서는 Secure가 꺼져 있으며 HTTPS 배포 시 `COOKIE_SECURE=true`를 지정합니다. CORS는 열지 않았으므로 브라우저 프런트엔드는 같은 출처를 사용해야 합니다.

## API 명세

요청·응답은 JSON입니다. 쓰기 요청은 `Content-Type: application/json`을 사용합니다. 아래 표의 `—`는 본문 없음을 뜻합니다. `CSRF`는 로그인 필요 여부와 별개입니다.

| 메서드 | 주소 | 로그인 | CSRF | 요청 | 성공 응답 | 성공 코드 |
|---|---|---|---|---|---|---|
| GET | `/api/auth/csrf` | 불필요 | 불필요 | — | Csrf | 200 |
| POST | `/api/auth/signup` | 불필요 | 필요 | Signup | Member | 201 |
| POST | `/api/auth/login` | 불필요 | 필요 | Login | Member + 세션 쿠키 | 200 |
| GET | `/api/auth/me` | 필요 | 불필요 | — | Member | 200 |
| POST | `/api/auth/logout` | 필요 | 필요 | — | — | 204 |
| GET | `/api/posts?page=0&size=20&q=` | 불필요 | 불필요 | 쿼리 매개변수 | Page | 200 |
| GET | `/api/posts/{id}` | 불필요 | 불필요 | — | Post | 200 |
| POST | `/api/posts` | 필요 | 필요 | PostInput | Post + Location | 201 |
| PUT | `/api/posts/{id}` | 작성자 | 필요 | PostInput | Post | 200 |
| DELETE | `/api/posts/{id}` | 작성자 | 필요 | — | — | 204 |
| GET | `/api/posts/{id}/comments` | 불필요 | 불필요 | — | Comment 배열 | 200 |
| POST | `/api/posts/{id}/comments` | 필요 | 필요 | CommentInput | Comment + Location | 201 |
| PUT | `/api/comments/{id}` | 작성자 | 필요 | CommentInput | Comment | 200 |
| DELETE | `/api/comments/{id}` | 작성자 | 필요 | — | — | 204 |

목록은 `createdAt DESC, id DESC`로 최신순 정렬합니다. `page`는 0부터 시작하고 0~1,000,000, `size`는 1~100(기본 20)입니다. `q`는 최대 200자이며 제목 또는 본문의 대소문자 구분 없는 부분 문자열 검색입니다. `%`, `_`, `!`는 와일드카드로 쓰지 않고 문자 그대로 검색합니다. 빈 검색어는 전체 목록입니다. 범위를 넘어선 정상 페이지는 빈 content 배열을 반환합니다. 댓글은 작성 시각·ID 오름차순이며 대댓글은 구현하지 않았습니다.

### 요청 DTO

```json
// Signup
{"email":"alice@example.com","password":"DemoPass123!","nickname":"Alice"}
// Login
{"email":"alice@example.com","password":"DemoPass123!"}
// PostInput (생성·수정 모두 제목과 본문 필수)
{"title":"Spring 첫 글","content":"게시글 본문"}
// CommentInput
{"content":"댓글 본문"}
```

실제 JSON에서는 위의 설명 주석을 제외합니다. 이메일은 필수·이메일 형식·최대 254자이며 소문자로 정규화하여 중복을 검사합니다. 비밀번호는 8~72자이고 UTF-8 기준으로도 72바이트 이하여야 합니다(BCrypt 입력 한도). 닉네임은 공백 외 1~40자, 제목은 공백 외 1~200자, 게시글 본문은 공백 외 1~20,000자, 댓글은 공백 외 1~2,000자입니다. ID는 양의 정수여야 합니다.

### 응답 DTO

시간은 UTC ISO-8601입니다. 엔티티, 회원 비밀번호, 비밀번호 해시, 작성자 이메일은 게시글·댓글 응답에 포함되지 않습니다.

```json
// Csrf
{"headerName":"X-CSRF-TOKEN","token":"발급받은 토큰"}
// Member
{"id":1,"email":"alice@example.com","nickname":"Alice","createdAt":"2026-10-13T00:00:00Z"}
// Post (목록·상세·생성·수정 동일 형태)
{"id":1,"title":"Spring 첫 글","content":"게시글 본문","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T00:00:00Z","updatedAt":"2026-10-13T00:00:00Z","commentCount":1}
// Comment
{"id":1,"postId":1,"content":"댓글 본문","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T00:00:00Z","updatedAt":"2026-10-13T00:00:00Z"}
// Page: content 각 원소는 위 Post
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

### 오류 코드와 공통 본문

컨트롤러 예외, Spring Security 인증·권한·CSRF 실패, 서블릿 오류 모두 아래 형태입니다.

```json
{"timestamp":"2026-10-13T00:00:00Z","status":404,"code":"NOT_FOUND","message":"Post not found","path":"/api/posts/999"}
```

| 상태 | code | 해당 요청 |
|---|---|---|
| 400 | `INVALID_INPUT` | 잘못된 JSON·필수값·길이·이메일·경로 ID·페이지, 데이터 충돌 |
| 400 | `DUPLICATE_EMAIL` | 이미 가입한 이메일 |
| 401 | `UNAUTHORIZED` | 로그인 필요한 모든 요청의 인증 실패, 로그인 비밀번호 오류, 비로그인 CSRF 실패 |
| 403 | `FORBIDDEN` | 다른 사람의 글·댓글 수정/삭제, 로그인 상태의 CSRF 실패 |
| 404 | `NOT_FOUND` | 없는 게시글·댓글, 없는 글의 댓글 조회·작성 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | JSON 요청에 잘못된 Content-Type |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 장애만 해당, 내부 예외 내용은 숨김 |

가입은 입력·중복 오류 400, 로그인은 입력 오류 400·잘못된 자격 증명 401입니다. 게시글·댓글 생성은 입력 오류 400(댓글 생성은 없는 글 404 추가), 읽기는 잘못된 경로·페이지 400 및 없는 대상 404, 수정·삭제는 잘못된 입력 400·없는 대상 404·다른 작성자 403입니다. 로그인 필요 요청에는 공통으로 401, 쓰기 요청에는 CSRF 실패 코드가 적용됩니다. 소유권은 본문의 authorId가 아니라 인증된 회원과 DB 작성자로 검사합니다.

## 설계 설명

### 세션을 선택한 이유

단일 서버의 게시판이므로 세션을 사용하여 토큰 서명 키·재발급·철회 저장소를 별도로 관리하지 않습니다. 로그아웃 시 서버 세션을 바로 무효화할 수 있습니다. BCrypt로 비밀번호를 저장하며 길이가 바이트 한도를 넘으면 해시를 생성하기 전에 거절합니다. 로그인은 Spring Security AuthenticationManager가 수행합니다. 직접 JSON 로그인 컨트롤러를 쓰므로 SecurityContextRepository에 인증 정보를 명시적으로 저장하고, SessionAuthenticationStrategy를 통해 세션 고정 공격 방지 및 CSRF 재발급을 처리합니다. 여러 서버로 확장할 때는 공유 세션 저장소(Spring Session 등)가 필요합니다.

### 엔티티 관계와 트랜잭션

Member 1:N Post, Member 1:N Comment, Post 1:N Comment입니다. Post·Comment의 작성자 및 Comment의 게시글은 필수 LAZY ManyToOne으로 연결합니다. 회원 이메일은 DB 유일 제약도 두어 동시 가입의 중복을 막습니다. 생성·수정·삭제는 서비스 트랜잭션으로 처리하고 조회는 readOnly입니다. 수정 시 updatedAt을 갱신합니다. Open Session In View는 끄고 서비스 내부에서 DTO로 변환합니다. 프레젠테이션 계층에는 엔티티가 나오지 않습니다.

### 목록 N+1 방지

PostRepository.list에서 작성자를 join하고 댓글을 left join하여 `count(c.id)`로 집계한 결과를 DTO 생성자에 직접 투영합니다. 페이징 데이터 쿼리와 전체 글 개수 쿼리로 최대 2회 조회합니다. 컬렉션 fetch join 후 메모리 페이징을 하지 않고 DB에서 페이지를 자릅니다. Post나 작성자 엔티티의 LAZY 필드에 반복 접근하지 않습니다. 통합 테스트에서 **글 13개·댓글 2개를 생성한 뒤 size=10으로 조회했을 때 Hibernate prepareStatementCount가 정확히 2**인지 검사합니다. 상세의 작성자와 댓글 목록의 작성자·게시글은 EntityGraph로 함께 조회합니다.

### 게시글 삭제 시 댓글

댓글을 먼저 벌크 DELETE하고 글을 DELETE하는 작업을 한 트랜잭션으로 처리합니다. 댓글을 엔티티별로 조회해서 하나씩 지우지 않습니다. 삭제된 글의 댓글 목록은 404입니다. 글 작성자는 자기 글을 삭제할 수 있고, 그 글에 달린 다른 회원의 댓글도 글과 함께 삭제됩니다. 댓글 개별 수정·삭제는 댓글 작성자만 가능합니다.

### API 설계 이유

회원 인증은 `/api/auth`에 모으고, 게시글은 복수형 리소스 `/api/posts`로 표현합니다. 댓글의 생성·목록은 게시글 종속성이 드러나는 `/api/posts/{id}/comments`, 댓글 자체의 수정·삭제는 고유 ID 기반 `/api/comments/{id}`를 씁니다. 조회 GET, 생성 POST, 편집 가능한 필드 전체 교체 PUT, 삭제 DELETE로 구분합니다. 생성은 201, 조회·수정·로그인은 200, 본문 없는 삭제·로그아웃은 204입니다. 글 생성 Location은 상세 주소이며, 댓글 생성 Location은 생성된 댓글을 포함하는 해당 글의 댓글 목록 주소입니다. 오류는 입력·인증·권한·대상 존재 여부에 따라 400·401·403·404로 구별합니다.

## 검증

통합 테스트 7개가 실제 Spring 컨텍스트·Spring Security 필터·H2를 사용합니다.

- 이메일 형식·중복·짧은 비밀번호·BCrypt 바이트 한도 및 DB 해시 저장
- 실제 CSRF 발급 → 가입 → 로그인 → 세션 ID 변경 → CSRF 재발급 → 로그아웃
- 공개 읽기 및 글·댓글의 모든 쓰기 메서드 비로그인 401
- 다른 회원의 글·댓글 수정·삭제 403
- 작성자의 수정·삭제와 글 삭제 시 댓글 제거
- 잘못된 JSON·필드·페이지·ID 400, 없는 대상 404, 잘못된 로그인 401
- 최신순 페이징·제목/본문 검색·댓글 수와 목록 SQL 2회

GitHub Actions에서도 push 및 pull request마다 `./mvnw verify`를 실행합니다.

## 실행 결과

아래는 로컬 서버를 실제 HTTP 클라이언트로 호출한 결과입니다. 전체 19단계와 재현 코드는 [docs/execution.md](docs/execution.md), [scripts/smoke.ps1](scripts/smoke.ps1)에 있습니다. 예제 비밀번호는 테스트용 가상 계정의 값입니다. 쿠키와 CSRF 토큰은 생략하고, 로그인 이후 요청은 해당 세션 쿠키 및 재발급 토큰을 전송했습니다.

## 2. 회원 가입

```http
POST /api/auth/signup
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"password":"DemoPass123!","email":"alice-ebf63001@example.com","nickname":"Alice"}
```

```http
HTTP/1.1 201

{"id":1,"email":"alice-ebf63001@example.com","nickname":"Alice","createdAt":"2026-10-13T07:15:58.658938Z"}
```


## 3. 로그인

```http
POST /api/auth/login
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"password":"DemoPass123!","email":"alice-ebf63001@example.com"}
```

```http
HTTP/1.1 200

{"id":1,"email":"alice-ebf63001@example.com","nickname":"Alice","createdAt":"2026-10-13T07:15:58.658938Z"}
```


## 5. 게시글 작성

```http
POST /api/posts
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"title":"Spring 첫 글","content":"회원과 게시판 API를 만들었습니다."}
```

```http
HTTP/1.1 201

{"id":1,"title":"Spring 첫 글","content":"회원과 게시판 API를 만들었습니다.","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T07:15:58.869934200Z","updatedAt":"2026-10-13T07:15:58.869934200Z","commentCount":0}
```


## 6. 댓글 작성

```http
POST /api/posts/1/comments
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"content":"첫 번째 댓글입니다."}
```

```http
HTTP/1.1 201

{"id":1,"postId":1,"content":"첫 번째 댓글입니다.","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T07:15:58.908908100Z","updatedAt":"2026-10-13T07:15:58.908908100Z"}
```


## 7. 비로그인 목록 조회

```http
GET /api/posts?page=0&size=10
```

```http
HTTP/1.1 200

{"content":[{"id":1,"title":"Spring 첫 글","content":"회원과 게시판 API를 만들었습니다.","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T07:15:58.869934Z","updatedAt":"2026-10-13T07:15:58.869934Z","commentCount":1}],"page":0,"size":10,"totalElements":1,"totalPages":1}
```


## 9. 비로그인 작성 401

```http
POST /api/posts
Content-Type: application/json

{"title":"비로그인","content":"실패 요청"}
```

```http
HTTP/1.1 401

{"timestamp":"2026-10-13T07:15:58.969147600Z","status":401,"code":"UNAUTHORIZED","message":"Login required or CSRF token missing","path":"/api/posts"}
```


## 14. 남의 게시글 수정 403

```http
PUT /api/posts/1
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"title":"수정 시도","content":"실패 요청"}
```

```http
HTTP/1.1 403

{"timestamp":"2026-10-13T07:15:59.118207600Z","status":403,"code":"FORBIDDEN","message":"Only the author may modify this resource","path":"/api/posts/1"}
```


## 15. 남의 댓글 삭제 403

```http
DELETE /api/comments/1
X-CSRF-TOKEN: <발급받은 토큰>
```

```http
HTTP/1.1 403

{"timestamp":"2026-10-13T07:15:59.126239700Z","status":403,"code":"FORBIDDEN","message":"Only the author may modify this resource","path":"/api/comments/1"}
```

## 참고 문서

- [Spring Boot 3.5 / Java 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Security 세션 인증 저장](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
