# vocab-learner (Spring Boot + Spring AI)

Versión Java del sistema de aprendizaje de vocabulario: compara los
subtítulos (.srt) de cada película con tu léxico personal, detecta palabras
y phrasal verbs nuevos, los traduce al español según el contexto, y amplía
tu léxico para que no te los vuelva a marcar como nuevos. Además, exporta
ese vocabulario a un TXT listo para importar en Anki.

## Requisitos

- Java 17+
- Maven
- Variable de entorno `ANTHROPIC_API_KEY` (solo para `bootstrap` y `process`;
  `export` no llama a Claude)

```bash
export ANTHROPIC_API_KEY="tu-api-key"
```

## Compilar

```bash
mvn clean package
```

Esto genera `target/vocab-learner-0.1.0.jar`. Para ejecutar los tests:

```bash
mvn test
```

## Carpetas de trabajo

La aplicación crea automáticamente estas carpetas (relativas al directorio
desde el que la ejecutas):

| Carpeta   | Contenido                                                        |
|-----------|------------------------------------------------------------------|
| `processed/` | Subtítulos procesados correctamente.                          |
| `error/`  | Subtítulos que fallaron (formato corrupto, codificación inválida, error de la API, etc.). |
| `output/` | Los `*_vocab.json` y los `*anki_vocab.txt` generados.            |

El `lexicon.json` no se mueve: se queda en el directorio desde el que
ejecutas la aplicación.

La raíz de estas carpetas se puede cambiar con la propiedad `vocab.root-dir`
(por defecto, `.`).

## 1. Generar tu léxico inicial (solo la primera vez)

```bash
java -jar target/vocab-learner-0.1.0.jar bootstrap lexicon.json
```

Crea `lexicon.json` con las ~2000 palabras básicas del inglés, en su forma
base (lema).

## 2. Procesar una película

```bash
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt lexicon.json
```

Los argumentos son:

- `pelicula.srt`: el subtítulo a procesar (obligatorio).
- `lexicon.json`: tu léxico (opcional; por defecto, `lexicon.json` en el
  directorio actual).

Qué hace:

1. Comprueba que el `.srt` existe. Si no existe, muestra
   `Error: No existe el fichero .srt: ...`, termina con código 1 y no mueve
   nada.
2. Genera `output/pelicula_vocab.json`, con las palabras y phrasal verbs
   nuevos de la película, su traducción y una frase de ejemplo extraída del
   guion.
3. Añade las palabras nuevas a `lexicon.json`, para que en la siguiente
   película no vuelvan a aparecer como "nuevas".
4. Mueve `pelicula.srt` a `processed/` si todo ha ido bien.

Si algo falla en cualquier paso posterior a la comprobación, el `.srt` se
mueve a `error/` y la aplicación termina con código 1.

Si ya existe un fichero con el mismo nombre en `processed/`, `error/` u
`output/`, se sobreescribe.

## 3. Exportar a Anki

```bash
java -jar target/vocab-learner-0.1.0.jar export output/pelicula_vocab.json
```

Genera `output/peliculaanki_vocab.txt`: una tarjeta por línea, con tres
campos separados por tabulador:

```
lemma<TAB>traducción<TAB>frase de ejemplo
```

El fichero está en UTF-8. Impórtalo en Anki como texto separado por
tabulaciones.

Puedes indicar otra ruta de salida como segundo argumento:

```bash
java -jar target/vocab-learner-0.1.0.jar export output/pelicula_vocab.json mazo.txt
```

## Flujo típico

```bash
# Primera vez
java -jar target/vocab-learner-0.1.0.jar bootstrap lexicon.json

# Cada película
java -jar target/vocab-learner-0.1.0.jar process pelicula.srt
java -jar target/vocab-learner-0.1.0.jar export output/pelicula_vocab.json
```

Tras esto, `output/` tiene el JSON y el TXT para Anki, `processed/` tiene el
`.srt` procesado y `lexicon.json` ha crecido.

## Estructura del proyecto

```
src/main/java/com/vocablearner/
├── VocabLearnerApplication.java     # CLI: bootstrap / process / export
├── model/
│   ├── NewVocabItem.java            # palabra o phrasal verb nuevo
│   ├── VocabExtractionResult.java   # respuesta de Claude (newWords / newPhrasalVerbs)
│   ├── VocabFile.java               # forma del *_vocab.json (sourceSrt, vocabulary)
│   └── LexiconFile.java             # forma JSON del léxico ({"words": [...]})
└── service/
    ├── SubtitleFileManager.java     # carpetas processed/error/output y movimiento de .srt
    ├── SrtParserService.java        # .srt -> texto plano (sin LLM)
    ├── CandidateExtractor.java      # filtro rápido de candidatas (sin LLM)
    ├── VocabExtractionService.java  # llamada a Claude: lematiza, detecta
    │                                #   phrasal verbs y traduce
    ├── StructuredOutput.java        # convierte la respuesta de Claude en records
    ├── LexiconService.java          # carga/guarda lexicon.json
    ├── LexiconBootstrapService.java # genera el léxico inicial de 2000 palabras
    └── AnkiExporter.java            # *_vocab.json -> TXT para Anki (sin LLM)

src/test/java/com/vocablearner/      # tests de la aplicación, el gestor de ficheros y el exportador
```

## Cómo funciona por dentro

1. **Parseo del .srt** (`SrtParserService`, sin LLM): se limpian timestamps,
   índices y etiquetas de formato, quedando solo el texto hablado. El
   fichero debe estar en UTF-8.
2. **Filtro rápido** (`CandidateExtractor`, sin LLM): se descartan las
   palabras que ya están literalmente en tu léxico, para no gastar tokens
   en ellas. Cada candidata se asocia a la primera línea donde aparece.
3. **Llamadas a Claude** (`VocabExtractionService`): el texto se trocea en
   bloques de líneas consecutivas (de unos 12.000 caracteres, con hasta 120
   candidatas por bloque), y cada candidata va en el bloque donde aparece su
   primera frase. El léxico completo va en el prompt de sistema, marcado para
   reutilizar la caché de prompts en los bloques siguientes. Claude:
   - Calcula el lema de cada candidata y comprueba si ya la conocías bajo
     otra forma (plural, conjugación, etc.).
   - Detecta phrasal verbs analizando el contexto real de las frases, no
     solo palabra por palabra.
   - Traduce cada elemento nuevo según el contexto de la frase donde
     aparece.

   Las respuestas se leen con `StructuredOutput`, que une todas las partes
   de la respuesta antes de convertirla a records de Java. Los resultados se
   deduplican por lema entre bloques.
4. **Actualización del léxico** (`LexiconService`, sin LLM): las palabras
   nuevas se añaden a `lexicon.json`.
5. **Gestión de ficheros** (`SubtitleFileManager`, sin LLM): valida el
   `.srt` antes de empezar y lo mueve a `processed/` o `error/` al terminar.
6. **Exportación** (`AnkiExporter`, sin LLM): convierte el `*_vocab.json` en
   un TXT con tabuladores. Los tabuladores o saltos de línea dentro de un
   campo se sustituyen por espacios.

## Notas

- Es una app de consola pura: `spring.main.web-application-type: none` en
  `application.yml` evita que arranque un servidor web.
- Modelo y límites de la llamada a Claude en `application.yml`
  (`claude-haiku-5-5`, `max-tokens: 18192`).
- `spring-ai-starter-model-anthropic` versión 2.0.0 (la BOM fija la versión
  exacta de todos los módulos de Spring AI); revisa
  [Maven Central](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-model-anthropic)
  por si hay una versión más reciente cuando lo uses.
