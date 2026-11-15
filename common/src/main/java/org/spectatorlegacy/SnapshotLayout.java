package org.spectatorlegacy;

/** Rules for the read-only, chest-style snapshot shown by the mod. */
final class SnapshotLayout {
  private SnapshotLayout() {}

  static int itemCount(int inventorySize) {
    return Math.min(inventorySize, 54);
  }

  static int displaySize(int itemCount) {
    return Math.max(9, ((itemCount + 8) / 9) * 9);
  }

  static boolean validDisplaySize(int size) {
    return size >= 9 && size <= 54 && size % 9 == 0;
  }
}
