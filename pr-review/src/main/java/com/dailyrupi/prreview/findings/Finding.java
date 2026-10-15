package com.dailyrupi.prreview.findings;

import java.util.ArrayList;
import java.util.List;

/**
 * One canonical finding (spec §8.1). Immutable; use {@link #toBuilder()} to derive a changed copy.
 * Fields the current phase does not fill yet (verifier results, status history) arrive with later phases.
 */
public record Finding(
        String findingId,
        String fingerprint,
        Category category,
        Severity severity,
        Kind kind,
        String title,
        String file,
        Integer lineStart,
        Integer lineEnd,
        String enclosingMethod,
        String codeSnippet,
        String trigger,
        String rationale,
        String recommendation,
        List<String> requirementIds,
        List<FindingSource> sources,
        EvidenceLevel evidenceLevel,
        FindingStatus status,
        String firstSeenSha,
        String lastEvaluatedSha) {

    public enum Kind {
        /** The reviewer asserts the code is wrong. */
        DEFECT,
        /** It may be wrong under a stated condition, or no requirement covers it. */
        RISK
    }

    public Finding {
        requirementIds = requirementIds == null ? List.of() : List.copyOf(requirementIds);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.findingId = findingId;
        b.fingerprint = fingerprint;
        b.category = category;
        b.severity = severity;
        b.kind = kind;
        b.title = title;
        b.file = file;
        b.lineStart = lineStart;
        b.lineEnd = lineEnd;
        b.enclosingMethod = enclosingMethod;
        b.codeSnippet = codeSnippet;
        b.trigger = trigger;
        b.rationale = rationale;
        b.recommendation = recommendation;
        b.requirementIds = new ArrayList<>(requirementIds);
        b.sources = new ArrayList<>(sources);
        b.evidenceLevel = evidenceLevel;
        b.status = status;
        b.firstSeenSha = firstSeenSha;
        b.lastEvaluatedSha = lastEvaluatedSha;
        return b;
    }

    /** "file:12-14", "file:12" or just "file", for messages. */
    public String location() {
        if (file == null) {
            return "(no file)";
        }
        if (lineStart == null) {
            return file;
        }
        return lineEnd == null || lineEnd.equals(lineStart) ? file + ":" + lineStart : file + ":" + lineStart + "-" + lineEnd;
    }

    public static final class Builder {

        private String findingId;
        private String fingerprint;
        private Category category;
        private Severity severity;
        private Kind kind = Kind.DEFECT;
        private String title;
        private String file;
        private Integer lineStart;
        private Integer lineEnd;
        private String enclosingMethod;
        private String codeSnippet;
        private String trigger;
        private String rationale;
        private String recommendation;
        private List<String> requirementIds = new ArrayList<>();
        private List<FindingSource> sources = new ArrayList<>();
        private EvidenceLevel evidenceLevel;
        private FindingStatus status = FindingStatus.DETECTED;
        private String firstSeenSha;
        private String lastEvaluatedSha;

        private Builder() {
        }

        public Builder findingId(String value) { findingId = value; return this; }
        public Builder fingerprint(String value) { fingerprint = value; return this; }
        public Builder category(Category value) { category = value; return this; }
        public Builder severity(Severity value) { severity = value; return this; }
        public Builder kind(Kind value) { kind = value; return this; }
        public Builder title(String value) { title = value; return this; }
        public Builder file(String value) { file = value; return this; }
        public Builder lines(Integer start, Integer end) { lineStart = start; lineEnd = end; return this; }
        public Builder enclosingMethod(String value) { enclosingMethod = value; return this; }
        public Builder codeSnippet(String value) { codeSnippet = value; return this; }
        public Builder trigger(String value) { trigger = value; return this; }
        public Builder rationale(String value) { rationale = value; return this; }
        public Builder recommendation(String value) { recommendation = value; return this; }
        public Builder requirementId(String value) { requirementIds.add(value); return this; }
        public Builder source(FindingSource value) { sources.add(value); return this; }
        public Builder evidenceLevel(EvidenceLevel value) { evidenceLevel = value; return this; }
        public Builder status(FindingStatus value) { status = value; return this; }
        public Builder firstSeenSha(String value) { firstSeenSha = value; return this; }
        public Builder lastEvaluatedSha(String value) { lastEvaluatedSha = value; return this; }

        public Finding build() {
            if (category == null || severity == null || title == null) {
                throw new IllegalStateException("A finding needs a category, a severity and a title");
            }
            return new Finding(findingId, fingerprint, category, severity, kind, title, file, lineStart, lineEnd,
                    enclosingMethod, codeSnippet, trigger, rationale, recommendation, requirementIds, sources,
                    evidenceLevel, status, firstSeenSha, lastEvaluatedSha);
        }
    }
}
