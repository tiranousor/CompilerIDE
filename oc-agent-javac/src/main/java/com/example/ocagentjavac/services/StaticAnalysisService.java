package com.example.ocagentjavac.services;

import com.example.ocagentjavac.dto.DiagnosticResult;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class StaticAnalysisService {
    private static final Logger logger = Logger.getLogger(StaticAnalysisService.class.getName());

    public List<DiagnosticResult> analyzeJavaCode(String className, String code) {
        logger.info("Анализ кода для класса: " + className);
        return runPMDAnalysis(code);
    }

    private List<DiagnosticResult> runPMDAnalysis(String code) {
        List<DiagnosticResult> diagnostics = new ArrayList<>();
        try {
            PMDConfiguration config = new PMDConfiguration();
            config.setDefaultLanguageVersion(new JavaLanguageModule().getVersion("21"));
            config.setMinimumPriority(RulePriority.MEDIUM);

            try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
                RuleSetLoader loader = analysis.newRuleSetLoader();
                RuleSet rules = loader.loadFromResource("rulesets/java/quickstart.xml");
                analysis.addRuleSet(rules);


                analysis.files().addSourceFile(
                        FileId.fromPath(Paths.get("InMemory.java")),
                        code
                );

                Report report = analysis.performAnalysisAndCollectReport();
                logViolations(report.getViolations());

                for (RuleViolation violation : report.getViolations()) {
                    diagnostics.add(createDiagnosticResult(violation));
                }
            }
        } catch (Exception e) {
            logger.warning("Ошибка анализа: " + e.getMessage());
            diagnostics.add(createErrorDiagnosticResult(e));
        }
        return diagnostics;
    }

    private void logViolations(List<RuleViolation> violations) {
        if (violations.isEmpty()) {
            logger.info("Нарушений не обнаружено");
            return;
        }

        // Упрощенный вывод без лишних заголовков
        violations.forEach(v -> {
            logger.info(String.format(
                    "Нарушение: %s\nФайл: %s\nСтрока: %d, Колонка: %d\n",
                    v.getDescription(),
                    v.getFileId().getFileName(),
                    v.getBeginLine(),
                    v.getBeginColumn()
            ));
        });
    }

    private DiagnosticResult createDiagnosticResult(RuleViolation violation) {
        DiagnosticResult diag = new DiagnosticResult();
        diag.setMessage(violation.getDescription());
        diag.setFile(violation.getFileId().getFileName());
        diag.setLine(violation.getBeginLine());
        diag.setColumn(violation.getBeginColumn());
        return diag;
    }

    private DiagnosticResult createErrorDiagnosticResult(Exception e) {
        DiagnosticResult diag = new DiagnosticResult();
        diag.setMessage("PMD Error: " + e.getMessage());
        diag.setFile("InMemory.java");
        diag.setLine(0);
        diag.setColumn(0);
        return diag;
    }
}



