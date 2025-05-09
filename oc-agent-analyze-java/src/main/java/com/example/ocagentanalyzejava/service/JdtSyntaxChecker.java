package com.example.ocagentanalyzejava.service;

import com.example.ocagentanalyzejava.dto.DiagnosticResult;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class JdtSyntaxChecker {

    public List<DiagnosticResult> check(Map<String,String> sources) {

        try {
            Path tmp = Files.createTempDirectory("jdt");
            for (var e : sources.entrySet()) {
                Path p = tmp.resolve(e.getKey());
                Files.createDirectories(p.getParent());
                Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
            }

            String[] files     = sources.keySet().stream()
                    .map(k -> tmp.resolve(k).toString())
                    .toArray(String[]::new);
            String[] encodings = new String[files.length];

            ASTParser p = ASTParser.newParser(AST.JLS21);
            p.setKind(ASTParser.K_COMPILATION_UNIT);
            p.setResolveBindings(true);
            p.setBindingsRecovery(true);
            p.setStatementsRecovery(true);
            p.setEnvironment(new String[0], new String[]{ tmp.toString() },
                    null, true);

            List<DiagnosticResult> out = new ArrayList<>();

            p.createASTs(files, encodings, new String[0],
                    new FileASTRequestor() {
                        @Override
                        public void acceptAST(String file,
                                              org.eclipse.jdt.core.dom.CompilationUnit cu) {
                            for (IProblem pr : cu.getProblems()) {
                                if (!pr.isError()) continue;
                                DiagnosticResult d = new DiagnosticResult();
                                d.setMessage(pr.getMessage());
                                d.setFile(tmp.relativize(Path.of(file))
                                        .toString()
                                        .replace(File.separator,"/"));
                                d.setLine(pr.getSourceLineNumber());
                                int s = pr.getSourceStart(), e = pr.getSourceEnd();
                                d.setColumn   (cu.getColumnNumber(s)+1);
                                d.setEndColumn(cu.getColumnNumber(e)+2);
                                d.setSeverity(2);
                                out.add(d);
                            }
                        }
                    }, null);

            tmp.toFile().deleteOnExit();
            return out;
        } catch (IOException ex) {
            DiagnosticResult d = new DiagnosticResult();
            d.setMessage("JDT I/O error: " + ex.getMessage());
            return List.of(d);
        }
    }
}

