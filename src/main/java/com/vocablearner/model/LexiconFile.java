package com.vocablearner.model;

import java.util.List;

/**
 * Forma de fichero JSON del lexico: {"words": ["a", "able", ...]}.
 * Se usa tanto para el lexico acumulado (lexicon.json) como para la
 * respuesta de Claude al generar el lexico inicial de 2000 palabras.
 */
public record LexiconFile(List<String> words) {
}
