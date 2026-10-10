# SDD Spec: Exportador de Vocabulario a Formato Anki (.txt / .csv)

## 1. Contexto & Estado Actual (Input)
* **Origen de datos:** Fichero JSON con el vocabulario filtrado (palabras de la película que no están en mi vocabulario conocido).
* **Estructura estimada del JSON actual:**
  ```{"vocabulary" : [ {
    "lemma" : "disposal",
    "surfaceForm" : "disposal",
    "type" : "word",
    "translation" : "eliminación (de residuos)",
    "example" : "Engineer, waste disposal."
  },.....
  ]}
  ```

## 2. Plan de Implementación (Output Requerido)
* [ ] **Creación del módulo exportador:** Leer el JSON existente y transformarlo en un string plano separado por tabulaciones (`\t`).
* [ ] **Formato Anki Estándar:** Cada línea debe representar una tarjeta con la estructura: `Palabra \t Contexto / Traducción`.
* [ ] **Persistencia:** Guardar el resultado en un archivo llamado `{nombre de la pelicula}anki_vocab.txt`  en codificación **UTF-8**.

## 3. Estrategia Test-First (TDD Lifecycle)
* **Comando de Test:** `mvn test ./...` 
* [x] **Fase Roja (Test Falla):** Crear un test que valide que, dado un JSON de prueba con 2 palabras, el exportador genere un archivo `.txt` con exactamente 2 líneas separadas por tabulaciones.
* [x] **Fase Verde (Código):** Escribir la lógica mínima del exportador para que el test pase.

## 4. Diseño Técnico (propuesta, pendiente de aprobación)

### 4.1 Análisis del JSON real
Fichero de referencia: `High.Fidelity.2000_vocab.json` (también existe `Blade_Runner_1982_vocab.json`, con la misma estructura).
* Generado por `VocabLearnerApplication.runProcess` (escribe `{"sourceSrt": ..., "vocabulary": [...]}`).
* Cada elemento de `vocabulary` tiene exactamente 5 claves: `lemma`, `surfaceForm`, `type`, `translation`, `example`. Coincide con el record `NewVocabItem`.
* `type` vale `"word"` o `"phrasal_verb"`. Hay 12 entradas de `phrasal_verb` en el fichero de High Fidelity.
* Hay unas 554 entradas en total (estimado a partir del número de claves; no contado exacto).
* Las claves de primer nivel son `sourceSrt` y `vocabulary`. El spec solo menciona `vocabulary`.
* No hay tabuladores ni saltos de línea escapados en los valores. Sí hay 12 valores con comillas escapadas (`\"`), que no afectan a un formato TSV.
* Los valores de `translation` pueden contener paréntesis y comas, como `"voy a (forma coloquial de 'going to')"`.

### 4.2 Diseño propuesto
* **Lectura:** un record nuevo `VocabFile(String sourceSrt, List<NewVocabItem> vocabulary)` en `model/`, leído con el `ObjectMapper` creado a mano, como en los servicios existentes. Hay que ignorar claves desconocidas o añadir `sourceSrt` al record, porque el `ObjectMapper` por defecto falla con propiedades no reconocidas.
* **Módulo:** `service/AnkiExporter` con un método `String toTsv(List<NewVocabItem>)` (puro, testeable) y `Path export(Path vocabJson)` que lee, transforma y escribe.
* **Formato por línea:** `lemma \t translation \t example`. Ver pregunta abierta 1 sobre si la segunda columna es "Contexto / Traducción" en una sola celda.
* **Sanitización:** reemplazar `\t`, `\r` y `\n` dentro de los campos por un espacio. Hoy no hay casos, pero evita romper el formato.
* **Escritura:** `Files.writeString(path, contenido, StandardCharsets.UTF_8)`, con separador `\n`.
* **Nombre de salida:** a partir del nombre del JSON, quitando el sufijo `_vocab.json`. Ej.: `High.Fidelity.2000_vocab.json` → `High.Fidelity.2000anki_vocab.txt` (según el spec literal; ver pregunta 3).
* **Punto de entrada:** un subcomando nuevo `export <vocab.json> [salida.txt]` en `VocabLearnerApplication`, igual que `bootstrap` y `process`. Así no cambia el pipeline existente.

### 4.3 Preguntas abiertas
1. **Columnas:** el spec dice `Palabra \t Contexto / Traducción` (dos columnas). Propuesta: `lemma \t translation \t example`, tres columnas, que Anki puede mapear a campos separados. Alternativa fiel al spec: `lemma \t translation + " / " + example`.
2. **Palabra:** `lemma` (forma base, recomendado) o `surfaceForm`.
3. **Nombre de fichero:** el spec escribe `{nombre}anki_vocab.txt` sin guion bajo. Propuesta: `{nombre}_anki_vocab.txt` por coherencia con `_vocab.json`. Confirma si prefieres el literal.
4. **Trigger:** ¿solo subcomando `export`, o también automático al final de `process`? Propuesta: solo subcomando, para no tocar el flujo existente.

### 4.4 Ajustes al comando de test
* `mvn test ./...` no es sintaxis válida de Maven. Propuesta: `mvn test` (o `mvn -Dtest=AnkiExporterTest test` para un test concreto).
* `spring-boot-starter-test` ya está en el classpath. No hace falta añadir dependencias.
* El test de fase roja va en `src/test/java/com/vocablearner/service/AnkiExporterTest.java`. Usa un JSON temporal con 2 entradas y comprueba 2 líneas con 2 tabuladores cada una (3 columnas) y codificación UTF-8 (tildes presentes).

---
## 🛑 Estado de Aprobación del Desarrollador
* [x] **Diseño Técnico Aceptado** (Cambia a [x] para autorizar a Claude a codificar).
* Decisiones tomadas al aprobar (valores por defecto de la sección 4.3): columnas `lemma \t translation \t example`; Palabra = `lemma`; nombre de salida `{nombre}anki_vocab.txt` según el spec literal; solo subcomando `export`.
