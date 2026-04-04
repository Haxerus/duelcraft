package com.haxerus.duelcraft.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeLoader {

    private static boolean loaded = false;

    private NativeLoader() {}

    public static synchronized void load() {
        if (loaded) return;

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String platform;
        String[] libs;

        if (os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"))) {
            platform = "windows-x86_64";
            libs = new String[]{ "ocgcore.dll", "duelcraft_jni.dll" };
        } else if (os.contains("linux") && arch.equals("amd64")) {
            platform = "linux-x86_64";
            libs = new String[]{ "libocgcore.so", "libduelcraft_jni.so" };
        } else {
            throw new UnsatisfiedLinkError(
                    "Unsupported platform: " + os + " " + arch);
        }

        try {
            Path tempDir = Files.createTempDirectory("duelcraft-natives");
            tempDir.toFile().deleteOnExit();

            // Load in order: ocgcore first, then the JNI bridge that depends on it
            for (String lib : libs) {
                String resourcePath = "/natives/" + platform + "/" + lib;
                Path extractedPath = tempDir.resolve(lib);

                try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new UnsatisfiedLinkError(
                                "Native library not found in JAR: " + resourcePath);
                    }
                    Files.copy(in, extractedPath, StandardCopyOption.REPLACE_EXISTING);
                }

                extractedPath.toFile().deleteOnExit();
                System.load(extractedPath.toAbsolutePath().toString());
            }

            loaded = true;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(
                    "Failed to extract native libraries: " + e.getMessage());
        }
    }
}
