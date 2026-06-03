package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YetiForceFieldsConfigTest {

  private static final YetiForceFieldsConfig CONFIG = new YetiForceFieldsConfig();

  @Test
  void getFields_leads_returnsFieldsIncludingRequiredLastname() {
    assertThat(CONFIG.getFields("Leads")).isNotEmpty();
    assertThat(CONFIG.getFields("Leads"))
        .anySatisfy(
            f -> {
              assertThat(f.name()).isEqualTo("lastname");
              assertThat(f.required()).isTrue();
            });
  }

  @Test
  void getFields_allFourModules_returnNonEmpty() {
    assertThat(CONFIG.getFields("Leads")).isNotEmpty();
    assertThat(CONFIG.getFields("Accounts")).isNotEmpty();
    assertThat(CONFIG.getFields("Contacts")).isNotEmpty();
    assertThat(CONFIG.getFields("SalesProcesses")).isNotEmpty();
  }

  @Test
  void getFields_unknownModule_returnsEmpty() {
    assertThat(CONFIG.getFields("NonExistent")).isEmpty();
  }
}
