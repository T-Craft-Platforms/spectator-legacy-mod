package org.spectator164;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;

@SideOnly(Side.CLIENT)
final class InventorySnapshotViewer {
  private InventorySnapshotViewer() {}

  static void showInventorySnapshot(byte[] data) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || !SpectatorMod.active(mc.thePlayer)) {
      return;
    }
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
      int type = in.readUnsignedByte();
      if (type == SpectatorProtocol.ENTITY_INVENTORY_SNAPSHOT) {
        showEntityInventory(mc, in);
      } else if (type == SpectatorProtocol.SPECIAL_INVENTORY_SNAPSHOT) {
        showSpecialInventory(mc, in);
      } else {
        showStandardInventory(mc, in, type);
      }
    } catch (IOException ignored) {
    }
  }

  private static void showEntityInventory(Minecraft mc, DataInputStream in) throws IOException {
    int kind = in.readUnsignedByte();
    if (kind > SpectatorProtocol.HOPPER_MINECART) {
      return;
    }
    String title = Packet.readString(in, 64);
    boolean customName = in.readBoolean();
    int horseType = 0, variant = 0, age = 0;
    boolean tame = false;
    if (kind == SpectatorProtocol.HORSE) {
      horseType = in.readUnsignedByte();
      variant = in.readInt();
      age = in.readInt();
      tame = in.readBoolean();
      if (horseType > 4) {
        return;
      }
    }
    int size = in.readUnsignedByte();
    if (!SpectatorProtocol.validEntityInventorySize(kind, size)) {
      return;
    }
    InventoryBasic snapshot = new InventoryBasic(title, customName, size);
    for (int i = 0; i < size; i++) {
      snapshot.setInventorySlotContents(i, Packet.readItemStack(in));
    }
    if (in.available() != 0) {
      return;
    }
    if (kind == SpectatorProtocol.HORSE) {
      EntityHorse horse = new EntityHorse(mc.theWorld);
      horse.setHorseType(horseType);
      horse.setHorseVariant(variant);
      horse.setGrowingAge(age);
      horse.setHorseTamed(tame);
      horse.setChested(size == 17);
      horse.setHorseSaddled(snapshot.getStackInSlot(0) != null);
      horse.func_110236_r(horse.getHorseArmorIndex(snapshot.getStackInSlot(1)));
      mc.displayGuiScreen(new GuiScreenHorseInventory(mc.thePlayer.inventory, snapshot, horse));
    } else if (kind == SpectatorProtocol.CHEST_MINECART) {
      mc.displayGuiScreen(new GuiChest(mc.thePlayer.inventory, snapshot));
    } else {
      mc.displayGuiScreen(new GuiHopper(mc.thePlayer.inventory, snapshot));
    }
  }

  private static void showSpecialInventory(Minecraft mc, DataInputStream in) throws IOException {
    int kind = in.readUnsignedByte();
    String title = Packet.readString(in, 64);
    boolean customName = in.readBoolean();
    IInventory snapshot;
    GuiScreen screen;
    if (kind == SpectatorProtocol.DROPPER || kind == SpectatorProtocol.DISPENSER) {
      TileEntityDispenser dispenser =
          kind == SpectatorProtocol.DROPPER ? new TileEntityDropper() : new TileEntityDispenser();
      if (customName) {
        dispenser.setCustomName(title);
      }
      snapshot = dispenser;
      screen = new GuiDispenser(mc.thePlayer.inventory, dispenser);
    } else if (kind == SpectatorProtocol.HOPPER) {
      TileEntityHopper hopper = new TileEntityHopper();
      if (customName) {
        hopper.setInventoryName(title);
      }
      snapshot = hopper;
      screen = new GuiHopper(mc.thePlayer.inventory, hopper);
    } else if (kind == SpectatorProtocol.BREWING_STAND) {
      TileEntityBrewingStand brewing = new TileEntityBrewingStand();
      if (customName) {
        brewing.func_94131_a(title);
      }
      brewing.setBrewTime(in.readInt());
      snapshot = brewing;
      screen = new GuiBrewingStand(mc.thePlayer.inventory, brewing);
    } else if (kind == SpectatorProtocol.BEACON) {
      TileEntityBeacon beacon = new TileEntityBeacon();
      if (customName) {
        beacon.func_94047_a(title);
      }
      beacon.setLevels(in.readInt());
      beacon.setPrimaryEffect(in.readInt());
      beacon.setSecondaryEffect(in.readInt());
      snapshot = beacon;
      screen = new ReadOnlyBeacon(mc.thePlayer.inventory, beacon);
    } else {
      return;
    }
    for (int i = 0; i < snapshot.getSizeInventory(); i++) {
      snapshot.setInventorySlotContents(i, Packet.readItemStack(in));
    }
    if (in.available() == 0) {
      mc.displayGuiScreen(screen);
    }
  }

  private static void showStandardInventory(Minecraft mc, DataInputStream in, int type)
      throws IOException {
    String title = Packet.readString(in, 64);
    boolean customName = in.readBoolean();
    if (type == SpectatorProtocol.FURNACE_SNAPSHOT) {
      TileEntityFurnace furnace = new TileEntityFurnace();
      if (customName) {
        furnace.setGuiDisplayName(title);
      }
      furnace.furnaceBurnTime = in.readInt();
      furnace.currentItemBurnTime = in.readInt();
      furnace.furnaceCookTime = in.readInt();
      for (int i = 0; i < 3; i++) {
        furnace.setInventorySlotContents(i, Packet.readItemStack(in));
      }
      if (in.available() == 0) {
        mc.displayGuiScreen(new GuiFurnace(mc.thePlayer.inventory, furnace));
      }
      return;
    }
    if (type != SpectatorProtocol.CHEST_SNAPSHOT) {
      return;
    }
    int size = in.readUnsignedByte();
    if (size < 9 || size > 54 || size % 9 != 0) {
      return;
    }
    InventoryBasic snapshot = new InventoryBasic(title, customName, size);
    for (int i = 0; i < size; i++) {
      snapshot.setInventorySlotContents(i, Packet.readItemStack(in));
    }
    if (in.available() == 0) {
      mc.displayGuiScreen(new GuiChest(mc.thePlayer.inventory, snapshot));
    }
  }

  private static final class ReadOnlyBeacon extends GuiBeacon {
    ReadOnlyBeacon(net.minecraft.entity.player.InventoryPlayer inventory, TileEntityBeacon beacon) {
      super(inventory, beacon);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
      if (button.id == -2) {
        super.actionPerformed(button);
      }
    }
  }
}
