package com.lingo.app.placement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlacementLevelTest {

  @Test
  void scoreMapsToExpectedCefr() {
    assertThat(PlacementService.levelFor(0)).isEqualTo("A1");
    assertThat(PlacementService.levelFor(2)).isEqualTo("A1");
    assertThat(PlacementService.levelFor(3)).isEqualTo("A2");
    assertThat(PlacementService.levelFor(5)).isEqualTo("B1");
    assertThat(PlacementService.levelFor(7)).isEqualTo("B2");
    assertThat(PlacementService.levelFor(9)).isEqualTo("C1");
    assertThat(PlacementService.levelFor(10)).isEqualTo("C1");
  }
}
