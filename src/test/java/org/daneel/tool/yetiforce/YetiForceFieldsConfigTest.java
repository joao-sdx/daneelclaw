package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YetiForceFieldsConfigTest {

  private static final YetiForceFieldsConfig CONFIG = new YetiForceFieldsConfig();

  @Test
  void getFields_leads_returnsEnabledFieldsIncludingARequiredOne() {
    assertThat(CONFIG.getFields("Leads")).isNotEmpty();
    assertThat(CONFIG.getFields("Leads")).allMatch(YetiForceField::daneel);
    assertThat(CONFIG.getFields("Leads")).anyMatch(YetiForceField::required);
  }

  @Test
  void getFields_allFourModules_returnNonEmpty() {
    assertThat(CONFIG.getFields("Leads")).isNotEmpty();
    assertThat(CONFIG.getFields("Accounts")).isNotEmpty();
    assertThat(CONFIG.getFields("Contacts")).isNotEmpty();
    assertThat(CONFIG.getFields("SSalesProcesses")).isNotEmpty();
  }

  @Test
  void getFields_unknownModule_returnsEmpty() {
    assertThat(CONFIG.getFields("NonExistent")).isEmpty();
  }
}
