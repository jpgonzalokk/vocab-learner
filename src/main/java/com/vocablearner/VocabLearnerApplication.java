package com.vocablearner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
// Igual que en LexiconService: el ObjectMapper se crea a mano, no se
// inyecta, porque esta app no incluye spring-boot-starter-web.
import com.vocablearner.model.LexiconFile;
import com.vocablearner.model.NewVocabItem;
import com.vocablearner.model.VocabExtractionResult;
import com.vocablearner.service.AnkiExporter;
import com.vocablearner.service.CandidateExtractor;
import com.vocablearner.service.LexiconBootstrapService;
import com.vocablearner.service.LexiconService;
import com.vocablearner.service.SrtParserService;
import com.vocablearner.service.VocabExtractionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uso:
 *
 *   Generar el lexico inicial (solo la primera vez):
 *     java -jar vocab-learner.jar bootstrap [lexicon.json]
 *
 *   Procesar una pelicula:
 *     java -jar vocab-learner.jar process pelicula.srt [lexicon.json] [salida.json]
 *
 *   Exportar un *_vocab.json a formato Anki (.txt, tabulador):
 *     java -jar vocab-learner.jar export pelicula_vocab.json [salida.txt]
 */
@SpringBootApplication
public class VocabLearnerApplication implements CommandLineRunner {

    private final SrtParserService srtParserService;
    private final CandidateExtractor candidateExtractor;
    private final LexiconService lexiconService;
    private final VocabExtractionService vocabExtractionService;
    private final LexiconBootstrapService lexiconBootstrapService;
    private final AnkiExporter ankiExporter;
    private final ObjectMapper objectMapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public VocabLearnerApplication(SrtParserService srtParserService,
                                    CandidateExtractor candidateExtractor,
                                    LexiconService lexiconService,
                                    VocabExtractionService vocabExtractionService,
                                    LexiconBootstrapService lexiconBootstrapService,
                                    AnkiExporter ankiExporter) {
        this.srtParserService = srtParserService;
        this.candidateExtractor = candidateExtractor;
        this.lexiconService = lexiconService;
        this.vocabExtractionService = vocabExtractionService;
        this.lexiconBootstrapService = lexiconBootstrapService;
        this.ankiExporter = ankiExporter;
    }

    public static void main(String[] args) {
        SpringApplication.run(VocabLearnerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "bootstrap" -> runBootstrap(args);
            case "process" -> runProcess(args);
            case "export" -> runExport(args);
            default -> printUsage();
        }
    }

    private void runBootstrap(String[] args) throws IOException {
        Path lexiconPath = Path.of(args.length > 1 ? args[1] : "lexicon.json");

        System.out.println("Generando lexico inicial de 2000 palabras con Claude...");
        LexiconFile generated = lexiconBootstrapService.generateInitialLexicon();

        Set<String> words = new java.util.TreeSet<>();
        for (String w : generated.words()) {
            words.add(w.toLowerCase().strip());
        }

        lexiconService.save(lexiconPath, words);
        System.out.printf("Lexico inicial guardado en %s (%d palabras).%n", lexiconPath, words.size());
    }

    private void runProcess(String[] args) throws IOException {
        if (args.length < 2) {
            printUsage();
            return;
        }

        Path srtPath = Path.of(args[1]);
        Path lexiconPath = Path.of(args.length > 2 ? args[2] : "lexicon.json");
        Path outputPath = Path.of(args.length > 3
                ? args[3]
                : srtPath.getFileName().toString().replaceAll("\\.srt$", "") + "_vocab.json");

        System.out.println("Leyendo " + srtPath + "...");
        String text = srtParserService.toPlainText(srtPath);

        System.out.println("Cargando lexico...");
        Set<String> lexicon = lexiconService.load(lexiconPath);
        System.out.println("  " + lexicon.size() + " palabras conocidas.");

        System.out.println("Filtrando candidatas...");
        Map<String, String> candidates = candidateExtractor.extract(text, lexicon);
        System.out.println("  " + candidates.size() + " palabras candidatas a revisar.");

        if (candidates.isEmpty()) {
            System.out.println("No hay palabras candidatas. Nada que hacer.");
            return;
        }

        System.out.println("Consultando a Claude (lematizacion + phrasal verbs + traduccion)...");
        VocabExtractionResult result = vocabExtractionService.extract(lexicon, candidates, text);

        List<NewVocabItem> allNew = new ArrayList<>();
        if (result.newWords() != null) {
            allNew.addAll(result.newWords());
        }
        if (result.newPhrasalVerbs() != null) {
            allNew.addAll(result.newPhrasalVerbs());
        }

        System.out.println("  " + (result.newWords() == null ? 0 : result.newWords().size()) + " palabras nuevas.");
        System.out.println("  " + (result.newPhrasalVerbs() == null ? 0 : result.newPhrasalVerbs().size()) + " phrasal verbs nuevos.");

        objectMapper.writeValue(outputPath.toFile(), Map.of(
                "sourceSrt", srtPath.toString(),
                "vocabulary", allNew
        ));
        System.out.println("Vocabulario nuevo guardado en " + outputPath);

        for (NewVocabItem item : allNew) {
            lexicon.add(item.lemma().toLowerCase().strip());
        }
        lexiconService.save(lexiconPath, lexicon);
        System.out.printf("Lexico actualizado: %d palabras en total (%s).%n", lexicon.size(), lexiconPath);
    }

    private void runExport(String[] args) throws IOException {
        if (args.length < 2) {
            printUsage();
            return;
        }

        Path vocabPath = Path.of(args[1]);
        String nombre = vocabPath.getFileName().toString().replaceAll("(_vocab)?\\.json$", "");
        Path outputPath = Path.of(args.length > 2
                ? args[2]
                : nombre + "anki_vocab.txt");

        System.out.println("Exportando " + vocabPath + " a formato Anki...");
        ankiExporter.export(vocabPath, outputPath);
        System.out.println("Anki guardado en " + outputPath);
    }

    private void printUsage() {
        System.out.println("""
                Uso:
                  bootstrap [lexicon.json]
                      Genera el lexico inicial de 2000 palabras basicas.

                  process <pelicula.srt> [lexicon.json] [salida.json]
                      Procesa una pelicula: detecta vocabulario nuevo,
                      lo traduce y actualiza el lexico.

                  export <pelicula_vocab.json> [salida.txt]
                      Exporta el vocabulario a un TXT para importar en Anki.
                """);
    }
}
