package com.example.board;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Dto {
    private Dto() {}
    public record Signup(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(min = 8, max = 72) String password,
                         @NotBlank @Size(max = 40) String nickname) {}
    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 72) String password) {}
    public record PostInput(@NotBlank @Size(max = 200) String title,
                            @NotBlank @Size(max = 20000) String content) {}
    public record CommentInput(@NotBlank @Size(max = 2000) String content) {}
    public record MemberView(Long id, String email, String nickname, Instant createdAt) {
        static MemberView of(Member m) { return new MemberView(m.id, m.email, m.nickname, m.createdAt); }
    }
    public record PostView(Long id, String title, String content, Long authorId, String authorNickname,
                           Instant createdAt, Instant updatedAt, long commentCount) {}
    public record CommentView(Long id, Long postId, String content, Long authorId, String authorNickname,
                              Instant createdAt, Instant updatedAt) {
        static CommentView of(Comment c) { return new CommentView(c.id, c.post.id, c.content, c.author.id,
                c.author.nickname, c.createdAt, c.updatedAt); }
    }
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record CsrfView(String headerName, String token) {}
    public record ErrorView(Instant timestamp, int status, String code, String message, String path) {}
}
