package com.example.board;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy strategy;
    private final CsrfTokenRepository csrf;
    AuthController(AuthService service, AuthenticationManager manager, SecurityContextRepository contexts,
                   SessionAuthenticationStrategy strategy, CsrfTokenRepository csrf) {
        this.service = service; this.manager = manager; this.contexts = contexts; this.strategy = strategy; this.csrf = csrf;
    }
    @GetMapping("/csrf")
    Dto.CsrfView csrf(CsrfToken token) { return new Dto.CsrfView(token.getHeaderName(),token.getToken()); }
    @PostMapping("/signup")
    ResponseEntity<Dto.MemberView> signup(@Valid @RequestBody Dto.Signup input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.signup(input));
    }
    @PostMapping("/login")
    Dto.MemberView login(@Valid @RequestBody Dto.Login input, HttpServletRequest req, HttpServletResponse res) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400,"INVALID_INPUT","Password must be at most 72 UTF-8 bytes");
        var auth = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(),input.password()));
        strategy.onAuthentication(auth,req,res);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context,req,res);
        return service.me(auth.getName());
    }
    @GetMapping("/me") Dto.MemberView me(Authentication auth) { return service.me(auth.getName()); }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
        csrf.saveToken(null,req,res);
        new SecurityContextLogoutHandler().logout(req,res,auth);
        return ResponseEntity.noContent().build();
    }
}
