package com.example.board;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts", indexes = @Index(name = "idx_post_created", columnList = "createdAt,id"))
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 200) String title;
    @Column(nullable = false, length = 20000) String content;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) Member author;
    @Column(nullable = false, updatable = false) Instant createdAt = Instant.now();
    @Column(nullable = false) Instant updatedAt = createdAt;
    protected Post() {}
    Post(String title, String content, Member author) { this.title = title; this.content = content; this.author = author; }
    void update(String title, String content) { this.title = title; this.content = content; this.updatedAt = Instant.now(); }
}
