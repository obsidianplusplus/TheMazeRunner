package com.panda.themazerunner.persistence;

import java.io.IOException;
import java.nio.file.*;

public final class AtomicFiles {
    private AtomicFiles() {}

    public static void write(Path destination, String data) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(
                temporary, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try (var channel =
                java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            throw new IOException("Atomic recovery writes unsupported: " + destination, error);
        }
    }
}
