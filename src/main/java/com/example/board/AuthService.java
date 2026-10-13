package com.example.board;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    AuthService(MemberRepository members, PasswordEncoder encoder) { this.members = members; this.encoder = encoder; }
    @Transactional
    public Dto.MemberView signup(Dto.Signup input) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400,"INVALID_INPUT","Password must be at most 72 UTF-8 bytes");
        if (members.existsByEmail(email)) throw new ApiException(400,"DUPLICATE_EMAIL","Email already registered");
        var member = members.saveAndFlush(new Member(email,encoder.encode(input.password()),input.nickname().trim()));
        return Dto.MemberView.of(member);
    }
    @Transactional(readOnly = true)
    public Dto.MemberView me(String email) { return Dto.MemberView.of(members.findByEmail(email).orElseThrow(() -> ApiException.missing("Member"))); }
}
