package com.dailyrupi.prreview.tools;

/** One test case from a Surefire XML report. {@code message} is the failure or error message, if any. */
public record TestCaseResult(String className, String name, Outcome outcome, String failureType, String message) {

    public enum Outcome {
        PASSED, FAILED, ERROR, SKIPPED
    }

    public boolean failed() {
        return outcome == Outcome.FAILED || outcome == Outcome.ERROR;
    }

    /** "ExpenseAndStatusTest.expenseCanBeAddedEditedListedAndDeleted", for messages. */
    public String displayName() {
        String simpleClass = className == null ? "" : className.substring(className.lastIndexOf('.') + 1);
        return simpleClass.isEmpty() ? name : simpleClass + "." + name;
    }
}
