package org.spectator164;

final class SpectatorProtocol {
  static final String CHANNEL = "Spectator164";

  static final byte TELEPORT_TO_PLAYER = 1;
  static final byte SPECTATOR_STATE = 2;
  static final byte REQUEST_BLOCK_INVENTORY = 3;
  static final byte CHEST_SNAPSHOT = 4;
  static final byte REQUEST_CAMERA = 5;
  static final byte CAMERA_TARGET = 6;
  static final byte TOGGLE_MODE = 7;
  static final byte FURNACE_SNAPSHOT = 8;
  static final byte SPECIAL_INVENTORY_SNAPSHOT = 9;
  static final byte ENTITY_INVENTORY_SNAPSHOT = 10;
  static final byte REQUEST_ENTITY_INVENTORY = 11;

  static final int HORSE = 0;
  static final int CHEST_MINECART = 1;
  static final int HOPPER_MINECART = 2;

  static final int DROPPER = 0;
  static final int DISPENSER = 1;
  static final int HOPPER = 2;
  static final int BREWING_STAND = 3;
  static final int BEACON = 4;

  private SpectatorProtocol() {}

  static boolean validEntityInventorySize(int kind, int size) {
    if (kind == HORSE) {
      return size == 2 || size == 17;
    }
    return size == (kind == CHEST_MINECART ? 27 : 5);
  }
}
