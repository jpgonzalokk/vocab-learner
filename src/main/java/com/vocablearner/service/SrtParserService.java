package com.vocablearner.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Convierte un fichero .srt en texto plano: quita indices de secuencia,
 * timestamps y etiquetas de formato (<i>, <b>, {\an8}, ...).
 */
@Service
public class SrtParserService {

    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern TIMESTAMP = Pattern.compile("-->");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BRACE_TAG = Pattern.compile("\\{[^}]+\\}");

    public String toPlainText(Path srtPath) throws IOException {
        List<String> lines = Files.readAllLines(srtPath, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (DIGITS_ONLY.matcher(line).matches()) {
                continue;
            }
            if (TIMESTAMP.matcher(line).find()) {
                continue;
            }
            line = HTML_TAG.matcher(line).replaceAll("");
            line = BRACE_TAG.matcher(line).replaceAll("");
            line = line.strip();
            if (!line.isEmpty()) {
                out.append(line).append('\n');
            }
        }

        return out.toString();
    }
}
