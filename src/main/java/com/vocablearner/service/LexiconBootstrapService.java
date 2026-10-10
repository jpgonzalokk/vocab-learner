package com.vocablearner.service;

import com.vocablearner.model.LexiconFile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class LexiconBootstrapService {

    // En una sola llamada Claude se queda corto (~1500 palabras), asi que la
    // lista se pide por tandas, pasando en cada una las palabras ya obtenidas
    // para que no las repita. Tandas pequenas para que cada llamada sea rapida.
    private static final int TARGET_WORDS = 2000;
    private static final int CHUNK_SIZE = 250;
    private static final int MAX_CALLS = 15;

    private static final String SYSTEM_PROMPT = """
            Eres un linguista experto en ingles como segunda lengua.

            Estas ayudando a construir una lista de las 2000 palabras mas \
            basicas y de mayor frecuencia de uso en ingles general (el \
            vocabulario que cubriria, aproximadamente, un nivel A1-B1: \
            listas de referencia como el General Service List o el Oxford \
            3000 son un buen criterio de referencia, pero no las cites, solo \
            usalas como guia de calidad). La lista se genera por tandas.

            Reglas:
            - Da cada palabra en su forma BASE (lema): verbos en infinitivo \
            sin "to" (ej. "go", no "going"/"goes"/"went"), sustantivos en \
            singular, adjetivos en forma positiva.
            - No repitas palabras, ni dentro de la tanda ni respecto a las \
            palabras ya obtenidas que se te indiquen.
            - No incluyas nombres propios.
            - Incluye tanto palabras de contenido (nombres, verbos, \
            adjetivos, adverbios) como palabras funcionales muy comunes \
            (preposiciones, conjunciones, pronombres, etc.).
            - Elige siempre las palabras mas frecuentes que aun no esten en \
            la lista.

            Devuelve la tanda en el campo "words".
            """;

    private final ChatClient chatClient;

    public LexiconBootstrapService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public LexiconFile generateInitialLexicon() {
        // LinkedHashSet: conserva el orden de frecuencia para recortar al final.
        Set<String> words = new LinkedHashSet<>();

        for (int call = 1; call <= MAX_CALLS && words.size() < TARGET_WORDS; call++) {
            int requested = Math.min(CHUNK_SIZE, TARGET_WORDS - words.size());
            System.out.printf("  Tanda %d: pidiendo %d palabras (%d/%d)...%n",
                    call, requested, words.size(), TARGET_WORDS);

            String user = words.isEmpty()
                    ? "Genera exactamente %d palabras: las %d mas frecuentes del ingles."
                            .formatted(requested, requested)
                    : """
                      Genera exactamente %d palabras NUEVAS: las siguientes mas \
                      frecuentes que no esten en esta lista de palabras ya \
                      obtenidas (%d):
                      %s
                      """.formatted(requested, words.size(), String.join(", ", words));

            LexiconFile chunk = StructuredOutput.call(chatClient, SYSTEM_PROMPT, user, LexiconFile.class);
            if (chunk.words() != null) {
                for (String w : chunk.words()) {
                    String normalized = w.toLowerCase().strip();
                    if (!normalized.isEmpty()) {
                        words.add(normalized);
                    }
                }
            }
        }

        return new LexiconFile(new ArrayList<>(words).subList(0, Math.min(TARGET_WORDS, words.size())));
    }
}
