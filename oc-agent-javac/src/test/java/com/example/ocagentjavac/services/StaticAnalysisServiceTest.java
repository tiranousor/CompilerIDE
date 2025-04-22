package com.example.ocagentjavac.services;

import com.example.ocagentjavac.dto.DiagnosticResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StaticAnalysisServiceTest {
    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(StaticAnalysisServiceTest.class.getName());

    private final StaticAnalysisService staticAnalysisService = new StaticAnalysisService();

    @Test
    void testCorrectCodeNoViolations() {
        String code = """
            package com.example;
            import java.util.*;
            public class TestClass {
                public void testMethod() {
                    List<String> list = new ArrayList<>();
                    list.add("Hello");
                }
            }""";

        List<DiagnosticResult> diagnostics = staticAnalysisService.analyzeJavaCode("TestClass", code);
    }

    @Test
    void testCodeWithUnconditionalIfAndUnusedField() {
        String code = """
        package com.example;
        public class TestClass {
            private int a;  // Должно вызывать предупреждение
            public void testMethod() {
                if (true) {  // Должно вызывать предупреждение
                    System.out.println("Always true");
                }
            }
        }""";

        List<DiagnosticResult> diagnostics = staticAnalysisService.analyzeJavaCode("TestClass", code);
    }



    @Test
    void testCodeWithCommonViolation() {
        String code = """
            package com.example;
            public class TestClass {
                public void testMethod() {
                    try {} catch (Exception e) {}
                }
            }""";

        List<DiagnosticResult> diagnostics = staticAnalysisService.analyzeJavaCode("TestClass", code);
    }

    private void logDiagnostics(String testCase, List<DiagnosticResult> diagnostics) {
        logger.info("=== Анализ: " + testCase + " ===");
        diagnostics.forEach(d -> logger.info(
                String.format("Нарушение: %s\nФайл: %s\nСтрока: %d, Колонка: %d\n",
                        d.getMessage(), d.getFile(), d.getLine(), d.getColumn())
        ));
    }

}


