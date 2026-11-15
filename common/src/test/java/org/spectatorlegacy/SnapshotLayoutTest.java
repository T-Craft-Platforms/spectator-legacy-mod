package org.spectatorlegacy;

import static org.junit.Assert.*;

import org.junit.Test;

public class SnapshotLayoutTest {
  @Test
  public void capsAndRoundsInventorySizes() {
    assertEquals(0, SnapshotLayout.itemCount(0));
    assertEquals(54, SnapshotLayout.itemCount(100));
    assertEquals(9, SnapshotLayout.displaySize(0));
    assertEquals(9, SnapshotLayout.displaySize(9));
    assertEquals(18, SnapshotLayout.displaySize(10));
    assertEquals(54, SnapshotLayout.displaySize(54));
  }

  @Test
  public void acceptsOnlyValidChestLayouts() {
    assertFalse(SnapshotLayout.validDisplaySize(0));
    assertTrue(SnapshotLayout.validDisplaySize(9));
    assertFalse(SnapshotLayout.validDisplaySize(10));
    assertTrue(SnapshotLayout.validDisplaySize(54));
    assertFalse(SnapshotLayout.validDisplaySize(63));
  }
}
