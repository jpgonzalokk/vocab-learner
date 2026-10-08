# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Requires Java 17+, Maven, and `ANTHROPIC_API_KEY` in the environment (only for the commands that call Claude).

```bash
mvn clean package                       # builds target/vocab-learner-0.1.0.jar
java -jar target/vocab-learner-0.1.0.jar bootstrap lexicon.json
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt lexicon.json [salida.json]
```

There is no linter configured, and there are no tests yet (`src/test` does not exist). `spring-boot-starter-test` is on the classpath, so a single test can be run with `mvn -Dtest=ClassName test` once one is added.

## Architecture

This is a Spring Boot 4 / Spring AI 2.0 command-line app, not a web app. `application.yml` sets `web-application-type: none`, so `VocabLearnerApplication` (a `CommandLineRunner`) dispatches on `args[0]` to `bootstrap` or `process`. The model is set in `application.yml` (`claude-haiku-5-5`, `max-tokens: 18192`, 5-minute timeout).

`ObjectMapper` is created by hand in the services and the app, not injected, because `spring-boot-starter-web` is deliberately absent and Boot does not register an `ObjectMapper` bean without it.

**The `process` pipeline** (`VocabLearnerApplication.runProcess`):

1. `SrtParserService` strips indices, timestamps, and HTML/brace formatting tags, producing plain text (no LLM).
2. `CandidateExtractor` tokenizes that text and keeps each distinct word not literally in the lexicon, mapped to its first example line (no LLM). This is only a cheap literal filter; lemma matching is delegated to Claude.
3. `VocabExtractionService` sends the candidates to Claude in chunks. The text is split into consecutive line blocks (`MAX_CHUNK_CHARS` = 12,000, `MAX_CANDIDATES_PER_CHUNK` = 120), and each candidate goes into the chunk containing its first example line. The full lexicon is placed in the system prompt, marked with `CACHE_SYSTEM`, so chunks 2..N reuse the prompt cache. Results are deduplicated by lowercase lemma across chunks.
4. `LexiconService` writes the new lemmas back into `lexicon.json` (sorted, saved as `{"words": [...]}`). The output file (`<name>_vocab.json`) is written before the lexicon update.

**The `bootstrap` pipeline:** `LexiconBootstrapService` asks Claude for about 2000 base-form words in batches of 250 (up to 15 calls). Each call passes the words already collected so Claude does not repeat them, and results keep frequency order via `LinkedHashSet`.

**Structured output goes through `StructuredOutput`, not `.entity()`.** With Spring AI 2.0 and Anthropic, a response can arrive split across several generations, and `entity()` reads only the first one. `StructuredOutput.call` concatenates all non-blank generations, throws if `finishReason` is `max_tokens`, and converts the result with `BeanOutputConverter`. Use it for any new LLM call. The chunking in `VocabExtractionService` exists because a single response listing every new word would exceed `max-tokens`.

Model records in `model/` are the JSON contracts with Claude: `VocabExtractionResult` (`newWords`, `newPhrasalVerbs`), `NewVocabItem` (`lemma`, `surfaceForm`, `type`, `translation`, `example`), and `LexiconFile`.

## Notes

- `README.md` is partly out of date. It says the code uses `.call().entity(...)` and that text is truncated at 60,000 characters (`MAX_TEXT_CHARS`). Both are stale: the code uses `StructuredOutput` and chunks the full text instead. Trust the code.
- Code comments, log messages, prompts, and the README are in Spanish; the code identifiers are in English. Keep new prompts and comments consistent with the surrounding code.
- `local/`, `lexicon.json`, `*_vocab.json`, and `.env` are gitignored, so generated output and local data stay out of commits.
