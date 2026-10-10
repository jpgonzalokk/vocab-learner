package com.vocablearner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

    // Se crea aqui mismo, sin depender de que Spring lo inyecte: al no usar
    // spring-boot-starter-web, Spring Boot no registra un bean ObjectMapper
    // automaticamente (esa autoconfiguracion requiere una clase de
    // spring-web que esta app no incluye a proposito).
    private final ObjectMapper objectMapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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
