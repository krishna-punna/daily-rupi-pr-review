package com.dailyrupi.prreview.findings;

import java.util.Set;

/** Maps SpotBugs/FindSecBugs rule types onto the finding taxonomy and severities. */
public final class SastRules {

    /** Injection-class rules: user input reaching an interpreter. HIGH at least, whatever the tool's priority. */
    private static final Set<String> INJECTION = Set.of(
            "COMMAND_INJECTION", "LDAP_INJECTION", "XPATH_INJECTION", "SPEL_INJECTION", "EL_INJECTION",
            "OGNL_INJECTION", "SCRIPT_ENGINE_INJECTION", "PATH_TRAVERSAL_IN",
            "PATH_TRAVERSAL_OUT", "XXE_DOCUMENT", "XXE_SAXPARSER", "XXE_XMLREADER", "TEMPLATE_INJECTION_FREEMARKER",
            "TEMPLATE_INJECTION_VELOCITY", "TEMPLATE_INJECTION_PEBBLE");

    private SastRules() {
    }

    public static Category category(String type) {
        if (type != null && (type.startsWith("SQL_INJECTION") || type.equals("HIBERNATE_INJECTION")
                || type.equals("JDO_INJECTION") || type.equals("JPA_INJECTION"))) {
            return Category.SECURITY_SQL_INJECTION;
        }
        return Category.SECURITY_OTHER;
    }

    /**
     * Injection rules are HIGH (CRITICAL at the tool's high priority); anything else follows the tool's
     * priority: 1 is HIGH, 2 MEDIUM, 3 LOW.
     */
    public static Severity severity(String type, int priority) {
        boolean injection = category(type) == Category.SECURITY_SQL_INJECTION || INJECTION.contains(type);
        if (injection) {
            return priority <= 1 ? Severity.CRITICAL : Severity.HIGH;
        }
        return switch (priority) {
            case 1 -> Severity.HIGH;
            case 2 -> Severity.MEDIUM;
            default -> Severity.LOW;
        };
    }

    public static String recommendation(String type) {
        if (category(type) == Category.SECURITY_SQL_INJECTION) {
            return "Pass request data as bind parameters (? placeholders or JPA parameters) instead of concatenating it into SQL.";
        }
        return "See the FindSecBugs description of " + type + " and fix the code it points at.";
    }

    /** Requirement the rule enforces, when the repository has one (see docs/requirements). */
    public static String requirementId(String type) {
        return category(type) == Category.SECURITY_SQL_INJECTION ? "REQ-SEC-001" : null;
    }
}
