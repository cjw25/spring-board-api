package com.example.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Locale;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(MemberRepository members) {
        return email -> members.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(m -> User.withUsername(m.email).password(m.passwordHash).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Member not found"));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper,
                                      SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        http.securityContext(c -> c.securityContextRepository(contexts))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/posts", "/api/posts/*", "/api/posts/*/comments").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> Errors.write(mapper,req,res,401,"UNAUTHORIZED","Login required"))
                .accessDeniedHandler((req,res,ex) -> {
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
                    int status = anonymous ? 401 : 403;
                    Errors.write(mapper,req,res,status,anonymous ? "UNAUTHORIZED" : "FORBIDDEN",
                            anonymous ? "Login required or CSRF token missing" : "Access denied or CSRF token invalid");
                }))
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable());
        return http.build();
    }
}
