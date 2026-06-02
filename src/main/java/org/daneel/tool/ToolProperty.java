package org.daneel.tool;

public record ToolProperty(
    String name, String description, String type, boolean required, String itemType) {
  public ToolProperty(String name, String description, String type, boolean required) {
    this(name, description, type, required, null);
  }
}
