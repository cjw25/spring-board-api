package com.example.board;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 254) String email;
    @Column(nullable = false) String passwordHash;
    @Column(nullable = false, length = 40) String nickname;
    @Column(nullable = false, updatable = false) Instant createdAt = Instant.now();
    protected Member() {}
    Member(String email, String passwordHash, String nickname) {
        this.email = email; this.passwordHash = passwordHash; this.nickname = nickname;
    }
}
