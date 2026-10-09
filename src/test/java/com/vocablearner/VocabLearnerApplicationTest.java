package com.vocablearner;

import com.vocablearner.model.NewVocabItem;
import com.vocablearner.model.VocabExtractionResult;
import com.vocablearner.service.VocabExtractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class VocabLearnerApplicationTest {

    // Directorio raiz de processed/, error/ y output/ para todos los tests de esta clase.
    @TempDir
    static Path root;

    @DynamicPropertySource
    static void rootDir(DynamicPropertyRegistry registry) {
        registry.add("vocab.root-dir", () -> root.toString());
    }

    // Nunca se llama a Claude en los tests: el doble devuelve un resultado fijo.
    @MockitoBean
    VocabExtractionService vocabExtractionService;

    @Autowired
    VocabLearnerApplication app;

    private static final String SRT = """
            1
            00:00:01,000 --> 00:00:02,000
            Engineer, waste disposal.

            """;

    @Test
    void siElSrtNoExisteTerminaConErrorYNoMueveNada() throws Exception {
        Path inexistente = root.resolve("no_existe.srt");
        Path lexicon = root.resolve("lexicon_aislamiento.json");

        assertThrows(IllegalArgumentException.class,
                () -> app.run("process", inexistente.toString(), lexicon.toString()));

        assertFalse(Files.exists(root.resolve("processed").resolve("no_existe.srt")));
        assertFalse(Files.exists(root.resolve("error").resolve("no_existe.srt")));
    }

    @Test
    void procesoCorrectoMueveSrtAInputYGuardaResultadoEnOutput() throws Exception {
        Path srt = root.resolve("exito.srt");
        Files.writeString(srt, SRT, StandardCharsets.UTF_8);
        Path lexicon = root.resolve("lexicon_exito.json");

        when(vocabExtractionService.extract(anySet(), anyMap(), anyString()))
                .thenReturn(new VocabExtractionResult(
                        List.of(new NewVocabItem("disposal", "disposal", "word",
                                "eliminación", "Engineer, waste disposal.")),
                        List.of()));

        app.run("process", srt.toString(), lexicon.toString());

        assertFalse(Files.exists(srt));
        assertTrue(Files.exists(root.resolve("processed").resolve("exito.srt")));
        assertTrue(Files.exists(root.resolve("output").resolve("exito_vocab.json")));
    }
}
