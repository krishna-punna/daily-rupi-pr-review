package com.dailyrupi.prreview.tools;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Real reports produced from backend/ (spec §18, Phase 2 DoD), with machine paths, system properties and
 * logs stripped. "defective" is a scratch branch that concatenates request data into SQL and renames
 * ExpenseResponse.paymentMethodName; "clean" is main.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static Path report(String relative) {
        URL url = Fixtures.class.getResource("/reports/" + relative);
        if (url == null) {
            throw new IllegalArgumentException("No test fixture reports/" + relative);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
