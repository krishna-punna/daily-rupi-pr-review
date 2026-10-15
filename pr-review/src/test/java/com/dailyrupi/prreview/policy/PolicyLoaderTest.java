package com.dailyrupi.prreview.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dailyrupi.prreview.findings.Severity;
import com.dailyrupi.prreview.policy.ReviewPolicy.Enforcement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyLoaderTest {

    @Test
    void repositoryPolicyLoadsAndItsChecksMatchTheWorkflow() throws IOException {
        // Maven runs tests from pr-review/, where the real policy file lives.
        ReviewPolicy policy = new PolicyLoader().load(Path.of("review-policy.yml"));

        assertThat(policy.policyVersion()).isEqualTo("1.0.0");
        assertThat(policy.requiredChecks()).containsExactly("backend-tests", "contract-tests", "sast");
        assertThat(policy.blockingSeverity()).isEqualTo(Severity.HIGH);
        assertThat(policy.enforcement()).isEqualTo(Enforcement.ADVISORY);
        assertThat(policy.project().checks()).containsOnlyKeys("backend-tests", "contract-tests", "sast");
        assertThat(policy.project().checks().keySet()).containsExactly("backend-tests", "contract-tests", "sast");
        assertThat(policy.project().checks().get("sast").mavenArgs()).containsExactly("compile", "spotbugs:check");

        String workflow = Files.readString(Path.of("../.github/workflows/pr-review.yml"));
        policy.requiredChecks().forEach(check -> assertThat(workflow).contains("name: " + check));
    }

    @Test
    void misspelledKeyIsRejectedRatherThanIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.yml");
        Files.writeString(file, Files.readString(Path.of("review-policy.yml"))
                .replace("requireAiReview:", "requireAiReveiw:"));

        assertThatThrownBy(() -> new PolicyLoader().load(file)).isInstanceOf(IOException.class)
                .hasMessageContaining("requireAiRev");
    }

    @Test
    void unknownExtraKeyIsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.yml");
        Files.writeString(file, Files.readString(Path.of("review-policy.yml")) + "\nskipSecurityChecks: true\n");

        assertThatThrownBy(() -> new PolicyLoader().load(file)).isInstanceOf(IOException.class)
                .hasMessageContaining("skipSecurityChecks");
    }

    @Test
    void missingFileIsAnError(@TempDir Path dir) {
        assertThatThrownBy(() -> new PolicyLoader().load(dir.resolve("absent.yml"))).isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }
}
