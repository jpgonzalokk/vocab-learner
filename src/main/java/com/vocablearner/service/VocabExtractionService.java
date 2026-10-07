package com.vocablearner.service;

import com.vocablearner.model.VocabExtractionResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VocabExtractionService {

    // Si el guion es muy largo, se trunca para mantener la llamada rapida y
    // barata; la deteccion de vocabulario individual no se ve afectada
    // porque usa la lista de candidatas completa, solo la deteccion de
    // phrasal verbs al final de peliculas muy largas podria perderse un poco.
    private static final int MAX_TEXT_CHARS = 60_000;

    private static final String SYSTEM_PROMPT = """
            Eres un asistente especializado en linguistica inglesa para un \
            estudiante hispanohablante que quiere aprender vocabulario nuevo \
            viendo peliculas en version original.

            Se te da:
            1. LEXICON: la lista de palabras (en su forma base/lema) que el \
            usuario YA conoce.
            2. CANDIDATE_WORDS: palabras individuales del guion que no \
            aparecen literalmente en LEXICON, cada una con una frase de \
            ejemplo donde aparece.
            3. FULL_TEXT: el texto completo del guion, para que puedas \
            detectar expresiones de varias palabras (phrasal verbs / verbos \
            preposicionales) en su contexto real.

            TU TAREA:

            A) Para cada palabra de CANDIDATE_WORDS, calcula su lema (forma \
            base): infinitivo para verbos, singular para sustantivos, forma \
            positiva para adjetivos. Compara ese lema contra LEXICON \
            (tratando como iguales formas verbales regulares, plurales \
            regulares, -ing, -ed, comparativos, etc.).
            - Si el lema YA esta en LEXICON -> la palabra NO es nueva, \
            ignorala.
            - Si el lema NO esta en LEXICON -> es una palabra nueva de \
            verdad.

            B) Revisa FULL_TEXT buscando verbos preposicionales / phrasal \
            verbs (verbo + particula con significado propio, ej. "give up", \
            "look after", "figure out", "run into"). Tratalos SIEMPRE como \
            una unidad de vocabulario nueva e independiente, incluso si el \
            verbo y la particula por separado ya estan en LEXICON, salvo \
            que el phrasal verb exacto ya este en LEXICON.

            C) Para cada palabra o phrasal verb nuevo, da su traduccion al \
            espanol mas adecuada AL CONTEXTO de la frase donde aparece.

            D) No incluyas nombres propios, interjecciones sin significado \
            (oh, uh, hmm) ni palabras mal transcritas / ruido de OCR.

            Devuelve la lista de palabras nuevas en newWords y los phrasal \
            verbs nuevos en newPhrasalVerbs. Para cada elemento indica: \
            lemma (forma base en ingles), surfaceForm (forma exacta en la \
            que aparecio), type ("word" o "phrasal_verb"), translation \
            (traduccion al espanol segun contexto) y example (la frase del \
            guion donde aparece).
            """;

    private final ChatClient chatClient;

    public VocabExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public VocabExtractionResult extract(Set<String> lexicon,
                                          Map<String, String> candidates,
                                          String fullText) {

        String truncatedText = fullText.length() > MAX_TEXT_CHARS
                ? fullText.substring(0, MAX_TEXT_CHARS)
                : fullText;

        String candidatesBlock = candidates.entrySet().stream()
                .map(e -> e.getKey() + " -> " + e.getValue())
                .collect(Collectors.joining("\n"));

        String lexiconBlock = String.join("\n", lexicon);

        String userMessage = """
                LEXICON (palabras ya conocidas, una por linea):
                %s

                CANDIDATE_WORDS (palabra -> frase de ejemplo):
                %s

                FULL_TEXT (para detectar phrasal verbs en contexto):
                %s
                """.formatted(lexiconBlock, candidatesBlock, truncatedText);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .entity(VocabExtractionResult.class);
    }
}
