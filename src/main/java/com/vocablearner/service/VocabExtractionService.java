package com.vocablearner.service;

import com.vocablearner.model.NewVocabItem;
import com.vocablearner.model.VocabExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VocabExtractionService {

    private static final Logger log = LoggerFactory.getLogger(VocabExtractionService.class);

    // El guion se procesa por trozos: de una sola vez la respuesta (una
    // entrada JSON por palabra nueva) supera max-tokens y llega cortada.
    private static final int MAX_CHUNK_CHARS = 12_000;
    private static final int MAX_CANDIDATES_PER_CHUNK = 120;

    private static final AnthropicCacheOptions CACHE_SYSTEM = AnthropicCacheOptions.builder()
            .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
            .build();

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

        // El lexico va en el system prompt, que es identico en todas las
        // llamadas de esta pelicula: asi se cachea y los trozos 2..N lo leen
        // de la cache en vez de pagarlo entero cada vez.
        String system = SYSTEM_PROMPT + """

                LEXICON (palabras ya conocidas, una por linea):
                %s
                """.formatted(String.join("\n", lexicon));
        List<Chunk> chunks = split(candidates, fullText);

        // Se deduplica por lema: un mismo lema puede salir en varios trozos
        // (sobre todo phrasal verbs, que no estan repartidos de antemano).
        Map<String, NewVocabItem> words = new LinkedHashMap<>();
        Map<String, NewVocabItem> phrasalVerbs = new LinkedHashMap<>();

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            log.info("  Trozo {}/{}: {} candidatas, {} caracteres",
                    i + 1, chunks.size(), chunk.candidates().size(), chunk.text().length());

            VocabExtractionResult partial = extractChunk(system, chunk);
            addAll(words, partial.newWords());
            addAll(phrasalVerbs, partial.newPhrasalVerbs());
        }

        return new VocabExtractionResult(
                new ArrayList<>(words.values()),
                new ArrayList<>(phrasalVerbs.values()));
    }

    private VocabExtractionResult extractChunk(String system, Chunk chunk) {
        String candidatesBlock = chunk.candidates().entrySet().stream()
                .map(e -> e.getKey() + " -> " + e.getValue())
                .collect(Collectors.joining("\n"));

        String userMessage = """
                CANDIDATE_WORDS (palabra -> frase de ejemplo):
                %s

                FULL_TEXT (para detectar phrasal verbs en contexto):
                %s
                """.formatted(candidatesBlock, chunk.text());

        return StructuredOutput.call(chatClient, system,
                userMessage, VocabExtractionResult.class,
                AnthropicChatOptions.builder().cacheOptions(CACHE_SYSTEM));
    }

    /**
     * Parte el guion en trozos consecutivos de lineas. Cada candidata va al
     * trozo donde esta su frase de ejemplo (su primera aparicion), asi Claude
     * ve el contexto real. Se corta el trozo cuando supera el tamano de texto
     * o el numero de candidatas, para que la respuesta quepa en max-tokens.
     */
    private static List<Chunk> split(Map<String, String> candidates, String fullText) {
        // frase de ejemplo -> candidatas cuya primera aparicion es esa linea
        Map<String, List<String>> candidatesByLine = new LinkedHashMap<>();
        candidates.forEach((word, line) ->
                candidatesByLine.computeIfAbsent(line, k -> new ArrayList<>()).add(word));

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Map<String, String> chunkCandidates = new LinkedHashMap<>();

        for (String line : fullText.split("\n")) {
            text.append(line).append('\n');
            List<String> words = candidatesByLine.remove(line.strip());
            if (words != null) {
                words.forEach(w -> chunkCandidates.put(w, candidates.get(w)));
            }
            if (text.length() >= MAX_CHUNK_CHARS
                    || chunkCandidates.size() >= MAX_CANDIDATES_PER_CHUNK) {
                chunks.add(new Chunk(new LinkedHashMap<>(chunkCandidates), text.toString()));
                text.setLength(0);
                chunkCandidates.clear();
            }
        }
        // Candidatas cuya linea no se encontro tal cual (no deberia pasar)
        candidatesByLine.values().forEach(ws ->
                ws.forEach(w -> chunkCandidates.put(w, candidates.get(w))));
        if (!text.isEmpty() || !chunkCandidates.isEmpty()) {
            chunks.add(new Chunk(new LinkedHashMap<>(chunkCandidates), text.toString()));
        }
        return chunks;
    }

    private static void addAll(Map<String, NewVocabItem> target, List<NewVocabItem> items) {
        if (items == null) {
            return;
        }
        for (NewVocabItem item : items) {
            if (item != null && item.lemma() != null) {
                target.putIfAbsent(item.lemma().toLowerCase().strip(), item);
            }
        }
    }

    private record Chunk(Map<String, String> candidates, String text) {
    }
}
