package com.vocablearner.service;

import com.vocablearner.model.LexiconFile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LexiconBootstrapService {

    private static final String SYSTEM_PROMPT = """
            Eres un linguista experto en ingles como segunda lengua.

            Genera una lista de las 2000 palabras mas basicas y de mayor \
            frecuencia de uso en ingles general (el vocabulario que \
            cubriria, aproximadamente, un nivel A1-B1: listas de \
            referencia como el General Service List o el Oxford 3000 son \
            un buen criterio de referencia, pero no las cites, solo usalas \
            como guia de calidad).

            Reglas:
            - Da cada palabra en su forma BASE (lema): verbos en infinitivo \
            sin "to" (ej. "go", no "going"/"goes"/"went"), sustantivos en \
            singular, adjetivos en forma positiva.
            - No repitas palabras.
            - No incluyas nombres propios.
            - Incluye tanto palabras de contenido (nombres, verbos, \
            adjetivos, adverbios) como palabras funcionales muy comunes \
            (preposiciones, conjunciones, pronombres, etc.) si estan entre \
            las 2000 mas frecuentes.

            Devuelve la lista completa en el campo "words". Debe tener \
            aproximadamente 2000 elementos.
            """;

    private final ChatClient chatClient;

    public LexiconBootstrapService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public LexiconFile generateInitialLexicon() {
        return StructuredOutput.call(chatClient, SYSTEM_PROMPT,
                "Genera la lista ahora.", LexiconFile.class);
    }
}
