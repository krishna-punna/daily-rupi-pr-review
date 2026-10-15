package com.dailyrupi.prreview.cli;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Top-level command. More subcommands (demo, eval, serve, state) are added phase by phase. */
@Component
@Command(name = "pr-review", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        subcommands = ReviewCommand.class,
        description = "AI pull-request review and merge-readiness engine (proof of concept).")
public class PrReviewCommand implements Runnable {

    @Spec
    CommandSpec spec;

    /** With no subcommand there is nothing to do, so show what is available. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
