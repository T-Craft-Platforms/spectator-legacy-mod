package org.spectatorlegacy;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.EnumGameType;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.input.Keyboard;

public final class ClientHooks {
  private ClientHooks() {}

  private static boolean debugKeyPending;
  private static boolean spectatorShortcutUsed;
  private static boolean pendingProfiler;
  // Reveal partial blocks against their supports without adding faces to solid terrain.
  public static boolean shouldRenderSide(
      Block block, IBlockAccess world, int x, int y, int z, int side) {
    return block.shouldSideBeRendered(world, x, y, z, side)
        || (!block.isOpaqueCube()
            && (block.getBlockBoundsMinX() > 0.0D
                || block.getBlockBoundsMinY() > 0.0D
                || block.getBlockBoundsMinZ() > 0.0D
                || block.getBlockBoundsMaxX() < 1.0D
                || block.getBlockBoundsMaxY() < 1.0D
                || block.getBlockBoundsMaxZ() < 1.0D)
            && world.isBlockOpaqueCube(x, y, z)
            && isSpectating());
  }

  public static boolean nextKeyboardEvent() {
    if (!Keyboard.next()) {
      return false;
    }
    Minecraft mc = Minecraft.getMinecraft();
    int key = Keyboard.getEventKey();
    boolean down = Keyboard.getEventKeyState();
    if (key == Keyboard.KEY_F3 && down && mc.currentScreen == null && !debugKeyPending) {
      debugKeyPending = true;
      spectatorShortcutUsed = false;
      pendingProfiler = GuiScreen.isShiftKeyDown();
    }
    if (debugKeyPending
        && down
        && mc.currentScreen == null
        && mc.thePlayer != null
        && ((key == Keyboard.KEY_N && Keyboard.isKeyDown(Keyboard.KEY_F3))
            || (key == Keyboard.KEY_F3 && Keyboard.isKeyDown(Keyboard.KEY_N)))) {
      PacketDispatcher.sendPacketToServer(
          PacketDispatcher.getPacket(
              SpectatorProtocol.CHANNEL, new byte[] {SpectatorProtocol.TOGGLE_MODE}));
      spectatorShortcutUsed = true;
    }
    if (key == Keyboard.KEY_F3 && !down && debugKeyPending) {
      if (!spectatorShortcutUsed) {
        mc.gameSettings.showDebugInfo = !mc.gameSettings.showDebugInfo;
        mc.gameSettings.showDebugProfilerChart = pendingProfiler;
      }
      debugKeyPending = false;
    }
    return true;
  }

  public static void setDebugInfo(GameSettings settings, boolean value) {
    if (!debugKeyPending) {
      settings.showDebugInfo = value;
    }
  }

  public static void setDebugProfiler(GameSettings settings, boolean value) {
    if (!debugKeyPending) {
      settings.showDebugProfilerChart = value;
    }
  }

  public static void setGameType(EnumGameType mode) {
    SpectatorMod.setClientMode(mode);
  }

  public static void selectHotbarSlot(InventoryPlayer inventory, int slot) {
    if (!isSpectating()) {
      inventory.currentItem = slot;
    }
  }

  public static boolean isSpectating() {
    Minecraft mc = Minecraft.getMinecraft();
    return mc.thePlayer != null && SpectatorMod.active(mc.thePlayer);
  }
}
