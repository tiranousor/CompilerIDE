package com.example.CompilerIDE.exception;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.exception.CompilationException;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Collections;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ClientService clientService;

    @Autowired
    public GlobalExceptionHandler(ClientService clientService) {
        this.clientService = clientService;
    }
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
        Map<String, Object> errorDetails = Map.of(
                "message", "Внутренняя ошибка сервера: " + ex.getMessage(),
                "file", "",
                "line", 0,
                "column", 0
        );
        errorResult.setStderr(Collections.singletonList(errorDetails));
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
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(HttpServletRequest request, Model model, MaxUploadSizeExceededException exc) {
        String uri = request.getRequestURI();
        String[] parts = uri.split("/");
        Long clientId = null;
        try {
            clientId = Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            clientId = 0L;
        }

        Client client = clientService.findOne(clientId);
        if (client == null) {
            client = new Client();
        }
        model.addAttribute("client", client);
        model.addAttribute("error", "Размер файла превышает допустимый лимит (например, 2MB). Пожалуйста, выберите файл меньшего размера.");
        return "editProfile";
    }

}