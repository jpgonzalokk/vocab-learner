package com.vocablearner.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleFileManagerTest {

    @TempDir
    Path root;

    @Test
    void creaLosTresDirectoriosSiNoExisten() throws Exception {
        new SubtitleFileManager(root).ensureDirectories();

        assertTrue(Files.isDirectory(root.resolve("input")));
        assertTrue(Files.isDirectory(root.resolve("error")));
        assertTrue(Files.isDirectory(root.resolve("output")));
    }

    @Test
    void moveToInputMueveElSrtYSobreescribeSiYaExiste() throws Exception {
        SubtitleFileManager manager = new SubtitleFileManager(root);
        manager.ensureDirectories();

        Path srt = root.resolve("pelicula.srt");
        Files.writeString(srt, "nuevo", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("input").resolve("pelicula.srt"), "viejo", StandardCharsets.UTF_8);

        Path destino = manager.moveToInput(srt);

        assertEquals(root.resolve("input").resolve("pelicula.srt"), destino);
        assertFalse(Files.exists(srt));
        assertEquals("nuevo", Files.readString(destino, StandardCharsets.UTF_8));
    }

    @Test
    void moveToErrorMueveElSrtAlDirectorioError() throws Exception {
        SubtitleFileManager manager = new SubtitleFileManager(root);
        manager.ensureDirectories();

        Path srt = root.resolve("roto.srt");
        Files.writeString(srt, "x", StandardCharsets.UTF_8);

        Path destino = manager.moveToError(srt);

        assertEquals(root.resolve("error").resolve("roto.srt"), destino);
        assertFalse(Files.exists(srt));
    }
}
