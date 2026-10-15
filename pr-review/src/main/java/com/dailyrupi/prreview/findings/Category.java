package com.dailyrupi.prreview.findings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The fixed finding taxonomy (spec §8.1). Serialised as its label, for example "SECURITY/SQL_INJECTION". */
public enum Category {

    SECURITY_SQL_INJECTION("SECURITY/SQL_INJECTION"),
    SECURITY_ACCESS_CONTROL("SECURITY/ACCESS_CONTROL"),
    SECURITY_OTHER("SECURITY/OTHER"),
    CORRECTNESS_NULL_DEREFERENCE("CORRECTNESS/NULL_DEREFERENCE"),
    CORRECTNESS_EXCEPTION_HANDLING("CORRECTNESS/EXCEPTION_HANDLING"),
    CORRECTNESS_OTHER("CORRECTNESS/OTHER"),
    BUSINESS_RULE_MISSING_VALIDATION("BUSINESS_RULE/MISSING_VALIDATION"),
    BUSINESS_RULE_OTHER("BUSINESS_RULE/OTHER"),
    API_COMPATIBILITY_BREAKING_CHANGE("API_COMPATIBILITY/BREAKING_CHANGE"),
    TEST_MISSING_OR_INADEQUATE("TEST/MISSING_OR_INADEQUATE"),
    TRANSACTION_CONCURRENCY("TRANSACTION_CONCURRENCY"),
    PERFORMANCE("PERFORMANCE"),
    MAINTAINABILITY("MAINTAINABILITY"),
    PROMPT_INJECTION("PROMPT_INJECTION");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static Category fromLabel(String label) {
        for (Category category : values()) {
            if (category.label.equals(label)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown finding category: " + label);
    }

    @Override
    public String toString() {
        return label;
    }
}
