package com.dailyrupi.prreview.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Groups the review commands. GitHub mode ({@code review github}) arrives in Phase 5. */
@Command(name = "review", mixinStandardHelpOptions = true, subcommands = ReviewLocalCommand.class,
        description = "Review a pull request.")
public class ReviewCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
