package com.vocablearner.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sustituye a {@code .call().entity(...)}: con Spring AI 2.0 + Anthropic la
 * respuesta puede llegar repartida en varias "generations" (la primera a
 * veces vacia), y entity() solo lee la primera. Aqui se junta el texto de
 * todas antes de convertirlo.
 */
final class StructuredOutput {

    private StructuredOutput() {
    }

    static <T> T call(ChatClient chatClient, String system, String user, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);

        ChatResponse response = chatClient.prompt()
                .system(system)
                .user(user + "\n\n" + converter.getFormat())
                .call()
                .chatResponse();

        String text = response == null ? "" : response.getResults().stream()
                .map(g -> g.getOutput().getText())
                .filter(Objects::nonNull)
                .filter(t -> !t.isBlank())
                .collect(Collectors.joining());

        if (text.isBlank()) {
            throw new IllegalStateException("Claude devolvio una respuesta vacia: "
                    + (response == null ? "null" : response.getMetadata()));
        }
        return converter.convert(text);
    }
}
