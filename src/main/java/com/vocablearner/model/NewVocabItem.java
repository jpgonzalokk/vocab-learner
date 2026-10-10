package com.vocablearner.model;

/**
 * Una palabra o phrasal verb nuevo detectado por Claude.
 *
 * @param lemma       forma base (infinitivo / singular), la que se guarda en el lexico
 * @param surfaceForm forma exacta en la que aparecio en el guion
 * @param type        "word" o "phrasal_verb"
 * @param translation traduccion al espanol segun el contexto
 * @param example     frase del guion donde aparece
 */
public record NewVocabItem(
        String lemma,
        String surfaceForm,
        String type,
        String translation,
        String example
) {
}
