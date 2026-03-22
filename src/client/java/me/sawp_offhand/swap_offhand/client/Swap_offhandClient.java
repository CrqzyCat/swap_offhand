package me.sawp_offhand.swap_offhand.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public class Swap_offhandClient implements ClientModInitializer {

    private static KeyBinding swapKey;

    @Override
    public void onInitializeClient() {
        swapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Swap Offhand",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) return;

            while (swapKey.wasPressed()) {
                ItemStack offhandStack = client.player.getOffHandStack();
                int bestSlot = -1;

                if (offhandStack.isOf(Items.TOTEM_OF_UNDYING)) {
                    ItemStack bestShield = null;
                    for (int i = 0; i < 36; i++) {
                        ItemStack current = client.player.getInventory().getStack(i);
                        if (current.isOf(Items.SHIELD)) {
                            if (bestShield == null || current.getDamage() < bestShield.getDamage()) {
                                bestShield = current;
                                bestSlot = i;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < 36; i++) {
                        if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                            bestSlot = i;
                            break;
                        }
                    }
                }

                if (bestSlot != -1) {
                    // SERVER-FIX: Slots above 9 (inventory) must be adjusted for clickSlot
                    int serverSlot = bestSlot;
                    if (bestSlot < 9) {
                        // HOTBAR-LOGIC (Packet method is safest here)
                        int originalSlot = client.player.getInventory().getSelectedSlot();
                        client.player.getInventory().setSelectedSlot(bestSlot);
                        client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                BlockPos.ORIGIN, Direction.DOWN));
                        client.player.getInventory().setSelectedSlot(originalSlot);
                    } else {
                        // INVENTORY-LOGIC (Improved click flow)
                        // First, pick up the item
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, serverSlot, 0, SlotActionType.PICKUP, client.player);
                        // Then, place it in the offhand (Slot 45 in most server mappings for PlayerContainer)
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, client.player);
                        // If there was something in the offhand, put the old item back into the original slot
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, serverSlot, 0, SlotActionType.PICKUP, client.player);
                    }
                }
            }
        });
    }
}