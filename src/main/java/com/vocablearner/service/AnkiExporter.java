package com.vocablearner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocablearner.model.NewVocabItem;
import com.vocablearner.model.VocabFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Convierte un *_vocab.json en un TXT con formato de importacion de Anki:
 * una tarjeta por linea, campos separados por tabulador (lemma, traduccion, ejemplo).
 */
@Service
public class AnkiExporter {

    // Igual que en LexiconService: el ObjectMapper se crea a mano, no se inyecta.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void export(Path vocabJson, Path salida) throws IOException {
        VocabFile file = objectMapper.readValue(vocabJson.toFile(), VocabFile.class);
        Files.writeString(salida, toTsv(file.vocabulary()), StandardCharsets.UTF_8);
    }

    /** Genera el contenido TSV. Los tabuladores y saltos de linea dentro de un campo se sustituyen por espacios. */
    String toTsv(List<NewVocabItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items == null) {
            return "";
        }
        for (NewVocabItem item : items) {
            sb.append(limpiar(item.lemma())).append('\t')
              .append(limpiar(item.translation())).append('\t')
              .append(limpiar(item.example())).append('\n');
        }
        return sb.toString();
    }

    private String limpiar(String campo) {
        return campo == null ? "" : campo.replaceAll("[\\t\\r\\n]+", " ");
    }
}
