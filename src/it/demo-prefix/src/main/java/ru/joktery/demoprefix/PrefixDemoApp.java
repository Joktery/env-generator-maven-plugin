package ru.joktery.demoprefix;

import java.util.Optional;

public class PrefixDemoApp {

    // getenv names get the prefix like any other, lowercase included
    private static final String API_KEY = System.getenv("JAVA_SCANNED_API_KEY");
    private static final String REGION = System.getenv("awsRegion");

    // already prefixed in the code — must not get a second MYAPP_
    private static final String TOKEN = System.getenv("MYAPP_SHARED_TOKEN");

    public static void main(String[] args) {
        System.out.println(Optional.ofNullable(API_KEY).orElse("<unset>"));
    }
}
