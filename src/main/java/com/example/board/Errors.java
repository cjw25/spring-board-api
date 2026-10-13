package com.example.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class Errors {
    static Dto.ErrorView body(int status, String code, String message, String path) {
        return new Dto.ErrorView(Instant.now(), status, code, message, path);
    }
    static void write(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
                      int status, String code, String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), body(status, code, message, req.getRequestURI()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Dto.ErrorView> api(ApiException e, HttpServletRequest r) { return response(e.status, e.code, e.getMessage(), r); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<Dto.ErrorView> invalid(Exception e, HttpServletRequest r) { return response(400,"INVALID_INPUT","Invalid request input",r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Dto.ErrorView> conflict(Exception e, HttpServletRequest r) { return response(400,"INVALID_INPUT","Conflicting data",r); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Dto.ErrorView> unauth(Exception e, HttpServletRequest r) { return response(401,"UNAUTHORIZED","Invalid email or password",r); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Dto.ErrorView> denied(Exception e, HttpServletRequest r) { return response(403,"FORBIDDEN","Access denied",r); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Dto.ErrorView> missing(Exception e, HttpServletRequest r) { return response(404,"NOT_FOUND","Resource not found",r); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Dto.ErrorView> method(Exception e, HttpServletRequest r) { return response(405,"METHOD_NOT_ALLOWED","Method not allowed",r); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Dto.ErrorView> media(Exception e, HttpServletRequest r) { return response(415,"UNSUPPORTED_MEDIA_TYPE","Use application/json",r); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Dto.ErrorView> unexpected(Exception e, HttpServletRequest r) {
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Unexpected request failure", e);
        return response(500,"INTERNAL_ERROR","Unexpected server error",r);
    }
    private ResponseEntity<Dto.ErrorView> response(int status, String code, String message, HttpServletRequest r) {
        return ResponseEntity.status(status).body(body(status,code,message,r.getRequestURI()));
    }
}
