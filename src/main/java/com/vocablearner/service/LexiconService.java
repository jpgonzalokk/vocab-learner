package com.vocablearner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocablearner.model.LexiconFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LexiconService {

    private final ObjectMapper objectMapper;

    public LexiconService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(
                com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
        );
    }

    /** Carga el lexico. Si el fichero no existe, devuelve un set vacio. */
    public Set<String> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashSet<>();
        }
        LexiconFile file = objectMapper.readValue(path.toFile(), LexiconFile.class);
        Set<String> words = new TreeSet<>();
        for (String w : file.words()) {
            words.add(w.toLowerCase().strip());
        }
        return words;
    }

    public void save(Path path, Set<String> lexicon) throws IOException {
        Set<String> sorted = new TreeSet<>(lexicon);
        objectMapper.writeValue(path.toFile(), new LexiconFile(sorted.stream().toList()));
    }
}
