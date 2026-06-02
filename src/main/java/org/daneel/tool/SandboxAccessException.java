package org.daneel.tool;

public class SandboxAccessException extends RuntimeException {

  public SandboxAccessException(String userPath) {
    super("path escapes sandbox root: " + userPath);
  }
}
