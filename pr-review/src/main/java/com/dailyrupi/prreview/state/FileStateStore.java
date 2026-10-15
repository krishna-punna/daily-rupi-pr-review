package com.dailyrupi.prreview.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.dailyrupi.prreview.publish.ReviewReport;
import com.dailyrupi.prreview.support.Json;

/**
 * Keeps every analysis of a PR in {@code <dir>/pr-<id>.json}, oldest first. Analysing the same head commit again
 * replaces that analysis rather than adding a second one. Phase 4 builds finding lifecycles on top of this.
 */
public final class FileStateStore {

    public record PrState(String repository, String prId, List<ReviewReport> analyses) {

        public PrState {
            analyses = List.copyOf(analyses);
        }

        public Optional<ReviewReport> latest() {
            return analyses.isEmpty() ? Optional.empty() : Optional.of(analyses.get(analyses.size() - 1));
        }
    }

    private final Path dir;

    public FileStateStore(Path dir) {
        this.dir = dir;
    }

    public Path save(ReviewReport report) throws IOException {
        List<ReviewReport> analyses = new ArrayList<>(load(report.prId()).map(PrState::analyses).orElse(List.of()));
        analyses.removeIf(existing -> existing.headSha().equals(report.headSha()));
        analyses.add(report);
        analyses.sort(Comparator.comparing(ReviewReport::startedAt));

        Files.createDirectories(dir);
        Path file = fileFor(report.prId());
        Path temp = Files.createTempFile(dir, "pr-", ".tmp");
        Json.pretty().writeValue(temp.toFile(), new PrState(report.repository(), report.prId(), analyses));
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    public Optional<PrState> load(String prId) throws IOException {
        Path file = fileFor(prId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(Json.pretty().readValue(file.toFile(), PrState.class));
    }

    private Path fileFor(String prId) {
        return dir.resolve("pr-" + safeName(prId) + ".json");
    }

    /** PR ids from local runs contain branch names; keep only characters that are safe in a file name. */
    public static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
