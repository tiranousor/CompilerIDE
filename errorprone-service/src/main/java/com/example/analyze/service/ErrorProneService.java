package com.example.analyze.service;
// ErrorProneService.java
import com.example.analyze.dto.DiagnosticResult;
import com.google.errorprone.ErrorProneAnalyzer;
import com.google.errorprone.scanner.ScannerSupplier;
import javax.tools.*;
import java.net.URI;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class ErrorProneService {

    public List<DiagnosticResult> analyze(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagCollector = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagCollector, null, null);

        // 1) создаём виртуальные файлы
        List<JavaFileObject> files = new ArrayList<>();
        sources.forEach((path, code) -> {
            files.add(new SimpleJavaFileObject(
                    URI.create("string:///" + path), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignore) {
                    return code;
                }
            });
        });

        // 2) опции для ErrorProne
        List<String> options = List.of(
                "-Xplugin:ErrorProne",
                "-Xep:DeadException:ERROR"     // включаем одну проверку
        );

        // 3) запуск компиляции
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagCollector, options, null, files
        );
        task.call();

        // 4) собираем результаты
        List<DiagnosticResult> out = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagCollector.getDiagnostics()) {
            DiagnosticResult r = new DiagnosticResult();
            r.setFile(d.getSource().getName().replace("string:/", ""));
            r.setLine((int)d.getLineNumber());
            r.setColumn((int)d.getColumnNumber());
            r.setMessage(d.getMessage(Locale.getDefault()));

            // Пример «авто-фикса» — просто текст, что можно удалить:
            if (d.getCode().contains("DeadException"))
                r.setFix("Удалить неиспользуемый объект на этой строке");

            out.add(r);
        }

        return out;
    }
}
