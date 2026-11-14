package org.spectator164;

import net.minecraft.entity.player.EntityPlayer;

public class CommonProxy {
  public void init() {}

  public void spectatorState(int entityId, boolean active) {}

  public void cameraTarget(int entityId) {}

  public boolean isClientSpectator(EntityPlayer player) {
    return false;
  }

  public void showInventorySnapshot(byte[] data) {}
}
