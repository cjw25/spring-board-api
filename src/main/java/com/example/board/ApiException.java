package com.example.board;

public class ApiException extends RuntimeException {
    final int status;
    final String code;
    ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    static ApiException missing(String entity) { return new ApiException(404, "NOT_FOUND", entity + " not found"); }
    static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "Only the author may modify this resource"); }
}
