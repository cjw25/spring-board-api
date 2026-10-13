package com.example.board;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Keep container error dispatches in the same shape as controller/filter errors. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<Dto.ErrorView> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number ? number : 404;
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String code = switch (status) {
            case 400 -> "INVALID_INPUT";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        return ResponseEntity.status(status).body(Errors.body(status,code,"Request could not be completed",
                path instanceof String text ? text : request.getRequestURI()));
    }
}
