package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptDocumentTest {

  // --- parse() tests ---

  @Test
  void parse_withFrontmatter_extractsSummaryAndBody() {
    var content = "---\nsummary: Says hello out loud.\n---\nUse the speak tool.";
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEqualTo("Says hello out loud.");
    assertThat(doc.body()).isEqualTo("Use the speak tool.");
  }

  @Test
  void parse_withoutFrontmatter_returnsEmptySummaryAndFullContent() {
    var content = "Use the speak tool.";
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEmpty();
    assertThat(doc.body()).isEqualTo("Use the speak tool.");
  }

  @Test
  void parse_withUnclosedFrontmatter_returnsEmptySummaryAndFullContent() {
    var content = "---\nsummary: Oops.\nBody without closing delimiter.";
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEmpty();
    assertThat(doc.body()).isEqualTo(content);
  }

  @Test
  void parse_withFrontmatterNoSummaryKey_returnsEmptySummary() {
    var content = "---\nother: value\n---\nBody here.";
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEmpty();
    assertThat(doc.body()).isEqualTo("Body here.");
  }

  @Test
  void parse_frontmatterStripsLeadingWhitespaceFromBody() {
    var content = "---\nsummary: Test.\n---\n\nBody after blank line.";
    var doc = PromptDocument.parse(content);
    assertThat(doc.body()).isEqualTo("Body after blank line.");
  }

  // --- render() tests ---

  @Test
  void render_then_parse_roundTrips_summary() {
    var content = PromptDocument.render("Says hello.", "Use the speak tool.");
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEqualTo("Says hello.");
  }

  @Test
  void render_then_parse_summaryWithColon_roundTrips() {
    var content = PromptDocument.render("Reminds: drink water", "Remember to hydrate.");
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEqualTo("Reminds: drink water");
  }
}
