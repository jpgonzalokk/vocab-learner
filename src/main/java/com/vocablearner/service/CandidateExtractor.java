package com.vocablearner.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae, del texto de un guion, las palabras que NO aparecen literalmente
 * en el lexico. Es un filtro barato (sin LLM): la comparacion fina por lema
 * (running -> run, etc.) la hace Claude despues, esto solo evita gastar
 * tokens en palabras exactamente ya conocidas.
 */
@Service
public class CandidateExtractor {

    private static final Pattern WORD = Pattern.compile("[A-Za-z']+");

    // Pronombres, articulos, etc. muy frecuentes: no aportan valor de
    // aprendizaje y ademas contaminarian la deteccion de phrasal verbs si
    // se tratasen como candidatas sueltas.
    private static final Set<String> TRIVIAL_TOKENS = Set.of(
            "a", "an", "the", "i", "you", "he", "she", "it", "we", "they",
            "me", "him", "her", "us", "them", "my", "your", "his", "its",
            "our", "their", "mine", "yours", "ours", "theirs"
    );

    /**
     * @return mapa palabra_candidata -> frase de ejemplo donde aparece
     *         (la primera vez que se encuentra), en orden de aparicion.
     */
    public Map<String, String> extract(String text, Set<String> lexicon) {
        Map<String, String> candidates = new LinkedHashMap<>();

        for (String line : text.split("\n")) {
            Matcher matcher = WORD.matcher(line);
            while (matcher.find()) {
                String word = matcher.group().toLowerCase().replaceAll("^'+|'+$", "");
                if (word.isEmpty() || TRIVIAL_TOKENS.contains(word)) {
                    continue;
                }
                if (lexicon.contains(word)) {
                    continue;
                }
                candidates.putIfAbsent(word, line.strip());
            }
        }

        return candidates;
    }
}
