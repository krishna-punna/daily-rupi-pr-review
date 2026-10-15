package com.dailyrupi.prreview.findings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable identity of an issue across commits (spec §9.3): category, file, the enclosing scope (a method
 * signature, a contract endpoint) and the rule. Line numbers are deliberately left out because they shift.
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    public static String of(Category category, String file, String scope, String ruleKey) {
        String key = String.join("|", category.label(), nullToEmpty(file).replace('\\', '/'), nullToEmpty(scope),
                nullToEmpty(ruleKey));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return "sha1:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is always available in the JDK", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
