package com.example.ocagentjavac.services;

import com.example.ocagentjavac.dto.DiagnosticResult;

import javax.tools.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class InMemoryJavaCompiler {

    // Класс для хранения исходного кода в памяти
    public static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        public JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    public List<DiagnosticResult> compile(String className, String code) {
        List<DiagnosticResult> diagnosticsList = new ArrayList<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        JavaFileObject compilationUnit = new JavaSourceFromString(className, code);
        Iterable<? extends JavaFileObject> compilationUnits = List.of(compilationUnit);
        List<String> options = List.of("-proc:none", "-Xlint:-options");
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        task.call();

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            DiagnosticResult diag = new DiagnosticResult();
            diag.setMessage(diagnostic.getMessage(null));
            diag.setFile(diagnostic.getSource() != null ? diagnostic.getSource().getName() : "Unknown");
            diag.setLine(diagnostic.getLineNumber());
            diag.setColumn(diagnostic.getColumnNumber());
            diagnosticsList.add(diag);
        }
        return diagnosticsList;
    }
}