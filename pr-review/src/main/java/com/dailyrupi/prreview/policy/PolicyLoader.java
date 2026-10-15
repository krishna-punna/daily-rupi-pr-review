package com.dailyrupi.prreview.policy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Loads review-policy.yml. Every key must be present and no unknown key is allowed, so a typo cannot silently
 * weaken the policy (a misspelt key shows up as both unknown and missing).
 */
public final class PolicyLoader {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    public ReviewPolicy load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Policy file not found: " + file);
        }
        try (InputStream in = Files.newInputStream(file)) {
            return yaml.readValue(in, ReviewPolicy.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new IOException("Invalid policy file " + file + ": " + e.getMessage(), e);
        }
    }
}
