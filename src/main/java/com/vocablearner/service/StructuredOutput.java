package com.vocablearner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
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

    private static final Logger log = LoggerFactory.getLogger(StructuredOutput.class);

    private StructuredOutput() {
    }

    static <T> T call(ChatClient chatClient, String system, String user, Class<T> type) {
        return call(chatClient, system, user, type, null);
    }

    static <T> T call(ChatClient chatClient, String system, String user, Class<T> type,
                      ChatOptions.Builder<?> options) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(system)
                .user(user + "\n\n" + converter.getFormat());
        if (options != null) {
            request = request.options(options);
        }
        ChatResponse response = request.call().chatResponse();

        if (response != null && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            log.info("    tokens: entrada={}, salida={}, cache leida={}, cache escrita={}",
                    usage.getPromptTokens(), usage.getCompletionTokens(),
                    usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens());
        }

        String text = response == null ? "" : response.getResults().stream()
                .map(g -> g.getOutput().getText())
                .filter(Objects::nonNull)
                .filter(t -> !t.isBlank())
                .collect(Collectors.joining());

        String finishReason = response == null || response.getResult() == null
                ? null
                : response.getResult().getMetadata().getFinishReason();

        if (finishReason != null && finishReason.equalsIgnoreCase("max_tokens")) {
            throw new IllegalStateException("La respuesta de Claude se corto al llegar a "
                    + "max-tokens; reduce el tamano de la peticion: " + response.getMetadata());
        }
        if (text.isBlank()) {
            throw new IllegalStateException("Claude devolvio una respuesta vacia (finishReason="
                    + finishReason + "): " + (response == null ? "null" : response.getMetadata()));
        }
        return converter.convert(text);
    }
}
