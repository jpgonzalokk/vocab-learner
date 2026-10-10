package com.vocablearner.model;

import java.util.List;

/**
 * Resultado de pedirle a Claude que analice un guion: las palabras sueltas
 * nuevas y los phrasal verbs nuevos detectados.
 */
public record VocabExtractionResult(
        List<NewVocabItem> newWords,
        List<NewVocabItem> newPhrasalVerbs
) {
}
