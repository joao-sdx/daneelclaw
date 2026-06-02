package org.daneel.tool.file;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SandboxFileSystem {

  private final Path root;

  @SneakyThrows
  public SandboxFileSystem(@Value("${daneel.tools.files.root:./rootdir}") String rootDir) {
    this.root = Path.of(rootDir).toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  /**
   * Resolves a user-supplied relative path against the sandbox root. Throws {@link
   * SandboxAccessException} if the resulting path escapes the root (e.g. via {@code ../}). A null
   * or blank path resolves to the root itself.
   */
  public Path resolve(String userPath) {
    var effective = (userPath == null || userPath.isBlank()) ? "" : userPath;
    var candidate = root.resolve(effective).normalize();
    if (!candidate.startsWith(root)) {
      throw new SandboxAccessException(userPath);
    }
    return candidate;
  }

  public Path root() {
    return root;
  }
}
