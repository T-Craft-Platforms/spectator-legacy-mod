package org.spectatorlegacy;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;

final class InventorySnapshots {
  private InventorySnapshots() {}

  static void sendEntitySnapshot(EntityPlayerMP player, int id) {
    Entity entity = player.worldObj.getEntityByID(id);
    if (entity == null || entity.isDead || player.getDistanceSqToEntity(entity) > 36.0D) {
      return;
    }
    IInventory inventory;
    int kind;
    if (entity.getClass() == EntityHorse.class) {
      kind = SpectatorProtocol.HORSE;
      inventory =
          ReflectionHelper.getPrivateValue(
              EntityHorse.class, (EntityHorse) entity, "horseChest", "field_110296_bG");
    } else if (entity.getClass() == EntityMinecartChest.class) {
      kind = SpectatorProtocol.CHEST_MINECART;
      inventory = (IInventory) entity;
    } else if (entity.getClass() == EntityMinecartHopper.class) {
      kind = SpectatorProtocol.HOPPER_MINECART;
      inventory = (IInventory) entity;
    } else {
      return;
    }
    if (inventory == null) {
      return;
    }
    int size = inventory.getSizeInventory();
    if (!SpectatorProtocol.validEntityInventorySize(kind, size)) {
      return;
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(SpectatorProtocol.ENTITY_INVENTORY_SNAPSHOT);
      out.writeByte(kind);
      String title =
          kind == SpectatorProtocol.HORSE ? entity.getEntityName() : inventory.getInvName();
      Packet.writeString(snapshotTitle(title), out);
      out.writeBoolean(kind == SpectatorProtocol.HORSE || inventory.isInvNameLocalized());
      if (kind == SpectatorProtocol.HORSE) {
        EntityHorse horse = (EntityHorse) entity;
        out.writeByte(horse.getHorseType());
        out.writeInt(horse.getHorseVariant());
        out.writeInt(horse.getGrowingAge());
        out.writeBoolean(horse.isTame());
      }
      out.writeByte(size);
      for (int i = 0; i < size; i++) {
        Packet.writeItemStack(inventory.getStackInSlot(i), out);
      }
      out.flush();
      sendSnapshotPacket(player, bytes);
    } catch (IOException ignored) {
    }
  }

  static void sendSnapshot(EntityPlayerMP player, int x, int y, int z) {
    if (!player.worldObj.blockExists(x, y, z)
        || player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D) {
      return;
    }
    int id = player.worldObj.getBlockId(x, y, z);
    Block block = id > 0 ? Block.blocksList[id] : null;
    TileEntity tile = player.worldObj.getBlockTileEntity(x, y, z);
    IInventory inventory = null;
    if (block instanceof BlockChest) {
      inventory = ((BlockChest) block).getInventory(player.worldObj, x, y, z);
    } else if (block instanceof BlockEnderChest) {
      inventory = player.getInventoryEnderChest();
    } else if (tile instanceof IInventory
        && tile.getClass().getName().startsWith("net.minecraft.tileentity.")) {
      inventory = (IInventory) tile;
    }
    if (inventory == null) {
      return;
    }
    int kind = specialInventoryKind(tile);
    if (kind >= 0) {
      sendSpecialSnapshot(player, inventory, tile, kind);
    } else if (tile instanceof TileEntityFurnace) {
      sendFurnaceSnapshot(player, (TileEntityFurnace) tile);
    } else {
      sendChestSnapshot(player, inventory);
    }
  }

  private static int specialInventoryKind(TileEntity tile) {
    if (tile instanceof TileEntityDropper) {
      return SpectatorProtocol.DROPPER;
    }
    if (tile instanceof TileEntityDispenser) {
      return SpectatorProtocol.DISPENSER;
    }
    if (tile instanceof TileEntityHopper) {
      return SpectatorProtocol.HOPPER;
    }
    if (tile instanceof TileEntityBrewingStand) {
      return SpectatorProtocol.BREWING_STAND;
    }
    if (tile instanceof TileEntityBeacon) {
      return SpectatorProtocol.BEACON;
    }
    return -1;
  }

  private static void sendSpecialSnapshot(
      EntityPlayerMP player, IInventory inventory, TileEntity tile, int kind) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(SpectatorProtocol.SPECIAL_INVENTORY_SNAPSHOT);
      out.writeByte(kind);
      Packet.writeString(snapshotTitle(inventory.getInvName()), out);
      out.writeBoolean(inventory.isInvNameLocalized());
      if (kind == SpectatorProtocol.BREWING_STAND) {
        out.writeInt(((TileEntityBrewingStand) tile).getBrewTime());
      }
      if (kind == SpectatorProtocol.BEACON) {
        TileEntityBeacon beacon = (TileEntityBeacon) tile;
        out.writeInt(beacon.getLevels());
        out.writeInt(beacon.getPrimaryEffect());
        out.writeInt(beacon.getSecondaryEffect());
      }
      for (int i = 0; i < inventory.getSizeInventory(); i++) {
        Packet.writeItemStack(inventory.getStackInSlot(i), out);
      }
      out.flush();
      sendSnapshotPacket(player, bytes);
    } catch (IOException ignored) {
    }
  }

  private static void sendFurnaceSnapshot(EntityPlayerMP player, TileEntityFurnace furnace) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(SpectatorProtocol.FURNACE_SNAPSHOT);
      Packet.writeString(furnace.getInvName(), out);
      out.writeBoolean(furnace.isInvNameLocalized());
      out.writeInt(furnace.furnaceBurnTime);
      out.writeInt(furnace.currentItemBurnTime);
      out.writeInt(furnace.furnaceCookTime);
      for (int i = 0; i < 3; i++) {
        Packet.writeItemStack(furnace.getStackInSlot(i), out);
      }
      out.flush();
      sendSnapshotPacket(player, bytes);
    } catch (IOException ignored) {
    }
  }

  private static void sendChestSnapshot(EntityPlayerMP player, IInventory inventory) {
    int count = Math.min(inventory.getSizeInventory(), 54);
    int size = Math.max(9, ((count + 8) / 9) * 9);
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(SpectatorProtocol.CHEST_SNAPSHOT);
      Packet.writeString(snapshotTitle(inventory.getInvName()), out);
      out.writeBoolean(inventory.isInvNameLocalized());
      out.writeByte(size);
      for (int i = 0; i < size; i++) {
        Packet.writeItemStack(i < count ? inventory.getStackInSlot(i) : null, out);
      }
      out.flush();
      sendSnapshotPacket(player, bytes);
    } catch (IOException ignored) {
    }
  }

  private static String snapshotTitle(String title) {
    if (title == null) {
      return "Container";
    }
    return title.length() > 64 ? title.substring(0, 64) : title;
  }

  private static void sendSnapshotPacket(EntityPlayerMP player, ByteArrayOutputStream bytes) {
    if (bytes.size() <= 32760) {
      PacketDispatcher.sendPacketToPlayer(
          PacketDispatcher.getPacket(SpectatorProtocol.CHANNEL, bytes.toByteArray()),
          (Player) player);
    }
  }
}
