package com.dailyrupi.prreview.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class PrReviewCommandTest {

    private final StringWriter out = new StringWriter();

    private int run(String... args) {
        CommandLine cli = new CommandLine(new PrReviewCommand());
        cli.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        cli.setOut(new PrintWriter(out));
        return cli.execute(args);
    }

    @Test
    void versionComesFromTheBuild() {
        assertThat(run("--version")).isZero();
        // An unfiltered placeholder or a missing file would show up as "@project.version@" or "unknown".
        assertThat(out.toString()).startsWith("pr-review ").doesNotContain("@").doesNotContain("unknown");
    }

    @Test
    void noArgumentsPrintsUsage() {
        assertThat(run()).isZero();
        assertThat(out.toString()).contains("Usage: pr-review");
    }
}
