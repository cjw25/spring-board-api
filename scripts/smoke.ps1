param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$OutputPath = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$transcript = [System.Text.StringBuilder]::new()
[void]$transcript.AppendLine('# 실제 HTTP 실행 결과')
[void]$transcript.AppendLine('')
[void]$transcript.AppendLine("실행 시각: $([DateTimeOffset]::Now.ToString('o')). scripts/smoke.ps1로 실행한 요청·응답입니다. CSRF 토큰과 쿠키는 생략했습니다.")
function New-Client {
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    return [System.Net.Http.HttpClient]::new($handler)
}
function Call-Api($Client, $Method, $Path, $Body, $Token, $Expected, $Label) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
    if ($Token) { [void]$request.Headers.TryAddWithoutValidation('X-CSRF-TOKEN', $Token) }
    $payload = ''
    if ($null -ne $Body) {
        $payload = $Body | ConvertTo-Json -Compress
        $request.Content = [System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, 'application/json')
    }
    $response = $Client.SendAsync($request).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $statusCode = [int]$response.StatusCode
    if ($statusCode -ne $Expected) { throw "$Label expected $Expected, got ${statusCode}: $content" }
    Write-Host "$Label -> $statusCode"
    [void]$transcript.AppendLine('')
    [void]$transcript.AppendLine("## $Label")
    [void]$transcript.AppendLine('')
    [void]$transcript.AppendLine('```http')
    [void]$transcript.AppendLine("$Method $Path")
    if ($Token) { [void]$transcript.AppendLine('X-CSRF-TOKEN: <발급받은 토큰>') }
    if ($payload) { [void]$transcript.AppendLine('Content-Type: application/json'); [void]$transcript.AppendLine(''); [void]$transcript.AppendLine($payload) }
    [void]$transcript.AppendLine('```')
    [void]$transcript.AppendLine('')
    [void]$transcript.AppendLine('```http')
    [void]$transcript.AppendLine("HTTP/1.1 $statusCode")
    [void]$transcript.AppendLine('')
    if ($Path -eq '/api/auth/csrf') {
        [void]$transcript.AppendLine('{"headerName":"X-CSRF-TOKEN","token":"<발급받은 토큰>"}')
    } else { [void]$transcript.AppendLine($content) }
    [void]$transcript.AppendLine('```')
    $response.Dispose(); $request.Dispose()
    if ($content) { return ($content | ConvertFrom-Json) }
}
$alice = New-Client
$bob = New-Client
$guest = New-Client
try {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0,8)
    $aliceEmail = "alice-$suffix@example.com"
    $bobEmail = "bob-$suffix@example.com"
    $aliceToken = (Call-Api $alice GET '/api/auth/csrf' $null '' 200 '1. CSRF 발급').token
    $null = Call-Api $alice POST '/api/auth/signup' @{email=$aliceEmail; password='DemoPass123!'; nickname='Alice'} $aliceToken 201 '2. 회원 가입'
    $null = Call-Api $alice POST '/api/auth/login' @{email=$aliceEmail; password='DemoPass123!'} $aliceToken 200 '3. 로그인'
    $aliceToken = (Call-Api $alice GET '/api/auth/csrf' $null '' 200 '4. 로그인 후 CSRF 재발급').token
    $post = Call-Api $alice POST '/api/posts' @{title='Spring 첫 글'; content='회원과 게시판 API를 만들었습니다.'} $aliceToken 201 '5. 게시글 작성'
    $comment = Call-Api $alice POST "/api/posts/$($post.id)/comments" @{content='첫 번째 댓글입니다.'} $aliceToken 201 '6. 댓글 작성'
    $null = Call-Api $guest GET '/api/posts?page=0&size=10' $null '' 200 '7. 비로그인 목록 조회'
    $null = Call-Api $guest GET "/api/posts/$($post.id)/comments" $null '' 200 '8. 비로그인 댓글 조회'
    $null = Call-Api $guest POST '/api/posts' @{title='비로그인'; content='실패 요청'} '' 401 '9. 비로그인 작성 401'
    $bobToken = (Call-Api $bob GET '/api/auth/csrf' $null '' 200 '10. 다른 회원 CSRF 발급').token
    $null = Call-Api $bob POST '/api/auth/signup' @{email=$bobEmail; password='DemoPass123!'; nickname='Bob'} $bobToken 201 '11. 다른 회원 가입'
    $null = Call-Api $bob POST '/api/auth/login' @{email=$bobEmail; password='DemoPass123!'} $bobToken 200 '12. 다른 회원 로그인'
    $bobToken = (Call-Api $bob GET '/api/auth/csrf' $null '' 200 '13. 다른 회원 CSRF 재발급').token
    $null = Call-Api $bob PUT "/api/posts/$($post.id)" @{title='수정 시도'; content='실패 요청'} $bobToken 403 '14. 남의 게시글 수정 403'
    $null = Call-Api $bob DELETE "/api/comments/$($comment.id)" $null $bobToken 403 '15. 남의 댓글 삭제 403'
    $null = Call-Api $guest GET '/api/posts/999999999' $null '' 404 '16. 없는 게시글 404'
    $null = Call-Api $alice POST '/api/posts' @{title=''; content='실패 요청'} $aliceToken 400 '17. 잘못된 입력 400'
    $null = Call-Api $alice DELETE "/api/posts/$($post.id)" $null $aliceToken 204 '18. 작성자가 게시글과 댓글 삭제'
    $null = Call-Api $guest GET "/api/posts/$($post.id)/comments" $null '' 404 '19. 삭제된 글의 댓글 조회 404'
    if ($OutputPath) { [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($OutputPath), $transcript.ToString(), [System.Text.UTF8Encoding]::new($false)) }
} finally { $alice.Dispose(); $bob.Dispose(); $guest.Dispose() }
