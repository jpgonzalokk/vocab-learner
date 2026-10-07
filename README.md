# vocab-learner (Spring Boot + Spring AI)

Versión Java del sistema de aprendizaje de vocabulario: compara los
subtítulos (.srt) de cada película con tu léxico personal, detecta palabras
y phrasal verbs nuevos, los traduce al español según el contexto, y amplía
tu léxico para que no te los vuelva a marcar como nuevos.

## Requisitos

- Java 17+
- Maven
- Variable de entorno `ANTHROPIC_API_KEY`

```bash
export ANTHROPIC_API_KEY="tu-api-key"
```

## Compilar

```bash
mvn clean package
```

Esto genera `target/vocab-learner-0.1.0.jar`.

## 1. Generar tu léxico inicial (solo la primera vez)

```bash
java -jar target/vocab-learner-0.1.0.jar bootstrap lexicon.json
```

Crea `lexicon.json` con las ~2000 palabras básicas del inglés, en su forma
base (lema).

## 2. Procesar cada película nueva

```bash
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt lexicon.json
```

Esto genera:

- `pelicula_vocab.json` → las palabras y phrasal verbs nuevos de esa
  película, con su traducción y una frase de ejemplo extraída del guion.
- Actualiza `lexicon.json` añadiendo esas palabras nuevas, para que en la
  siguiente película no vuelvan a aparecer como "nuevas".

Puedes indicar un nombre de salida distinto como tercer argumento:

```bash
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt lexicon.json vocab_pelicula.json
```

## Estructura del proyecto

```
src/main/java/com/vocablearner/
├── VocabLearnerApplication.java     # CLI: bootstrap / process
├── model/
│   ├── NewVocabItem.java            # palabra o phrasal verb nuevo
│   ├── VocabExtractionResult.java   # respuesta de Claude (newWords / newPhrasalVerbs)
│   └── LexiconFile.java             # forma JSON del léxico ({"words": [...]})
└── service/
    ├── SrtParserService.java        # .srt -> texto plano (sin LLM)
    ├── CandidateExtractor.java      # filtro rápido de candidatas (sin LLM)
    ├── LexiconService.java          # carga/guarda lexicon.json
    ├── VocabExtractionService.java  # llamada a Claude: lematiza, detecta
    │                                #   phrasal verbs y traduce
    └── LexiconBootstrapService.java # genera el léxico inicial de 2000 palabras
```

## Cómo funciona por dentro

1. **Parseo del .srt** (`SrtParserService`, sin LLM): se limpian timestamps,
   índices y etiquetas de formato, quedando solo el texto hablado.
2. **Filtro rápido** (`CandidateExtractor`, sin LLM): se descartan las
   palabras que ya están literalmente en tu léxico, para no gastar tokens
   en ellas.
3. **Una llamada a Claude** (`VocabExtractionService`) con el texto completo
   del guion, tu léxico y las palabras candidatas. Usa
   `chatClient.prompt()...call().entity(VocabExtractionResult.class)` de
   Spring AI, que pide el JSON con el esquema adecuado y lo mapea
   directamente a los records de Java, sin parseo manual. Claude:
   - Calcula el lema de cada candidata y comprueba si ya la conocías bajo
     otra forma (plural, conjugación, etc.).
   - Detecta phrasal verbs analizando el contexto real de las frases, no
     solo palabra por palabra.
   - Traduce cada elemento nuevo según el contexto de la frase donde
     aparece.
4. **Actualización del léxico** (`LexiconService`, sin LLM): las palabras
   nuevas se añaden a `lexicon.json`.

## Notas

- Es una app de consola pura: `spring.main.web-application-type: none` en
  `application.yml` evita que arranque un servidor web.
- Si una película es muy larga, el texto que se manda a Claude se trunca a
  60.000 caracteres (constante `MAX_TEXT_CHARS` en
  `VocabExtractionService`); la detección de vocabulario individual no se
  ve afectada porque usa la lista de candidatas completa, solo la
  detección de phrasal verbs en la parte final del guion podría perderse
  en películas extremadamente largas. Puedes subir ese límite si lo
  necesitas.
- `spring-ai-starter-model-anthropic` versión 2.0.0 (la BOM fija la versión
  exacta de todos los módulos de Spring AI); revisa
  [Maven Central](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-model-anthropic)
  por si hay una versión más reciente cuando lo uses.
