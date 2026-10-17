package com.panda.themazerunner.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class AtomicFilesTest {
    @TempDir Path directory;

    @Test
    void committedReplacementHasNoTemporaryPartialFile() throws Exception {
        Path path = directory.resolve("state.yml");
        AtomicFiles.write(path, "before");
        AtomicFiles.write(path, "after");
        assertEquals("after", Files.readString(path));
        assertFalse(Files.exists(directory.resolve("state.yml.tmp")));
    }
}
