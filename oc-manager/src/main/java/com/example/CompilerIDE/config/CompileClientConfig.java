package com.example.CompilerIDE.config;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.exception.CompilationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class CompileClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(CompileClientConfig.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    public class CustomFeignErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            if (response.status() >= 400) {
                try {
                    String body = response.body().asInputStream().readAllBytes() != null
                            ? new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8).trim()
                            : "";
                    logger.error("Feign Error Response Body: {}", body);

                    try {
                        // Попытка распарсить ответ как JSON
                        Map<String, Object> errorMap = objectMapper.readValue(body, Map.class);

                        // Проверяем наличие stderr
                        if (errorMap.containsKey("stderr")) {
                            // Извлечение первой ошибки
                            Object stderrObj = errorMap.get("stderr");
                            if (stderrObj instanceof Iterable) {
                                Iterable<?> stderrList = (Iterable<?>) stderrObj;
                                Map<String, Object> firstError = null;
                                for (Object obj : stderrList) {
                                    if (obj instanceof Map) {
                                        firstError = (Map<String, Object>) obj;
                                        break;
                                    }
                                }
                                if (firstError != null) {
                                    return new CompilationException("Компиляция завершилась с ошибками.", firstError);
                                }
                            }
                        }

                        // Если stderr отсутствует или имеет неправильный формат
                        return new CompilationException("Компиляция завершилась с ошибками.");
                    } catch (IOException jsonEx) {
                        // Если ответ не JSON, предполагаем, что это простое текстовое сообщение об ошибке
                        Map<String, Object> errorMap = Map.of("message", body);
                        return new CompilationException("Компиляция завершилась с ошибками.", errorMap);
                    }
                } catch (IOException e) {
                    logger.error("Ошибка при чтении тела ответа ошибки Feign: ", e);
                    return new Exception("Ошибка при компиляции кода.");
                }
            }
            // Если статус < 400, используем дефолтный декодер
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }
}
