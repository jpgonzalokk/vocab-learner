package com.vocablearner.model;

import java.util.List;

/**
 * Fichero *_vocab.json que genera el comando "process".
 *
 * @param sourceSrt  ruta del SRT del que salio el vocabulario
 * @param vocabulary palabras y phrasal verbs nuevos
 */
public record VocabFile(
        String sourceSrt,
        List<NewVocabItem> vocabulary
) {
}
