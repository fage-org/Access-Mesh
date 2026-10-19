package cn.ac.fage.accessmesh.testsupport;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Cross-process test slot; the channel and lock remain reachable until this JVM exits. */
public final class TestForkSlot {
    private static FileChannel channel;
    private static FileLock lock;
    private static final int NUMBER = acquire();

    private TestForkSlot() {}

    public static int number() {
        return NUMBER;
    }

    private static int acquire() {
        String explicit = System.getProperty("it.forkNumber");
        if (explicit != null && !explicit.isBlank()) {
            final int value;
            try {
                value = Integer.parseInt(explicit.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("it.forkNumber must be an integer from 1 to 4", e);
            }
            if (value < 1 || value > 4 || !tryAcquire(value)) {
                throw new IllegalStateException("Test fork slot must be free and in 1..4: " + value);
            }
            return value;
        }
        for (int slot = 1; slot <= 4; slot++) {
            if (tryAcquire(slot)) return slot;
        }
        throw new IllegalStateException("All test fork slots 1..4 are occupied; wait for the other build");
    }

    private static boolean tryAcquire(int slot) {
        Path directory = Path.of(System.getProperty("user.home"), ".accessmesh");
        try {
            Files.createDirectories(directory);
            FileChannel candidate = FileChannel.open(directory.resolve("it-slot-" + slot + ".lck"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            try {
                FileLock acquired = candidate.tryLock();
                if (acquired != null) {
                    channel = candidate;
                    lock = acquired;
                    return true;
                }
            } catch (IOException | RuntimeException e) {
                candidate.close();
                throw e;
            }
            candidate.close();
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot acquire test fork slot " + slot, e);
        }
    }
}
