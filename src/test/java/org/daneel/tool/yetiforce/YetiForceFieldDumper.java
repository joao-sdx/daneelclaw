package org.daneel.tool.yetiforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Developer utility (NOT a test) that dumps the full field catalog of every YetiForce module into a
 * single {@code target/yetiforce/yetiforce-fields.yml}, matching the schema of the curated {@code
 * src/main/resources/yetiforce-fields.yml} so it can replace that file wholesale. Every field is
 * emitted with {@code daneel: true}; flip unwanted fields to {@code false} before dropping the file
 * in — only fields with {@code daneel: true} are exposed to the LLM.
 *
 * <p>It reads the {@code YETIFORCE_*} values from the project-root {@code .env} file, so just run
 * {@code main()} from the IDE (no env exports needed), or:
 *
 * <pre>
 *   ./mvnw exec:java \
 *     -Dexec.mainClass=org.daneel.tool.yetiforce.YetiForceFieldDumper \
 *     -Dexec.classpathScope=test
 * </pre>
 *
 * <p>It deliberately has no {@code @Test} methods and is not named {@code *Test}, so Surefire never
 * runs it during the build (CICD has no {@code .env}).
 */
@Slf4j
public final class YetiForceFieldDumper {

  private static final List<String> MODULES =
      List.of("Leads", "Accounts", "Contacts", "SSalesProcesses");

  private static final String[] REQUIRED_ENV = {
    "YETIFORCE_URL",
    "YETIFORCE_API_KEY",
    "YETIFORCE_APP_NAME",
    "YETIFORCE_APP_PASS",
    "YETIFORCE_USER",
    "YETIFORCE_PASSWORD"
  };

  private static final Path ENV_FILE = Path.of(".env");

  private static final Path OUTPUT_DIR = Path.of("target", "yetiforce");

  private static final Path OUTPUT_FILE = OUTPUT_DIR.resolve("yetiforce-fields.yml");

  private final YetiForceCrmClient client;
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  private YetiForceFieldDumper(YetiForceCrmClient client) {
    this.client = client;
  }

  public static void main(String[] args) throws Exception {
    var env = loadDotEnv(ENV_FILE);
    for (var name : REQUIRED_ENV) {
      if (isBlank(env.get(name))) {
        log.error("missing_env var={} — set it in {}", name, ENV_FILE);
        return;
      }
    }

    var props = new YetiForceProperties();
    props.setUrl(env.get("YETIFORCE_URL"));
    props.setApiKey(env.get("YETIFORCE_API_KEY"));
    props.setAppName(env.get("YETIFORCE_APP_NAME"));
    props.setAppPass(env.get("YETIFORCE_APP_PASS"));
    props.setUser(env.get("YETIFORCE_USER"));
    props.setPassword(env.get("YETIFORCE_PASSWORD"));

    new YetiForceFieldDumper(new YetiForceCrmClient(props, new ObjectMapper())).run();
  }

  /** Parses {@code KEY=value} lines from a {@code .env} file, ignoring comments and blanks. */
  private static Map<String, String> loadDotEnv(Path path) throws IOException {
    var values = new LinkedHashMap<String, String>();
    if (!Files.exists(path)) {
      log.error("missing_env_file path={} — create it with the YETIFORCE_* values", path);
      return values;
    }
    for (var line : Files.readAllLines(path)) {
      var trimmed = line.strip();
      var eq = trimmed.indexOf('=');
      if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
        continue;
      }
      values.put(trimmed.substring(0, eq).strip(), stripQuotes(trimmed.substring(eq + 1).strip()));
    }
    return values;
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private void run() throws Exception {
    Files.createDirectories(OUTPUT_DIR);
    var document = new LinkedHashMap<String, Object>();
    for (var module : MODULES) {
      try {
        var fields = normalize(client.getFields(module));
        document.put(module, fields);
        log.info("yetiforce_fields_fetched module={} count={}", module, fields.size());
      } catch (Exception ex) {
        log.error("yetiforce_fields_dump_failed module={} reason={}", module, ex.getMessage());
      }
    }
    yamlMapper.writeValue(OUTPUT_FILE.toFile(), document);
    log.info("yetiforce_fields_dumped file={} modules={}", OUTPUT_FILE, document.keySet());
  }

  /** Maps the dictionary {@code result} into the curated {@code yetiforce-fields.yml} schema. */
  private List<Map<String, Object>> normalize(Map<String, Object> result) {
    var out = new ArrayList<Map<String, Object>>();
    for (var raw : extractFields(result)) {
      var entry = toFieldEntry(raw);
      if (entry != null) {
        out.add(entry);
      }
    }
    return out;
  }

  /**
   * The dictionary endpoint may return fields as a list, or as a map keyed by field name (and may
   * wrap them under a {@code fields} key). Handle each shape defensively.
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractFields(Map<String, Object> result) {
    var node = result.containsKey("fields") ? result.get("fields") : result;
    var fields = new ArrayList<Map<String, Object>>();
    if (node instanceof List<?> list) {
      for (var item : list) {
        if (item instanceof Map<?, ?> map) {
          fields.add((Map<String, Object>) map);
        }
      }
    } else if (node instanceof Map<?, ?> map) {
      for (var e : ((Map<String, Object>) map).entrySet()) {
        if (e.getValue() instanceof Map<?, ?> def) {
          var withName = new LinkedHashMap<String, Object>((Map<String, Object>) def);
          withName.putIfAbsent("name", e.getKey());
          fields.add(withName);
        }
      }
    }
    return fields;
  }

  private Map<String, Object> toFieldEntry(Map<String, Object> raw) {
    var name = asText(raw.get("name"));
    if (isBlank(name)) {
      return null;
    }
    var entry = new LinkedHashMap<String, Object>();
    entry.put("name", name);
    entry.put("type", fieldType(raw.get("type")));
    entry.put("required", asBoolean(raw.get("mandatory")));
    entry.put("daneel", true);
    entry.put("description", description(raw, name));
    return entry;
  }

  private String fieldType(Object type) {
    if (type instanceof String s && !s.isBlank()) {
      return s;
    }
    if (type instanceof Map<?, ?> map && map.get("name") instanceof String s && !s.isBlank()) {
      return s;
    }
    return "string";
  }

  private String description(Map<String, Object> raw, String name) {
    var label = asText(raw.get("label"));
    var base = isBlank(label) ? name : label;
    var picklist = picklistValues(raw.get("picklistvalues"));
    return picklist.isEmpty() ? base : base + " [" + String.join(", ", picklist) + "]";
  }

  @SuppressWarnings("unchecked")
  private List<String> picklistValues(Object picklist) {
    var values = new ArrayList<String>();
    if (picklist instanceof Map<?, ?> map) {
      for (var v : ((Map<String, Object>) map).values()) {
        if (!isBlank(asText(v))) {
          values.add(asText(v));
        }
      }
    } else if (picklist instanceof List<?> list) {
      for (var v : list) {
        if (!isBlank(asText(v))) {
          values.add(asText(v));
        }
      }
    }
    return values;
  }

  private static String asText(Object value) {
    return value == null ? null : value.toString();
  }

  private static boolean asBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    return value != null && "true".equalsIgnoreCase(value.toString());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
