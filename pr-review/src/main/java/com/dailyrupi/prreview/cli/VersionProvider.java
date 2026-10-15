package com.dailyrupi.prreview.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/** Reads the version Maven wrote into pr-review-version.properties at build time. */
public class VersionProvider implements IVersionProvider {

    static final String RESOURCE = "/pr-review-version.properties";

    @Override
    public String[] getVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        }
        return new String[] {"pr-review " + properties.getProperty("version", "unknown")};
    }
}
