package com.dailyrupi.prreview.tools;

/**
 * One SpotBugs/FindSecBugs bug instance. {@code sourcePath} is relative to the source root
 * (for example com/dailyrupi/expense/ScratchSearch.java). Priority 1 is high, 2 normal, 3 low.
 */
public record SastIssue(
        String type,
        String category,
        int priority,
        int rank,
        Integer cweId,
        String className,
        String methodName,
        String methodSignature,
        String sourcePath,
        Integer startLine,
        Integer endLine,
        String shortMessage,
        String longMessage) {

    /** Fully qualified method with its JVM descriptor: stable across line shifts, used for fingerprints. */
    public String methodKey() {
        return methodName == null ? className : className + "." + methodName + methodSignature;
    }

    /** "search(String)": the method as a reader would write it. */
    public String readableMethod() {
        return methodName == null ? null : methodName + "(" + JvmDescriptors.readableParameters(methodSignature) + ")";
    }
}
