package org.spectator164;

import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.command.CommandGameMode;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemInWorldManager;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.world.EnumGameType;
import net.minecraftforge.common.EnumHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@Mod(modid = "spectator164", name = "Spectator 1.6.4", version = "0.1.0-dev")
@NetworkMod(
    clientSideRequired = true,
    serverSideRequired = true,
    channels = {SpectatorProtocol.CHANNEL},
    packetHandler = SpectatorMod.Packets.class)
public final class SpectatorMod implements ITickHandler {
  @SidedProxy(
      clientSide = "org.spectator164.ClientProxy",
      serverSide = "org.spectator164.CommonProxy")
  public static CommonProxy proxy;

  private static final String KEY = "spectator164Active";
  private static boolean clientActive;
  private static EnumGameType spectatorMode;
  private static final Map<EntityPlayerMP, Entity> cameras =
      new WeakHashMap<EntityPlayerMP, Entity>();

  static boolean clientActive() {
    return clientActive;
  }

  static void clearClientState() {
    clientActive = false;
  }

  static void setClientMode(EnumGameType mode) {
    clientActive = mode.getID() == 3;
  }

  static boolean active(EntityPlayer p) {
    return p.worldObj.isRemote
        ? proxy.isClientSpectator(p)
        : p instanceof EntityPlayerMP
            && ((EntityPlayerMP) p).theItemInWorldManager.getGameType().getID() == 3;
  }

  @EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    spectatorMode =
        (EnumGameType)
            EnumHelper.addEnum(
                EnumGameType.class,
                "SPECTATOR",
                new Class<?>[] {Integer.TYPE, String.class},
                new Object[] {Integer.valueOf(3), "spectator"});
    if (spectatorMode == null
        || EnumGameType.getByID(3) != spectatorMode
        || !"spectator".equals(spectatorMode.getName())) {
      throw new IllegalStateException("Could not register spectator game mode");
    }
    LanguageRegistry.instance()
        .addStringLocalization("gameMode.spectator", "en_US", "Spectator Mode");
  }

  @EventHandler
  public void init(FMLInitializationEvent e) {
    MinecraftForge.EVENT_BUS.register(this);
    proxy.init();
    GameRegistry.registerPlayerTracker(new PlayerTracker());
    TickRegistry.registerTickHandler(this, Side.SERVER);
    TickRegistry.registerTickHandler(this, Side.CLIENT);
  }

  @EventHandler
  public void serverStart(FMLServerStartingEvent e) {
    e.registerServerCommand(new Command());
  }

  public static class Command extends CommandGameMode {
    @Override
    protected EnumGameType getGameModeFromCommand(ICommandSender sender, String name) {
      return "spectator".equalsIgnoreCase(name)
          ? spectatorMode
          : super.getGameModeFromCommand(sender, name);
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
      if (args.length == 1) {
        return getListOfStringsMatchingLastWord(
            args, new String[] {"survival", "creative", "adventure", "spectator"});
      }
      return super.addTabCompletionOptions(sender, args);
    }
  }

  public static void configureCapabilities(EnumGameType mode, PlayerCapabilities capabilities) {
    if (mode.getID() != 3) {
      return;
    }
    capabilities.allowFlying = true;
    capabilities.isFlying = true;
    capabilities.disableDamage = true;
    capabilities.isCreativeMode = false;
    capabilities.allowEdit = false;
  }

  public static void changingMode(ItemInWorldManager manager, EnumGameType next) {
    EntityPlayerMP p = manager.thisPlayerMP;
    if (p == null) {
      return;
    }
    int oldId = manager.getGameType().getID();
    boolean enabled = next.getID() == 3;
    if (enabled && oldId != 3 && oldId >= 0) {
      p.getEntityData().setInteger("spectator164PreviousMode", oldId);
    }
    if (oldId == 3 && !enabled) {
      stopCamera(p);
      p.noClip = false;
      p.setInvisible(false);
    }
    if (enabled) {
      p.noClip = true;
      p.fallDistance = 0;
      p.setInvisible(true);
      p.clearActivePotions();
    }
    if (p.playerNetServerHandler != null && (oldId == 3) != enabled) {
      sync(p, enabled);
      broadcastState(p, enabled);
    }
  }

  private static void cameraPacket(EntityPlayerMP p, int id) {
    PacketDispatcher.sendPacketToPlayer(
        PacketDispatcher.getPacket(
            SpectatorProtocol.CHANNEL,
            ByteBuffer.allocate(5).put(SpectatorProtocol.CAMERA_TARGET).putInt(id).array()),
        (Player) p);
  }

  private static void stopCamera(EntityPlayerMP p) {
    Entity target = cameras.remove(p);
    if (target != null) {
      p.rotationYaw = p.prevRotationYaw = SpectatorHooks.cameraYaw(target);
      p.rotationPitch = p.prevRotationPitch = target.rotationPitch;
      cameraPacket(p, -1);
    }
  }

  static void sync(EntityPlayerMP p, boolean enabled) {
    PacketDispatcher.sendPacketToPlayer(
        PacketDispatcher.getPacket(
            SpectatorProtocol.CHANNEL, new byte[] {(byte) (enabled ? 1 : 0)}),
        (Player) p);
  }

  static void broadcastState(EntityPlayerMP p, boolean enabled) {
    byte[] data =
        ByteBuffer.allocate(6)
            .put(SpectatorProtocol.SPECTATOR_STATE)
            .putInt(p.entityId)
            .put((byte) (enabled ? 1 : 0))
            .array();
    PacketDispatcher.sendPacketToAllInDimension(
        PacketDispatcher.getPacket(SpectatorProtocol.CHANNEL, data), p.dimension);
  }

  public static class Packets implements IPacketHandler {
    public void onPacketData(
        INetworkManager manager, Packet250CustomPayload packet, Player player) {
      if (packet.data == null) {
        return;
      }
      EntityPlayer p = (EntityPlayer) player;
      if (p.worldObj.isRemote) {
        handleClientPacket(packet);
      } else {
        handleServerPacket(packet, p);
      }
    }

    private void handleClientPacket(Packet250CustomPayload packet) {
      if (packet.data.length == 1) {
        clientActive = packet.data[0] != 0;
      } else if (packet.data.length == 6 && packet.data[0] == SpectatorProtocol.SPECTATOR_STATE) {
        proxy.spectatorState(ByteBuffer.wrap(packet.data, 1, 4).getInt(), packet.data[5] != 0);
      } else if (packet.data.length > 1
          && (packet.data[0] == SpectatorProtocol.CHEST_SNAPSHOT
              || packet.data[0] == SpectatorProtocol.FURNACE_SNAPSHOT
              || packet.data[0] == SpectatorProtocol.SPECIAL_INVENTORY_SNAPSHOT
              || packet.data[0] == SpectatorProtocol.ENTITY_INVENTORY_SNAPSHOT)) {
        proxy.showInventorySnapshot(packet.data);
      } else if (packet.data.length == 5 && packet.data[0] == SpectatorProtocol.CAMERA_TARGET) {
        proxy.cameraTarget(ByteBuffer.wrap(packet.data, 1, 4).getInt());
      }
    }

    private void handleServerPacket(Packet250CustomPayload packet, EntityPlayer p) {
      if (packet.data.length == 1 && packet.data[0] == SpectatorProtocol.TOGGLE_MODE) {
        EntityPlayerMP playerMP = (EntityPlayerMP) p;
        int mode = active(p) ? p.getEntityData().getInteger("spectator164PreviousMode") : 3;
        if (active(p) && (mode < 0 || mode > 2)) {
          mode = 0;
        }
        playerMP.mcServer.getCommandManager().executeCommand(p, "gamemode " + mode);
      } else if (packet.data.length == 5
          && packet.data[0] == SpectatorProtocol.REQUEST_CAMERA
          && active(p)) {
        EntityPlayerMP spectator = (EntityPlayerMP) p;
        int id = ByteBuffer.wrap(packet.data, 1, 4).getInt();
        if (id == -1) {
          stopCamera(spectator);
        } else {
          Entity target = p.worldObj.getEntityByID(id);
          if (target != null
              && target != p
              && !target.isDead
              && !SpectatorHooks.isSpectator(target)
              && p.getDistanceSqToEntity(target) <= 36.0D) {
            cameras.put(spectator, target);
            cameraPacket(spectator, id);
          }
        }
      } else if (packet.data.length == 5
          && packet.data[0] == SpectatorProtocol.REQUEST_ENTITY_INVENTORY
          && active(p)) {
        InventorySnapshots.sendEntitySnapshot(
            (EntityPlayerMP) p, ByteBuffer.wrap(packet.data, 1, 4).getInt());
      } else if (packet.data.length == 13
          && packet.data[0] == SpectatorProtocol.REQUEST_BLOCK_INVENTORY
          && active(p)) {
        ByteBuffer request = ByteBuffer.wrap(packet.data, 1, 12);
        InventorySnapshots.sendSnapshot(
            (EntityPlayerMP) p, request.getInt(), request.getInt(), request.getInt());
      } else if (packet.data.length == 5
          && packet.data[0] == SpectatorProtocol.TELEPORT_TO_PLAYER
          && active(p)) {
        int id = ByteBuffer.wrap(packet.data, 1, 4).getInt();
        Entity target = p.worldObj.getEntityByID(id);
        if (target instanceof EntityPlayerMP && target != p && !target.isDead) {
          EntityPlayerMP spectator = (EntityPlayerMP) p;
          stopCamera(spectator);
          spectator.playerNetServerHandler.setPlayerLocation(
              target.posX, target.posY, target.posZ, target.rotationYaw, target.rotationPitch);
        }
      }
    }
  }

  private static void restore(EntityPlayer p) {
    if (!active(p)) {
      return;
    }
    p.noClip = true;
    p.fallDistance = 0;
    p.capabilities.allowFlying = true;
    p.capabilities.isFlying = true;
    p.capabilities.disableDamage = true;
    p.setInvisible(true);
  }

  @ForgeSubscribe
  public void joinWorld(EntityJoinWorldEvent event) {
    if (!event.world.isRemote && event.entity instanceof EntityPlayer) {
      restore((EntityPlayer) event.entity);
    }
  }

  public static class PlayerTracker implements IPlayerTracker {
    private void update(EntityPlayer player) {
      EntityPlayerMP p = (EntityPlayerMP) player;
      restore(p);
      p.sendPlayerAbilities();
      sync(p, active(p));
      broadcastState(p, active(p));
    }

    public void onPlayerLogin(EntityPlayer player) {
      EntityPlayerMP p = (EntityPlayerMP) player;
      if (p.getEntityData().getBoolean(KEY)) {
        p.getEntityData().removeTag(KEY);
        if (!active(p) && !p.mcServer.getForceGamemode()) {
          p.setGameType(spectatorMode);
        }
      }
      update(p);
    }

    public void onPlayerChangedDimension(EntityPlayer player) {
      stopCamera((EntityPlayerMP) player);
      update(player);
    }

    public void onPlayerRespawn(EntityPlayer player) {
      stopCamera((EntityPlayerMP) player);
      update(player);
    }

    public void onPlayerLogout(EntityPlayer player) {
      cameras.remove((EntityPlayerMP) player);
    }
  }

  public void tickStart(EnumSet<TickType> type, Object... data) {
    EntityPlayer p = (EntityPlayer) data[0];
    if (!p.worldObj.isRemote && p.ticksExisted % 40 == 0) {
      sync((EntityPlayerMP) p, active(p));
    }
    if (active(p)) {
      p.noClip = true;
      p.fallDistance = 0;
      p.extinguish();
      p.capabilities.isFlying = true;
      p.capabilities.allowFlying = true;
      if (!p.worldObj.isRemote) {
        p.setInvisible(true);
        if (p.ticksExisted % 40 == 0) {
          broadcastState((EntityPlayerMP) p, true);
        }
      }
    } else if (p.worldObj.isRemote) {
      p.noClip = false;
    }
  }

  public void tickEnd(EnumSet<TickType> type, Object... data) {
    EntityPlayer p = (EntityPlayer) data[0];
    if (p.worldObj.isRemote) {
      return;
    }
    EntityPlayerMP spectator = (EntityPlayerMP) p;
    Entity target = cameras.get(spectator);
    if (target == null) {
      return;
    }
    if (!active(p)
        || target.isDead
        || target.worldObj != p.worldObj
        || SpectatorHooks.isSpectator(target)) {
      stopCamera(spectator);
      return;
    }
    double x = SpectatorHooks.cameraX(target),
        y = SpectatorHooks.cameraY(target) - p.getEyeHeight(),
        z = SpectatorHooks.cameraZ(target);
    if (p.getDistanceSq(x, y, z) > 64.0D) {
      spectator.playerNetServerHandler.setPlayerLocation(
          x, y, z, SpectatorHooks.cameraYaw(target), target.rotationPitch);
    } else {
      p.setPositionAndRotation(x, y, z, SpectatorHooks.cameraYaw(target), target.rotationPitch);
    }
    p.motionX = p.motionY = p.motionZ = 0;
  }

  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.PLAYER);
  }

  public String getLabel() {
    return "Spectator164";
  }

  @ForgeSubscribe
  public void interact(PlayerInteractEvent e) {
    if (!active(e.entityPlayer)) {
      return;
    }
    e.setCanceled(true);
  }

  @ForgeSubscribe
  public void attack(AttackEntityEvent e) {
    if (active(e.entityPlayer)) {
      e.setCanceled(true);
    }
  }

  @ForgeSubscribe
  public void interactEntity(EntityInteractEvent e) {
    if (active(e.entityPlayer)) {
      e.setCanceled(true);
    }
  }

  @ForgeSubscribe
  public void pickup(EntityItemPickupEvent e) {
    if (active(e.entityPlayer)) {
      e.setCanceled(true);
    }
  }

  @ForgeSubscribe
  public void damage(LivingAttackEvent e) {
    if (e.entityLiving instanceof EntityPlayer && active((EntityPlayer) e.entityLiving)) {
      e.setCanceled(true);
    }
  }
}
