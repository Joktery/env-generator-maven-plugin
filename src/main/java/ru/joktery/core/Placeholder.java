package ru.joktery.core;

/**
 * A single env placeholder occurrence: {@code ${NAME}} or {@code ${NAME:defaultValue}}.
 * {@code defaultValue} is {@code null} when the placeholder has no (usable) default.
 */
public final class Placeholder {

    private final String name;
    private final String defaultValue;

    public Placeholder(String name, String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return name;
    }

    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return "${" + name + (defaultValue == null ? "" : ":" + defaultValue) + "}";
    }
}
