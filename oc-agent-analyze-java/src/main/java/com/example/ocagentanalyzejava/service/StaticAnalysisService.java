// src/main/java/com/example/analyzeworker/service/StaticAnalysisService.java
package com.example.ocagentanalyzejava.service;

import com.example.ocagentanalyzejava.dto.DiagnosticResult;
import lombok.RequiredArgsConstructor;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaticAnalysisService {

    private final JdtSyntaxChecker jdt;
    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisService.class);

    private static String norm(String p) {
        return p == null ? "" : p.replace('\\', '/').replaceFirst("^/+", "");
    }

    public List<DiagnosticResult> analyze(Map<String, String> sources,
                                          String current) {

        /* ---------- JDT ---------- */
        List<DiagnosticResult> diags = new ArrayList<>(jdt.check(sources));

        // ⬇︎  фильтруем СРАЗУ
        if (current != null && !current.isBlank())
            diags.removeIf(d -> !current.equals(d.getFile()));

        if (!diags.isEmpty())          // ←‑‑ если JDT нашёл ошибки – сразу уходим
            return diags;

        /* ---------- PMD ---------- */
        PMDConfiguration cfg = new PMDConfiguration();
        cfg.setDefaultLanguageVersion(new JavaLanguageModule().getVersion("21"));
        cfg.setMinimumPriority(RulePriority.MEDIUM);

        try (PmdAnalysis a = PmdAnalysis.create(cfg)) {
            RuleSetLoader loader = a.newRuleSetLoader();
            RuleSet rules = loader.loadFromResource("rulesets/java/quickstart.xml");
            a.addRuleSet(rules);

        /* в PMD всё равно добавляем ВСЕ файлы –
           правила могут сравнивать разные классы */
            sources.forEach((path, code) -> {
                if (code == null || code.isBlank()) return;
                a.files().addSourceFile(FileId.fromPath(Paths.get(path)), code);
            });

            Report rep = a.performAnalysisAndCollectReport();
            for (RuleViolation v : rep.getViolations()) {

                String cur = norm(current);
                String violPath = norm(v.getFileId().getOriginalPath());   // <‑‑ главное
                if (!cur.isBlank() && !cur.equals(violPath)) {
                    continue;          // показываем только варнинги текущего файла
                }

                DiagnosticResult d = new DiagnosticResult();
                d.setMessage(v.getDescription());
                d.setFile(violPath);                     // путь совпадает с frontend
                d.setLine(v.getBeginLine());
                d.setColumn(v.getBeginColumn());
                d.setEndColumn(v.getEndColumn() + 1);
                d.setSeverity(1);                        // warning
                diags.add(d);
            }
        } catch (Exception ex) {
            DiagnosticResult d = new DiagnosticResult();
            d.setMessage("PMD error: " + ex.getMessage());
            d.setFile(current);
            diags.add(d);
        }
        return diags;
    }
}
