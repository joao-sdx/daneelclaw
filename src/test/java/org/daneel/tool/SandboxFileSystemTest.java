package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SandboxFileSystemTest {

  @TempDir Path tempDir;

  private SandboxFileSystem sandbox;

  @BeforeEach
  void setUp() {
    sandbox = new SandboxFileSystem(tempDir.toString());
  }

  @Test
  void resolve_legitimatePath_staysUnderRoot() {
    var resolved = sandbox.resolve("a/b/c.txt");
    assertThat(resolved.toString()).startsWith(sandbox.root().toString());
  }

  @Test
  void resolve_blankPath_returnsRoot() {
    assertThat(sandbox.resolve("")).isEqualTo(sandbox.root());
    assertThat(sandbox.resolve("  ")).isEqualTo(sandbox.root());
  }

  @Test
  void resolve_nullPath_returnsRoot() {
    assertThat(sandbox.resolve(null)).isEqualTo(sandbox.root());
  }

  @Test
  void resolve_dotDotTraversal_throwsSandboxAccessException() {
    assertThatThrownBy(() -> sandbox.resolve("../etc/passwd"))
        .isInstanceOf(SandboxAccessException.class);
  }

  @Test
  void resolve_absolutePathOutsideRoot_throwsSandboxAccessException() {
    assertThatThrownBy(() -> sandbox.resolve("/etc/passwd"))
        .isInstanceOf(SandboxAccessException.class);
  }

  @Test
  void resolve_nestedTraversal_throwsSandboxAccessException() {
    assertThatThrownBy(() -> sandbox.resolve("sub/../../x"))
        .isInstanceOf(SandboxAccessException.class);
  }

  @Test
  void resolve_subDirectoryPath_staysUnderRoot() {
    var resolved = sandbox.resolve("notes/2024/diary.md");
    assertThat(resolved.toString()).startsWith(sandbox.root().toString());
  }

  @Test
  void root_isAbsoluteAndNormalized() {
    assertThat(sandbox.root().isAbsolute()).isTrue();
    assertThat(sandbox.root().toString()).doesNotContain("..");
  }
}
