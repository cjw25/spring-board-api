# 실제 HTTP 실행 결과

실행 시각: 2026-10-13T16:15:58.2296974+09:00. scripts/smoke.ps1로 실행한 요청·응답입니다. CSRF 토큰과 쿠키는 생략했습니다.

## 1. CSRF 발급

```http
GET /api/auth/csrf
```

```http
HTTP/1.1 200

{"headerName":"X-CSRF-TOKEN","token":"<발급받은 토큰>"}
```

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

## 4. 로그인 후 CSRF 재발급

```http
GET /api/auth/csrf
```

```http
HTTP/1.1 200

{"headerName":"X-CSRF-TOKEN","token":"<발급받은 토큰>"}
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

## 8. 비로그인 댓글 조회

```http
GET /api/posts/1/comments
```

```http
HTTP/1.1 200

[{"id":1,"postId":1,"content":"첫 번째 댓글입니다.","authorId":1,"authorNickname":"Alice","createdAt":"2026-10-13T07:15:58.908908Z","updatedAt":"2026-10-13T07:15:58.908908Z"}]
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

## 10. 다른 회원 CSRF 발급

```http
GET /api/auth/csrf
```

```http
HTTP/1.1 200

{"headerName":"X-CSRF-TOKEN","token":"<발급받은 토큰>"}
```

## 11. 다른 회원 가입

```http
POST /api/auth/signup
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"password":"DemoPass123!","email":"bob-ebf63001@example.com","nickname":"Bob"}
```

```http
HTTP/1.1 201

{"id":2,"email":"bob-ebf63001@example.com","nickname":"Bob","createdAt":"2026-10-13T07:15:59.040695700Z"}
```

## 12. 다른 회원 로그인

```http
POST /api/auth/login
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"password":"DemoPass123!","email":"bob-ebf63001@example.com"}
```

```http
HTTP/1.1 200

{"id":2,"email":"bob-ebf63001@example.com","nickname":"Bob","createdAt":"2026-10-13T07:15:59.040696Z"}
```

## 13. 다른 회원 CSRF 재발급

```http
GET /api/auth/csrf
```

```http
HTTP/1.1 200

{"headerName":"X-CSRF-TOKEN","token":"<발급받은 토큰>"}
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

## 16. 없는 게시글 404

```http
GET /api/posts/999999999
```

```http
HTTP/1.1 404

{"timestamp":"2026-10-13T07:15:59.130207400Z","status":404,"code":"NOT_FOUND","message":"Post not found","path":"/api/posts/999999999"}
```

## 17. 잘못된 입력 400

```http
POST /api/posts
X-CSRF-TOKEN: <발급받은 토큰>
Content-Type: application/json

{"title":"","content":"실패 요청"}
```

```http
HTTP/1.1 400

{"timestamp":"2026-10-13T07:15:59.153834Z","status":400,"code":"INVALID_INPUT","message":"Invalid request input","path":"/api/posts"}
```

## 18. 작성자가 게시글과 댓글 삭제

```http
DELETE /api/posts/1
X-CSRF-TOKEN: <발급받은 토큰>
```

```http
HTTP/1.1 204


```

## 19. 삭제된 글의 댓글 조회 404

```http
GET /api/posts/1/comments
```

```http
HTTP/1.1 404

{"timestamp":"2026-10-13T07:15:59.175346600Z","status":404,"code":"NOT_FOUND","message":"Post not found","path":"/api/posts/1/comments"}
```
