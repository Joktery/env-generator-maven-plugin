package ru.joktery.demo;

import java.util.Optional;

public class DemoApp {

    // must be picked up by the Java source scanner
    private static final String API_KEY = System.getenv("JAVA_SCANNED_API_KEY");

    // lowercase names from getenv are accepted as is
    private static final String REGION = System.getenv("awsRegion");

    // system property with a default
    private static final String TIMEOUT = System.getProperty("java.scanned.timeout", "5000");

    // @Value-style placeholder (no Spring here, just the annotation shape the scanner looks for)
    private String featureFlag = valueOf("${FEATURE_FLAG_ENABLED:false}");

    // comments must be ignored
    // System.getenv("COMMENTED_ENV_VAR")
    /* @Value("${ALSO_COMMENTED:1}") */

    private static String valueOf(String s) {
        return s;
    }

    public static void main(String[] args) {
        System.out.println(Optional.ofNullable(API_KEY).orElse("<unset>"));
    }
}
