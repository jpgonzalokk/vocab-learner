# SDD Spec: Pipeline de Procesamiento de Subtítulos (.srt) por Parámetro

## 1. Contexto & Requisitos de Arquitectura
* **Entrada de Usuario:** La aplicación debe aceptar la ruta de un archivo `.srt` como argumento por línea de comandos (CLI Parameter).
* **Ciclo de Vida del Archivo (Pipeline):**
  * Al iniciar: Validar la existencia del archivo `.srt` proporcionado.
  * Si el procesamiento es **Exitoso**: Mover el archivo `.srt` original al directorio `./input/`.
  * Si el procesamiento **Falla**: Mover el archivo `.srt` original al directorio `./error/`.
* **Destino de Resultados:** Los archivos JSON de vocabulario y los archivos generados para Anki deben guardarse estrictamente en el directorio `./output/`.

## 2. Árbol de Directorios Requerido
La aplicación debe asegurar o crear automáticamente la siguiente estructura si no existe:
* 📁 `.` (Raíz del proyecto)
  * 📁 `input/`  -> Historial de subtítulos procesados correctamente.
  * 📁 `error/`  -> Subtítulos que fallaron (formato corrupto, codificación inválida).
  * 📁 `output/` -> Archivos `.json` de vocabulario y `.txt` para Anki.

## 3. Estrategia Test-First (TDD)
* **Comando de Test:** `mvn test` (Maven, Java 17, JUnit 5 vía `spring-boot-starter-test`).
* [x] **Fase Roja (Test Falla):** tests escritos, `mvn test` falla por compilación (`SubtitleFileManager` no existe).
* [x] **Fase Verde (Código):** `SubtitleFileManager` implementado y `runProcess` integrado. `mvn test`: 6 tests, 0 fallos.
* [x] **Test de Aislamiento:** Validar que si el archivo no existe, el programa termine con código de error sin mover nada.
* [x] **Test de Éxito:** Simular un procesamiento correcto y verificar que el `.srt` termine en `./input/` y el resultado en `./output/`.

## 4. Diseño Técnico (propuesta, pendiente de aprobación)

### 4.1 Cómo recibe parámetros hoy la aplicación
* `VocabLearnerApplication.run` despacha por `args[0]`: `bootstrap`, `process`, `export` (y `printUsage` en cualquier otro caso).
* `process <srt> [lexicon.json] [salida.json]`: posicional. `args[1]` se convierte en `Path` sin comprobar que exista. Si falta, muestra el uso.
* El lexicon por defecto es `lexicon.json` en el directorio actual. La salida por defecto es `<nombre>_vocab.json` en el directorio actual, no en `output/`.
* Si el `.srt` no existe, `Files.readAllLines` lanza `NoSuchFileException`, que se propaga desde `run()`. No hay código de salida controlado ni movimiento de ficheros.
* `SrtParserService.toPlainText` lee en UTF-8 estricto. Un fichero con otra codificación lanza `MalformedInputException` (subclase de `IOException`). Un BOM al inicio hace que la primera línea de índice (`1`) no se reconozca como índice y pase como texto.
* `export <vocab.json> [salida.txt]` usa el mismo patrón: el valor por defecto va al directorio actual.

### 4.2 Diseño propuesto
* **Parámetros:** `process <ruta.srt> [lexicon.json]`. El segundo argumento es el lexicon; la salida ya no es un parámetro. Validar al inicio que el fichero existe y es regular. Si no, salir con código de error distinto de 0 **sin mover nada**.
* **Directorios:** crear `input/`, `error/` y `output/` al arrancar, relativos al directorio actual (igual que `lexicon.json`).
* **Salida:** todos los `_vocab.json` y los `.txt` de Anki van a `output/`. Esto implica quitar el argumento `[salida.json]` de `process` (ver pregunta 2).
* **Ciclo de vida:** el movimiento del `.srt` ocurre al final de `runProcess`. Éxito = `process` completo, incluido el guardado del lexicon, y el `.srt` pasa a `input/`. Cualquier excepción posterior a la validación = mover a `error/` y relanzar para que el código de salida sea de fallo.
* **Colisiones:** si ya existe un fichero con el mismo nombre en destino (`input/`, `error/` o `output/`), se sobrescribe.
* **Código de salida:** `SpringApplication.exit` con un `ExitCodeGenerator`, para no depender del comportamiento por defecto ante excepciones.
* **Componente nuevo:** `service/SubtitleFileManager` con tres responsabilidades: crear directorios, validar el `.srt` y mover ficheros (con resolución de colisiones). `VocabLearnerApplication` orquesta; no mezcla más lógica de ficheros en él.

### 4.3 Archivos afectados
| Archivo | Cambio |
|---|---|
| `src/main/java/com/vocablearner/VocabLearnerApplication.java` | `runProcess`: validación previa, rutas por defecto en `output/`, bloque try/catch con movimiento a `input/` o `error/`, código de salida. `runExport`: salida por defecto en `output/`. Texto de uso. |
| `src/main/java/com/vocablearner/service/SubtitleFileManager.java` | **Nuevo.** Creación de directorios, validación, movimiento con resolución de colisiones. |
| `src/main/java/com/vocablearner/service/SrtParserService.java` | Opcional: tratar explícitamente el BOM (`﻿`) al inicio. No es estrictamente necesario para este spec. |
| `src/test/java/com/vocablearner/service/SubtitleFileManagerTest.java` | **Nuevo.** Pruebas de directorios, movimiento y colisiones, con `@TempDir`. |
| `src/test/java/com/vocablearner/VocabLearnerApplicationTest.java` | **Nuevo.** Test de aislamiento (fichero inexistente: código de error, nada movido) y test de éxito. El de éxito necesita sustituir `VocabExtractionService` por un doble con `@MockitoBean`, porque no se debe llamar a Claude en tests. |
| `.gitignore` | Añadir `input/`, `error/`, `output/`. Son datos de usuario, igual que `lexicon.json` y `*_vocab.json`. |
| `CLAUDE.md` | Sección Commands: `process` ya no acepta salida, y `export` y los directorios cambian. |
| `README.md` | Mismo cambio de uso que CLAUDE.md. |

### 4.4 Decisiones del desarrollador
1. **Errores:** cualquier excepción posterior a la validación mueve el `.srt` a `error/`, incluidos los fallos de la API de Claude.
2. **Parámetros:** `process <ruta.srt> [lexicon.json]`. El segundo argumento es el lexicon y no define la salida. La salida del proceso es un fichero con el nombre de la película, derivado del `.srt`, guardado siempre en `output/`. Lectura propuesta: `<nombre>_vocab.json` (el nombre del `.srt` sin extensión, más el sufijo que ya usa el proceso). Pendiente de confirmar el sufijo exacto, porque la descripción mencionaba ".srt".
3. **Lexicon:** se queda en el directorio actual.
4. **Colisiones:** se sobrescribe el fichero existente en el destino.
5. **Directorio raíz:** relativo al directorio actual.

---
## 🛑 Estado de Aprobación del Desarrollador
* [x] **Diseño Técnico Aceptado** (Cambia a [x] para autorizar la codificación).
* Decisiones tomadas al aprobar: `process <ruta.srt> [lexicon.json]`; salida en `output/` con el nombre derivado del `.srt` (`<nombre>_vocab.json`); errores a `error/`; colisiones sobrescritas; directorios relativos al directorio actual, configurable con la propiedad `vocab.root-dir` (default `.`) para los tests.
