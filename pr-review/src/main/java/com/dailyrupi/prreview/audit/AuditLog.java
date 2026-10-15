package com.dailyrupi.prreview.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.dailyrupi.prreview.support.Json;

/**
 * Append-only JSON Lines record of what an analysis did and decided (spec §7.2). It holds identifiers, statuses
 * and hashes only: never source code, prompts or secrets.
 */
public final class AuditLog {

    public enum Event {
        ANALYSIS_STARTED, CHECK_RESULT, FINDING_RECORDED, READINESS_EVALUATED, ANALYSIS_FINISHED
    }

    private final Path file;
    private final Clock clock;

    public AuditLog(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    public synchronized void record(Event event, String analysisId, Map<String, ?> data) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("at", clock.instant().toString());
        line.put("event", event.name());
        line.put("analysisId", analysisId);
        line.putAll(data);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, Json.compact().writeValueAsString(line) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
