package com.example.CompilerIDE.exception;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.exception.CompilationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CompilationException.class)
    public ResponseEntity<CompilationResult> handleCompilationException(CompilationException ex) {
        logger.error("CompilationException: ", ex);

        CompilationResult errorResult = new CompilationResult();
        errorResult.setStdout("");
        errorResult.setStderr(Collections.singletonList(
                Map.of(
                        "message", ex.getMessage(),
                        "file", ex.getFileName(),
                        "line", ex.getLineNumber(),
                        "column", ex.getColumnNumber()
                )
        ));
        errorResult.setReturnCode(1);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResult);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CompilationResult> handleGeneralException(Exception ex) {
        logger.error("Unhandled Exception: ", ex);

        CompilationResult errorResult = new CompilationResult();
        errorResult.setStdout("");
        errorResult.setStderr(Collections.singletonList(
                Map.of(
                        "message", "Внутренняя ошибка сервера: " + ex.getMessage(),
                        "file", "",
                        "line", 0,
                        "column", 0
                )
        ));
        errorResult.setReturnCode(1);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CompilationResult> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        logger.error("Method Not Supported Exception: ", ex);

        CompilationResult errorResult = new CompilationResult();
        errorResult.setStdout("");
        errorResult.setStderr(Collections.singletonList(
                Map.of(
                        "message", "Метод запроса '" + ex.getMethod() + "' не поддерживается для этого эндпоинта.",
                        "file", "",
                        "line", 0,
                        "column", 0
                )
        ));
        errorResult.setReturnCode(1);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResult);
    }

}