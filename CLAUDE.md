# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Requires Java 17+, Maven, and `ANTHROPIC_API_KEY` in the environment (only for the commands that call Claude).

```bash
mvn clean package                       # builds target/vocab-learner-0.1.0.jar
mvn test                                # runs all tests
mvn -Dtest=ClassName test               # runs a single test class
java -jar target/vocab-learner-0.1.0.jar bootstrap lexicon.json
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt [lexicon.json]
java -jar target/vocab-learner-0.1.0.jar export output/pelicula_vocab.json [salida.txt]
```

There is no linter configured. Tests live in `src/test/java/com/vocablearner/` (`VocabLearnerApplicationTest`, `service/SubtitleFileManagerTest`, `service/AnkiExporterTest`). The application tests mock `VocabExtractionService` with `@MockitoBean`, so tests never call Claude.

## Architecture

This is a Spring Boot 4 / Spring AI 2.0 command-line app, not a web app. `application.yml` sets `web-application-type: none`, so `VocabLearnerApplication` (a `CommandLineRunner`) dispatches on `args[0]` to `bootstrap`, `process` or `export`. `main` catches startup/runner exceptions, prints the root cause, and exits with code 1. The model is set in `application.yml` (`claude-haiku-5-5`, `max-tokens: 18192`, 5-minute timeout).

`ObjectMapper` is created by hand in the services and the app, not injected, because `spring-boot-starter-web` is deliberately absent and Boot does not register an `ObjectMapper` bean without it.

**The `process` pipeline** (`VocabLearnerApplication.runProcess`, which delegates the work to `procesar`):

0. `SubtitleFileManager.validateSrt` checks the `.srt` exists before anything else; if not, the run throws `IllegalArgumentException` and no file is moved. `ensureDirectories` then creates `processed/`, `error/` and `output/` under the root, which is the `vocab.root-dir` property (default `.`).
1. `SrtParserService` strips indices, timestamps, and HTML/brace formatting tags, producing plain text (no LLM).
2. `CandidateExtractor` tokenizes that text and keeps each distinct word not literally in the lexicon, mapped to its first example line (no LLM). This is only a cheap literal filter; lemma matching is delegated to Claude.
3. `VocabExtractionService` sends the candidates to Claude in chunks. The text is split into consecutive line blocks (`MAX_CHUNK_CHARS` = 12,000, `MAX_CANDIDATES_PER_CHUNK` = 120), and each candidate goes into the chunk containing its first example line. The full lexicon is placed in the system prompt, marked with `CACHE_SYSTEM`, so chunks 2..N reuse the prompt cache. Results are deduplicated by lowercase lemma across chunks.
4. `LexiconService` writes the new lemmas back into `lexicon.json` (sorted, saved as `{"words": [...]}`). The output file (`output/<name>_vocab.json`, a `VocabFile` with `sourceSrt` and `vocabulary`) is written before the lexicon update. `lexicon.json` stays in the current directory and is not moved.
5. File lifecycle: on success the `.srt` is moved to `processed/`; any exception after validation moves it to `error/` (with the exception suppressed-chained if the move fails) and rethrows. Moves overwrite existing files. The second positional argument is the lexicon, not an output path; the output always goes to `output/`.

**The `export` pipeline** (`VocabLearnerApplication.runExport` → `AnkiExporter`): reads a `*_vocab.json` and writes a UTF-8 TXT with one card per line, `lemma \t translation \t example`. Tabs and newlines inside fields become spaces. No LLM call. Default output is `output/<name>anki_vocab.txt` (the name is `_vocab.json`-stripped, then `anki_vocab.txt` appended); an optional second argument overrides it.

**The `bootstrap` pipeline:** `LexiconBootstrapService` asks Claude for about 2000 base-form words in batches of 250 (up to 15 calls). Each call passes the words already collected so Claude does not repeat them, and results keep frequency order via `LinkedHashSet`.

**Structured output goes through `StructuredOutput`, not `.entity()`.** With Spring AI 2.0 and Anthropic, a response can arrive split across several generations, and `entity()` reads only the first one. `StructuredOutput.call` concatenates all non-blank generations, throws if `finishReason` is `max_tokens`, and converts the result with `BeanOutputConverter`. Use it for any new LLM call. The chunking in `VocabExtractionService` exists because a single response listing every new word would exceed `max-tokens`.

Model records in `model/` are the JSON contracts with Claude: `VocabExtractionResult` (`newWords`, `newPhrasalVerbs`), `NewVocabItem` (`lemma`, `surfaceForm`, `type`, `translation`, `example`), `LexiconFile`, and `VocabFile` (the `*_vocab.json` shape: `sourceSrt`, `vocabulary`).

## Notes

- `README.md` describes the current usage, the work folders and the `export` command. It is kept in sync with the code.
- Code comments, log messages, prompts, and the README are in Spanish; the code identifiers are in English. Keep new prompts and comments consistent with the surrounding code.
- `local/`, `lexicon.json`, `*_vocab.json`, and `.env` are gitignored, so generated output and local data stay out of commits. `processed/`, `error/` and `output/` are NOT gitignored yet, so generated files there can show up as untracked.
