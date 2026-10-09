package com.vocablearner.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnkiExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportaDosPalabrasComoDosLineasSeparadasPorTabuladores() throws Exception {
        Path vocabJson = tempDir.resolve("prueba_vocab.json");
        Files.writeString(vocabJson, """
                {
                  "sourceSrt" : "prueba.srt",
                  "vocabulary" : [ {
                    "lemma" : "disposal",
                    "surfaceForm" : "disposal",
                    "type" : "word",
                    "translation" : "eliminación (de residuos)",
                    "example" : "Engineer, waste disposal."
                  }, {
                    "lemma" : "misery",
                    "surfaceForm" : "miseries",
                    "type" : "word",
                    "translation" : "miseria, desdicha",
                    "example" : "The music or the misery?"
                  } ]
                }
                """, StandardCharsets.UTF_8);
        Path salida = tempDir.resolve("prueba_anki_vocab.txt");

        new AnkiExporter().export(vocabJson, salida);

        List<String> lineas = Files.readAllLines(salida, StandardCharsets.UTF_8);
        assertEquals(2, lineas.size(), "Debe haber exactamente 2 lineas");
        assertEquals("disposal\teliminación (de residuos)\tEngineer, waste disposal.", lineas.get(0));
        assertEquals("misery\tmiseria, desdicha\tThe music or the misery?", lineas.get(1));
    }
}
