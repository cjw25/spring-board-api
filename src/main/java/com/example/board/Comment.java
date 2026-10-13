package com.example.board;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments", indexes = @Index(name = "idx_comment_post", columnList = "post_id"))
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 2000) String content;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) Member author;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) Post post;
    @Column(nullable = false, updatable = false) Instant createdAt = Instant.now();
    @Column(nullable = false) Instant updatedAt = createdAt;
    protected Comment() {}
    Comment(String content, Member author, Post post) { this.content = content; this.author = author; this.post = post; }
    void update(String content) { this.content = content; this.updatedAt = Instant.now(); }
}
