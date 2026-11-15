package org.spectatorlegacy.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class SpectatorTransformer implements IClassTransformer, Opcodes {
  private static final int PRESSURE_PLATE = 0;
  private static final int BLOCK_COLLISIONS = 1;
  private static final int MOVEMENT_COLLISIONS = 2;
  private static final int INVENTORY_PACKETS = 3;
  private static final int GUI_CLICK = 4;
  private static final int DROP_PACKET = 5;
  private static final int ENTITY_COLLISIONS = 6;
  private static final int HOTBAR_SLOT = 7;
  private static final int HUD_TOOLTIP = 8;
  private static final int PLAYER_PICKUP = 9;
  private static final int COLLISION_ELIGIBILITY = 10;
  private static final int CAPABILITIES = 11;
  private static final int SERVER_GAME_MODE = 12;
  private static final int CLIENT_GAME_MODE = 13;
  private static final int DEBUG_INFO = 14;
  private static final int DEBUG_PROFILER = 15;
  private static final int KEYBOARD_EVENT = 16;
  private static final int LIQUID_CHECKS = 17;
  private static final int WEB_COLLISION = 18;
  private static final int BLOCK_FACE_CHECKS = 19;

  public byte[] transform(String name, String transformedName, byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    final boolean entity = "net.minecraft.entity.Entity".equals(transformedName);
    final boolean server = "net.minecraft.network.NetServerHandler".equals(transformedName);
    final boolean gui = "net.minecraft.client.gui.inventory.GuiContainer".equals(transformedName);
    final boolean living = "net.minecraft.entity.EntityLivingBase".equals(transformedName);
    final boolean potion = "net.minecraft.potion.Potion".equals(transformedName);
    final boolean playerClass = "net.minecraft.entity.player.EntityPlayer".equals(transformedName);
    final boolean minecart = "net.minecraft.entity.item.EntityMinecart".equals(transformedName);
    final boolean minecraft = "net.minecraft.client.Minecraft".equals(transformedName);
    final boolean forgeHud = "net.minecraftforge.client.GuiIngameForge".equals(transformedName);
    final boolean gameType = "net.minecraft.world.EnumGameType".equals(transformedName);
    final boolean gameManager = "net.minecraft.item.ItemInWorldManager".equals(transformedName);
    final boolean controller =
        "net.minecraft.client.multiplayer.PlayerControllerMP".equals(transformedName);
    final boolean renderBlocks =
        "net.minecraft.client.renderer.RenderBlocks".equals(transformedName);
    if (!entity
        && !server
        && !gui
        && !living
        && !potion
        && !playerClass
        && !minecart
        && !minecraft
        && !forgeHud
        && !gameType
        && !gameManager
        && !controller
        && !renderBlocks) {
      return bytes;
    }
    final int[] changes = new int[BLOCK_FACE_CHECKS + 1];
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
    reader.accept(
        new ClassVisitor(ASM4, writer) {
          @Override
          public MethodVisitor visitMethod(
              int access, final String method, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, method, desc, signature, exceptions);
            final boolean pressure =
                entity
                    && desc.equals("()Z")
                    && (method.equals("doesEntityNotTriggerPressurePlate")
                        || method.equals("func_82144_au"));
            final boolean collisions =
                entity
                    && desc.equals("()V")
                    && (method.equals("doBlockCollisions") || method.equals("func_70017_D"));
            final boolean ladder =
                living
                    && desc.equals("()Z")
                    && (method.equals("isOnLadder") || method.equals("func_70617_f_"));
            final boolean liquid =
                entity
                    && ((desc.equals("()Z")
                            && (method.equals("isInWater")
                                || method.equals("func_70090_H")
                                || method.equals("handleWaterMovement")
                                || method.equals("func_70072_I")
                                || method.equals("handleLavaMovement")
                                || method.equals("func_70058_J")))
                        || (desc.equals("(Lnet/minecraft/block/material/Material;)Z")
                            && (method.equals("isInsideOfMaterial")
                                || method.equals("func_70055_a"))));
            final boolean web =
                entity
                    && desc.equals("()V")
                    && (method.equals("setInWeb") || method.equals("func_70110_aj"));
            final boolean potionApplicable =
                living
                    && desc.equals("(Lnet/minecraft/potion/PotionEffect;)Z")
                    && (method.equals("isPotionApplicable") || method.equals("func_70687_e"));
            final boolean instantPotion =
                potion
                    && desc.equals(
                        "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;ID)V");
            final boolean flying =
                server && (method.equals("handleFlying") || method.equals("func_72498_a"));
            final boolean inventory =
                server
                    && (method.equals("handleWindowClick")
                        || method.equals("func_72523_a")
                        || method.equals("handleEnchantItem")
                        || method.equals("func_72479_a")
                        || method.equals("handleCreativeSetSlot")
                        || method.equals("func_72464_a"));
            final boolean drop =
                server && (method.equals("handleBlockDig") || method.equals("func_72510_a"));
            final boolean collisionEligibility =
                living
                    && desc.equals("()Z")
                    && (method.equals("canBeCollidedWith")
                        || method.equals("func_70067_L")
                        || method.equals("canBePushed")
                        || method.equals("func_70104_M"));
            final boolean pickup =
                playerClass
                    && (method.equals("collideWithPlayer") || method.equals("func_71044_o"))
                    && desc.equals("(Lnet/minecraft/entity/Entity;)V");
            final boolean entityCollision =
                (entity || minecart)
                    && (method.equals("applyEntityCollision") || method.equals("func_70108_f"))
                    && desc.equals("(Lnet/minecraft/entity/Entity;)V");
            final boolean hotbar =
                minecraft
                    && (method.equals("runTick") || method.equals("func_71407_l"))
                    && desc.equals("()V");
            final boolean tooltip =
                forgeHud && method.equals("renderToolHightlight") && desc.equals("(II)V");
            final boolean guiClick =
                gui && (method.equals("handleMouseClick") || method.equals("func_74191_a"));
            final boolean particles =
                (entity || living || playerClass) && (access & ACC_STATIC) == 0;
            final boolean capabilities =
                gameType
                    && (method.equals("configurePlayerCapabilities")
                        || method.equals("func_77147_a"))
                    && desc.equals("(Lnet/minecraft/entity/player/PlayerCapabilities;)V");
            final boolean managerMode =
                gameManager
                    && (method.equals("setGameType") || method.equals("func_73076_a"))
                    && desc.equals("(Lnet/minecraft/world/EnumGameType;)V");
            final boolean clientMode =
                controller
                    && (method.equals("setGameType") || method.equals("func_78746_a"))
                    && desc.equals("(Lnet/minecraft/world/EnumGameType;)V");
            final boolean standardRender =
                renderBlocks
                    && (method.equals("renderStandardBlockWithAmbientOcclusion")
                        || method.equals("func_78578_a")
                        || method.equals("renderStandardBlockWithAmbientOcclusionPartial")
                        || method.equals("func_102027_b")
                        || method.equals("renderStandardBlockWithColorMultiplier")
                        || method.equals("func_78609_c"));
            if (!capabilities
                && !managerMode
                && !clientMode
                && !particles
                && !pressure
                && !collisions
                && !ladder
                && !liquid
                && !web
                && !potionApplicable
                && !instantPotion
                && !flying
                && !inventory
                && !guiClick
                && !drop
                && !entityCollision
                && !collisionEligibility
                && !pickup
                && !hotbar
                && !tooltip
                && !standardRender) {
              return mv;
            }
            if (liquid) {
              changes[LIQUID_CHECKS]++;
            }
            if (web) {
              changes[WEB_COLLISION]++;
            }
            if (capabilities) {
              changes[CAPABILITIES]++;
            }
            if (managerMode) {
              changes[SERVER_GAME_MODE]++;
            }
            if (clientMode) {
              changes[CLIENT_GAME_MODE]++;
            }
            if (inventory) {
              changes[INVENTORY_PACKETS]++;
            }
            if (guiClick) {
              changes[GUI_CLICK]++;
            }
            if (drop) {
              changes[DROP_PACKET]++;
            }
            if (entityCollision) {
              changes[ENTITY_COLLISIONS]++;
            }
            if (collisionEligibility) {
              changes[COLLISION_ELIGIBILITY]++;
            }
            if (pickup) {
              changes[PLAYER_PICKUP]++;
            }
            if (tooltip) {
              changes[HUD_TOOLTIP]++;
            }
            if (pressure) {
              changes[PRESSURE_PLATE]++;
            }
            if (collisions) {
              changes[BLOCK_COLLISIONS]++;
            }
            return new MethodVisitor(ASM4, mv) {
              @Override
              public void visitCode() {
                super.visitCode();
                if (managerMode) {
                  mv.visitVarInsn(ALOAD, 0);
                  mv.visitVarInsn(ALOAD, 1);
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorMod",
                      "changingMode",
                      "(Lnet/minecraft/item/ItemInWorldManager;Lnet/minecraft/world/EnumGameType;)V");
                }
                if (clientMode) {
                  mv.visitVarInsn(ALOAD, 1);
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/ClientHooks",
                      "setGameType",
                      "(Lnet/minecraft/world/EnumGameType;)V");
                }
                if (collisionEligibility || ladder || liquid || potionApplicable) {
                  skipForSpectator(0, ICONST_0);
                }
                if (instantPotion) {
                  skipForSpectator(2, RETURN);
                }
                if (entityCollision) {
                  Label normal = new Label();
                  mv.visitVarInsn(ALOAD, 0);
                  mv.visitVarInsn(ALOAD, 1);
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorHooks",
                      "blockEntityCollision",
                      "(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Z");
                  mv.visitJumpInsn(IFEQ, normal);
                  mv.visitInsn(RETURN);
                  mv.visitLabel(normal);
                  mv.visitFrame(F_SAME, 0, null, 0, null);
                }
                if (tooltip) {
                  Label normal = new Label();
                  mv.visitMethodInsn(
                      INVOKESTATIC, "org/spectatorlegacy/ClientHooks", "isSpectating", "()Z");
                  mv.visitJumpInsn(IFEQ, normal);
                  mv.visitInsn(RETURN);
                  mv.visitLabel(normal);
                  mv.visitFrame(F_SAME, 0, null, 0, null);
                }
                if (drop) {
                  Label normal = new Label();
                  mv.visitVarInsn(ALOAD, 0);
                  mv.visitVarInsn(ALOAD, 1);
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorHooks",
                      "blockDrop",
                      "(Lnet/minecraft/network/NetServerHandler;Lnet/minecraft/network/packet/Packet14BlockDig;)Z");
                  mv.visitJumpInsn(IFEQ, normal);
                  mv.visitInsn(RETURN);
                  mv.visitLabel(normal);
                  mv.visitFrame(F_SAME, 0, null, 0, null);
                }
                if (inventory || guiClick) {
                  Label normal = new Label();
                  if (inventory) {
                    mv.visitVarInsn(ALOAD, 0);
                  }
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      inventory
                          ? "org/spectatorlegacy/SpectatorHooks"
                          : "org/spectatorlegacy/ClientHooks",
                      inventory ? "blockInventoryChange" : "isSpectating",
                      inventory ? "(Lnet/minecraft/network/NetServerHandler;)Z" : "()Z");
                  mv.visitJumpInsn(IFEQ, normal);
                  mv.visitInsn(RETURN);
                  mv.visitLabel(normal);
                  mv.visitFrame(F_SAME, 0, null, 0, null);
                }
                if (pressure || collisions || pickup || web) {
                  skipForSpectator(0, pressure ? ICONST_1 : RETURN);
                }
              }

              private void skipForSpectator(int entityLocal, int result) {
                Label normal = new Label();
                mv.visitVarInsn(ALOAD, entityLocal);
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    "org/spectatorlegacy/SpectatorHooks",
                    "isSpectator",
                    "(Lnet/minecraft/entity/Entity;)Z");
                mv.visitJumpInsn(IFEQ, normal);
                if (result != RETURN) {
                  mv.visitInsn(result);
                }
                mv.visitInsn(result == RETURN ? RETURN : IRETURN);
                mv.visitLabel(normal);
                mv.visitFrame(F_SAME, 0, null, 0, null);
              }

              @Override
              public void visitInsn(int opcode) {
                if (capabilities && opcode == RETURN) {
                  mv.visitVarInsn(ALOAD, 0);
                  mv.visitVarInsn(ALOAD, 1);
                  mv.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorMod",
                      "configureCapabilities",
                      "(Lnet/minecraft/world/EnumGameType;Lnet/minecraft/entity/player/PlayerCapabilities;)V");
                }
                super.visitInsn(opcode);
              }

              @Override
              public void visitFieldInsn(
                  int opcode, String owner, String field, String descriptor) {
                if (hotbar
                    && opcode == PUTFIELD
                    && owner.equals("net/minecraft/client/settings/GameSettings")
                    && descriptor.equals("Z")
                    && (field.equals("showDebugInfo")
                        || field.equals("field_74330_P")
                        || field.equals("showDebugProfilerChart")
                        || field.equals("field_74329_Q"))) {
                  boolean info = field.equals("showDebugInfo") || field.equals("field_74330_P");
                  changes[info ? DEBUG_INFO : DEBUG_PROFILER]++;
                  super.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/ClientHooks",
                      info ? "setDebugInfo" : "setDebugProfiler",
                      "(Lnet/minecraft/client/settings/GameSettings;Z)V");
                } else if (hotbar
                    && opcode == PUTFIELD
                    && owner.equals("net/minecraft/entity/player/InventoryPlayer")
                    && (field.equals("currentItem") || field.equals("field_70461_c"))
                    && descriptor.equals("I")) {
                  changes[HOTBAR_SLOT]++;
                  super.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/ClientHooks",
                      "selectHotbarSlot",
                      "(Lnet/minecraft/entity/player/InventoryPlayer;I)V");
                } else {
                  super.visitFieldInsn(opcode, owner, field, descriptor);
                }
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String target, String descriptor) {
                if (standardRender
                    && opcode == INVOKEVIRTUAL
                    && owner.equals("net/minecraft/block/Block")
                    && (target.equals("shouldSideBeRendered") || target.equals("func_71877_c"))
                    && descriptor.equals("(Lnet/minecraft/world/IBlockAccess;IIII)Z")) {
                  changes[BLOCK_FACE_CHECKS]++;
                  super.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/ClientHooks",
                      "shouldRenderSide",
                      "(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Z");
                } else if (hotbar
                    && opcode == INVOKESTATIC
                    && owner.equals("org/lwjgl/input/Keyboard")
                    && target.equals("next")
                    && descriptor.equals("()Z")) {
                  changes[KEYBOARD_EVENT]++;
                  super.visitMethodInsn(
                      INVOKESTATIC, "org/spectatorlegacy/ClientHooks", "nextKeyboardEvent", "()Z");
                } else if (particles
                    && opcode == INVOKEVIRTUAL
                    && owner.equals("net/minecraft/world/World")
                    && (target.equals("spawnParticle") || target.equals("func_72869_a"))
                    && descriptor.equals("(Ljava/lang/String;DDDDDD)V")) {
                  super.visitVarInsn(ALOAD, 0);
                  super.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorHooks",
                      "spawnParticle",
                      "(Lnet/minecraft/world/World;Ljava/lang/String;DDDDDDLnet/minecraft/entity/Entity;)V");
                } else if (flying
                    && opcode == INVOKEVIRTUAL
                    && owner.equals("net/minecraft/world/WorldServer")
                    && (target.equals("getCollidingBoundingBoxes") || target.equals("func_72945_a"))
                    && descriptor.equals(
                        "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;")) {
                  changes[MOVEMENT_COLLISIONS]++;
                  super.visitMethodInsn(
                      INVOKESTATIC,
                      "org/spectatorlegacy/SpectatorHooks",
                      "movementCollisions",
                      "(Lnet/minecraft/world/WorldServer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;");
                } else {
                  super.visitMethodInsn(opcode, owner, target, descriptor);
                }
              }
            };
          }
        },
        0);
    if ((entity
            && (changes[PRESSURE_PLATE] != 1
                || changes[BLOCK_COLLISIONS] != 1
                || changes[ENTITY_COLLISIONS] != 1
                || changes[LIQUID_CHECKS] != 4
                || changes[WEB_COLLISION] != 1))
        || (server
            && (changes[MOVEMENT_COLLISIONS] != 2
                || changes[INVENTORY_PACKETS] != 3
                || changes[DROP_PACKET] != 1))
        || (gui && changes[GUI_CLICK] != 1)
        || (minecart && changes[ENTITY_COLLISIONS] != 1)
        || (playerClass && changes[PLAYER_PICKUP] != 1)
        || (living && changes[COLLISION_ELIGIBILITY] != 2)
        || (minecraft
            && (changes[HOTBAR_SLOT] != 1
                || changes[DEBUG_INFO] != 1
                || changes[DEBUG_PROFILER] != 1
                || changes[KEYBOARD_EVENT] != 1))
        || (forgeHud && changes[HUD_TOOLTIP] != 1)
        || (gameType && changes[CAPABILITIES] != 1)
        || (gameManager && changes[SERVER_GAME_MODE] != 1)
        || (controller && changes[CLIENT_GAME_MODE] != 1)
        || (renderBlocks && changes[BLOCK_FACE_CHECKS] != 18)) {
      throw new IllegalStateException(
          "SpectatorLegacy could not apply the expected hooks to "
              + transformedName
              + ": "
              + changes[PRESSURE_PLATE]
              + ","
              + changes[BLOCK_COLLISIONS]
              + ","
              + changes[MOVEMENT_COLLISIONS]);
    }
    System.out.println("SpectatorLegacy hooks verified for " + transformedName);
    return writer.toByteArray();
  }
}
