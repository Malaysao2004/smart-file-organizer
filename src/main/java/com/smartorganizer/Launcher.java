package com.smartorganizer;

/** Non-Application entry point so the shaded JAR can start JavaFX from the classpath. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}