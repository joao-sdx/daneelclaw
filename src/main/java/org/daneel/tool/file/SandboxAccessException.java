package org.daneel.tool.file;

public class SandboxAccessException extends RuntimeException {

  public SandboxAccessException(String userPath) {
    super("path escapes sandbox root: " + userPath);
  }
}
