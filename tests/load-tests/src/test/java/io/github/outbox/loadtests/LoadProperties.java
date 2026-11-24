package io.github.outbox.loadtests;

/**
 * System properties a run is configured with, each falling back to its default when not set.
 */
final class LoadProperties {

    private LoadProperties() {
    }

    static String stringProperty(String name, String defaultValue) {
        return System.getProperty(name, defaultValue);
    }

    static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    static double doubleProperty(String name, double defaultValue) {
        return Double.parseDouble(System.getProperty(name, String.valueOf(defaultValue)));
    }
}
