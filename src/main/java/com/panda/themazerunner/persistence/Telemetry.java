package com.panda.themazerunner.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Bounded asynchronous CSV writer. Actor IDs are round-local integers, never player identities. */
public final class Telemetry implements AutoCloseable {
    private final ThreadPoolExecutor worker;
    private final Path file;
    private final Logger logger;
    private static final String HEADER =
            "utc,round,mode,map,profile,second,actor,class,faction,event,value,detail\n";
    private final AtomicLong dropped = new AtomicLong();

    public Telemetry(Path directory, Logger logger) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("events.csv");
        this.logger = logger;
        if (!Files.exists(file)) Files.writeString(file, HEADER, StandardOpenOption.CREATE_NEW);
        worker =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(512),
                        r -> {
                            Thread t = new Thread(r, "tmr-telemetry");
                            t.setDaemon(true);
                            return t;
                        },
                        (r, e) -> dropped.incrementAndGet());
    }

    public long dropped() {
        return dropped.get();
    }

    private static String cell(Object value) {
        return "\""
                + String.valueOf(value).replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ')
                + "\"";
    }

    public void record(
            String round,
            String mode,
            String map,
            String profile,
            long second,
            int actor,
            String job,
            String faction,
            String event,
            double value,
            String detail) {
        String row =
                String.join(
                                ",",
                                cell(java.time.Instant.now()),
                                cell(round),
                                cell(mode),
                                cell(map),
                                cell(profile),
                                cell(second),
                                cell(actor),
                                cell(job),
                                cell(faction),
                                cell(event),
                                cell(value),
                                cell(detail))
                        + "\n";
        worker.execute(
                () -> {
                    try {
                        rotate();
                        Files.writeString(file, row, StandardOpenOption.APPEND);
                    } catch (IOException error) {
                        dropped.incrementAndGet();
                        logger.warning("Telemetry write failed: " + error.getMessage());
                    }
                });
    }

    private void rotate() throws IOException {
        if (Files.size(file) < 16L * 1024 * 1024) return;
        // Bound retention to 128 MiB plus the current segment. Export before rotation if needed.
        Files.deleteIfExists(file.resolveSibling("events.8.csv"));
        for (int i = 7; i >= 1; i--) {
            Path older = file.resolveSibling("events." + i + ".csv");
            if (Files.exists(older))
                Files.move(
                        older,
                        file.resolveSibling("events." + (i + 1) + ".csv"),
                        StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(file, file.resolveSibling("events.1.csv"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(file, HEADER, StandardOpenOption.CREATE_NEW);
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException error) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
