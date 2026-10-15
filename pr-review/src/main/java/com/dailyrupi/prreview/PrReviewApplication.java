package com.dailyrupi.prreview;

import com.dailyrupi.prreview.cli.PrReviewCommand;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/** Runs the picocli command tree inside a Spring context, then exits with the command's exit code. */
@SpringBootApplication
public class PrReviewApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final PrReviewCommand command;
    private int exitCode;

    public PrReviewApplication(IFactory factory, PrReviewCommand command) {
        this.factory = factory;
        this.command = command;
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(command, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(PrReviewApplication.class, args)));
    }
}
