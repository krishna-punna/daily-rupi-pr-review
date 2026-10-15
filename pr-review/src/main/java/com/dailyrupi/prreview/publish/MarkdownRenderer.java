package com.dailyrupi.prreview.publish;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.dailyrupi.prreview.checks.CheckResult;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingSource;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.readiness.ReadinessReport;
import com.dailyrupi.prreview.readiness.ReadinessStatus;

/** Renders a review as Markdown: the local review-report.md now, the PR summary comment in Phase 5. */
public final class MarkdownRenderer {

    public String render(ReviewReport report) {
        ReadinessReport readiness = report.readiness();
        StringBuilder md = new StringBuilder();
        md.append("## PR review: ").append(badge(readiness.status())).append("\n\n");
        md.append("**").append(escape(report.title())).append("** · head `").append(report.shortHeadSha())
                .append("` · policy ").append(report.policyVersion())
                .append(" · AI review: ").append(report.aiStatus().name().toLowerCase(Locale.ROOT)).append("\n\n");

        section(md, "Blocking", readiness.blockingReasons());
        section(md, "Needs a human decision", readiness.uncertainties().stream()
                .filter(u -> !u.startsWith("AI review did not run") && !u.startsWith("Approvals are unknown")
                        && !u.startsWith("No coverage report"))
                .toList());
        section(md, "Warnings", readiness.warnings());
        if (readiness.requiredHumanApprovalsRemaining() > 0) {
            md.append("**Human approval:** ").append(readiness.requiredHumanApprovalsRemaining())
                    .append(" more approving review required before merge.\n\n");
        }

        md.append("### Checks\n\n| Check | Result | Detail |\n|---|---|---|\n");
        for (CheckResult check : report.checks()) {
            md.append("| `").append(check.name()).append("` | ").append(checkIcon(check.status())).append(' ')
                    .append(check.status()).append(" | ").append(escape(check.detail() == null ? "" : check.detail()))
                    .append(" |\n");
        }
        md.append('\n');

        List<Finding> active = report.findings().stream().filter(f -> f.status() != FindingStatus.PRE_EXISTING).toList();
        List<Finding> preExisting = report.findings().stream().filter(f -> f.status() == FindingStatus.PRE_EXISTING).toList();
        md.append("### Findings\n\n");
        if (active.isEmpty()) {
            md.append("None introduced by this change.\n\n");
        } else {
            findingTable(md, active);
        }
        if (!preExisting.isEmpty()) {
            md.append("<details><summary>Already present before this change (").append(preExisting.size())
                    .append("), not blocking</summary>\n\n");
            findingTable(md, preExisting);
            md.append("</details>\n\n");
        }

        ReviewReport.Coverage coverage = report.diffCoverage();
        md.append("### Diff coverage\n\n");
        if (coverage.ratio() != null) {
            md.append(String.format(Locale.ROOT, "%.0f%% (%d of %d changed lines with code are executed by tests)",
                    coverage.ratio() * 100, coverage.coveredLines(), coverage.coverableLines()));
        } else {
            md.append(coverage.note() == null ? "Not measured." : escape(coverage.note()) + ".");
        }
        md.append("\n\n");

        List<String> context = readiness.uncertainties().stream()
                .filter(u -> u.startsWith("AI review did not run") || u.startsWith("Approvals are unknown")).toList();
        if (!context.isEmpty() || report.aiStatusReason() != null) {
            md.append("<sub>");
            if (report.aiStatusReason() != null) {
                md.append(escape(report.aiStatusReason())).append(". ");
            }
            md.append(context.stream().filter(u -> !u.startsWith("AI review did not run")).map(MarkdownRenderer::escape)
                    .collect(Collectors.joining(". ")));
            md.append("</sub>\n\n");
        }

        md.append("---\n<sub>Analysis `").append(report.analysisId()).append("` of `").append(report.shortHeadSha())
                .append("` against merge base `").append(shortSha(report.diffBaseSha())).append("` · policy `")
                .append(report.policyVersion()).append("` · rules fired: ").append(String.join(", ", readiness.rulesFired()))
                .append(" · ").append(readiness.evaluatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                .append(". The AI only advises; checks, rules and human approval decide.</sub>\n");
        return md.toString();
    }

    /** Plain ASCII summary for the console (Windows consoles often cannot show the Markdown's symbols). */
    public String consoleSummary(ReviewReport report) {
        ReadinessReport readiness = report.readiness();
        StringBuilder out = new StringBuilder();
        out.append("Readiness: ").append(readiness.status()).append("  (rules fired: ")
                .append(String.join(", ", readiness.rulesFired())).append(")\n");
        readiness.blockingReasons().forEach(r -> out.append("  BLOCKING  ").append(r).append('\n'));
        readiness.warnings().forEach(w -> out.append("  WARNING   ").append(w).append('\n'));
        report.checks().forEach(c -> out.append("  CHECK     ").append(c.name()).append(": ").append(c.status())
                .append(c.detail() == null ? "" : " - " + c.detail()).append('\n'));
        out.append("  FINDINGS  ").append(report.findings().size()).append(" (")
                .append(report.findings().stream().filter(f -> f.status() == FindingStatus.PRE_EXISTING).count())
                .append(" pre-existing)\n");
        return out.toString();
    }

    private static void findingTable(StringBuilder md, List<Finding> findings) {
        md.append("| ID | Status | Severity | Category | Finding | Location | Evidence |\n|---|---|---|---|---|---|---|\n");
        for (Finding f : findings) {
            md.append("| ").append(f.findingId()).append(" | ").append(f.status()).append(" | ").append(f.severity())
                    .append(" | ").append(f.category()).append(" | ").append(escape(f.title()))
                    .append(f.requirementIds().isEmpty() ? "" : " (" + String.join(", ", f.requirementIds()) + ")")
                    .append(" | `").append(escape(f.location())).append("` | ").append(escape(evidence(f))).append(" |\n");
        }
        md.append('\n');
    }

    private static String evidence(Finding finding) {
        return finding.sources().stream().map(MarkdownRenderer::source).collect(Collectors.joining("; "));
    }

    private static String source(FindingSource s) {
        String what = s.tool() == null ? s.type().name() : s.tool();
        if (s.ruleId() != null) {
            what += " " + s.ruleId();
        }
        return s.reference() == null ? what : what + " (" + s.reference() + ")";
    }

    private static void section(StringBuilder md, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        md.append("### ").append(title).append("\n\n");
        items.forEach(item -> md.append("- ").append(escape(item)).append('\n'));
        md.append('\n');
    }

    private static String badge(ReadinessStatus status) {
        return switch (status) {
            case NOT_READY -> "⛔ NOT READY";
            case NEEDS_INVESTIGATION -> "🔎 NEEDS INVESTIGATION";
            case READY_FOR_HUMAN_APPROVAL -> "✅ READY FOR HUMAN APPROVAL";
            case ELIGIBLE_FOR_MERGE -> "🟢 ELIGIBLE FOR MERGE";
        };
    }

    private static String checkIcon(CheckResult.Status status) {
        return switch (status) {
            case PASSED -> "✅";
            case FAILED -> "❌";
            case MISSING, ERROR -> "⚠️";
            case RUNNING -> "⏳";
        };
    }

    /** Keeps table cells on one line and stops text from closing a cell or starting markup. */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ").replace("\n", " ").replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shortSha(String sha) {
        return sha == null ? "?" : sha.substring(0, Math.min(7, sha.length()));
    }
}
