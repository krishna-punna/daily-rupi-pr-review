package com.dailyrupi.prreview.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.dailyrupi.prreview.audit.AuditLog;
import com.dailyrupi.prreview.findings.Category;
import com.dailyrupi.prreview.findings.Finding;
import com.dailyrupi.prreview.findings.FindingStatus;
import com.dailyrupi.prreview.findings.Severity;
import com.dailyrupi.prreview.publish.ReviewReport;
import com.dailyrupi.prreview.readiness.AiStatus;
import com.dailyrupi.prreview.readiness.ReadinessReport;
import com.dailyrupi.prreview.readiness.ReadinessStatus;
import com.dailyrupi.prreview.support.Json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStateStoreTest {

    @TempDir
    Path dir;

    private static ReviewReport analysis(String headSha, Instant at, ReadinessStatus status) {
        Finding finding = Finding.builder().findingId("F-001").fingerprint("sha1:x").category(Category.SECURITY_OTHER)
                .severity(Severity.MEDIUM).title("t").status(FindingStatus.CONFIRMED).build();
        return new ReviewReport(1, "an-" + headSha, "repo", "local-feature/x", "t", "main", "b".repeat(40), "feature/x",
                headSha, "c".repeat(40), at, at, "1.0.0", Map.of(), null, AiStatus.SKIPPED, null, List.of(), List.of(),
                new ReviewReport.Coverage(0, 0, null, null), List.of(finding),
                new ReadinessReport(status, List.of(), List.of(), List.of(), 1, List.of("R7"), "1.0.0", at));
    }

    @Test
    void analysesAccumulatePerCommitAndReanalysingACommitReplacesIt() throws IOException {
        FileStateStore store = new FileStateStore(dir);
        Instant t1 = Instant.parse("2026-10-15T06:00:00Z");

        store.save(analysis("1".repeat(40), t1, ReadinessStatus.NOT_READY));
        store.save(analysis("2".repeat(40), t1.plusSeconds(60), ReadinessStatus.NOT_READY));
        Path file = store.save(analysis("2".repeat(40), t1.plusSeconds(120), ReadinessStatus.READY_FOR_HUMAN_APPROVAL));

        FileStateStore.PrState state = store.load("local-feature/x").orElseThrow();
        assertThat(file.getFileName().toString()).isEqualTo("pr-local-feature_x.json");
        assertThat(state.analyses()).extracting(ReviewReport::headSha).containsExactly("1".repeat(40), "2".repeat(40));
        assertThat(state.latest().orElseThrow().readiness().status()).isEqualTo(ReadinessStatus.READY_FOR_HUMAN_APPROVAL);
        assertThat(state.latest().orElseThrow().findings().get(0).category()).isEqualTo(Category.SECURITY_OTHER);
    }

    @Test
    void unknownPrHasNoState() throws IOException {
        assertThat(new FileStateStore(dir).load("nothing")).isEmpty();
    }

    @Test
    void auditLogAppendsOneJsonObjectPerLine() throws IOException {
        Path file = dir.resolve("audit/pr-1.jsonl");
        AuditLog audit = new AuditLog(file, Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC));

        audit.record(AuditLog.Event.ANALYSIS_STARTED, "an-1", Map.of("headSha", "abc"));
        audit.record(AuditLog.Event.READINESS_EVALUATED, "an-1", Map.of("status", "NOT_READY", "rulesFired", List.of("R2")));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(Json.compact().readTree(lines.get(1)).get("rulesFired").get(0).asText()).isEqualTo("R2");
        assertThat(lines.get(0)).startsWith("{\"at\":\"2026-10-15T06:00:00Z\",\"event\":\"ANALYSIS_STARTED\"");
    }
}
