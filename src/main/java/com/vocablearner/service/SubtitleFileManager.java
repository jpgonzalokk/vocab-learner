package com.vocablearner.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gestiona los directorios de trabajo (processed/, error/, output/) y mueve cada
 * .srt segun el resultado del procesamiento. La raiz se configura con
 * la propiedad vocab.root-dir (por defecto, el directorio actual).
 */
@Service
public class SubtitleFileManager {

    private final Path root;

    @Autowired
    public SubtitleFileManager(@Value("${vocab.root-dir:.}") String root) {
        this(Path.of(root));
    }

    public SubtitleFileManager(Path root) {
        this.root = root;
    }

    /** Comprueba que el .srt existe y es un fichero regular. No mueve nada. */
    public void validateSrt(Path srt) {
        if (!Files.isRegularFile(srt)) {
            throw new IllegalArgumentException("No existe el fichero .srt: " + srt);
        }
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(processedDir());
        Files.createDirectories(errorDir());
        Files.createDirectories(outputDir());
    }

    public Path processedDir() {
        return root.resolve("processed");
    }

    public Path errorDir() {
        return root.resolve("error");
    }

    public Path outputDir() {
        return root.resolve("output");
    }

    /** Mueve el .srt a processed/ sobreescribiendo cualquier fichero previo. Devuelve la nueva ruta. */
    public Path moveToProcessed(Path srt) throws IOException {
        return move(srt, processedDir());
    }

    /** Mueve el .srt a error/ sobreescribiendo cualquier fichero previo. Devuelve la nueva ruta. */
    public Path moveToError(Path srt) throws IOException {
        return move(srt, errorDir());
    }

    private Path move(Path srt, Path directorio) throws IOException {
        Path destino = directorio.resolve(srt.getFileName());
        Files.move(srt, destino, StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }
}
