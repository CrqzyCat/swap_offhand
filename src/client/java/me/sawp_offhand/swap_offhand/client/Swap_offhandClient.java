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

/**
 * Client-side mod that swaps the offhand item intelligently.
 *
 * <p>Behavior:
 * <ul>
 *     <li>If holding a Totem → switches to the best shield available</li>
 *     <li>If not holding a Totem → equips a Totem if available</li>
 * </ul>
 *
 * <p>The mod distinguishes between hotbar and inventory handling,
 * because different packet strategies are required for reliability.
 */
public class Swap_offhandClient implements ClientModInitializer {

    /** Key binding used to trigger the offhand swap */
    private static KeyBinding swapKey;

    @Override
    public void onInitializeClient() {

        /** Register keybind (default: G) */
        swapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Swap Offhand",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyBinding.Category.MISC
        ));

        /**
         * Main tick loop:
         * Handles key press detection and swapping logic.
         */
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) return;

            /** Process all queued key presses */
            while (swapKey.wasPressed()) {

                /** Current item in offhand */
                ItemStack offhandStack = client.player.getOffHandStack();

                /** Best slot found for swapping */
                int bestSlot = -1;

                /**
                 * If offhand currently holds a Totem:
                 * → search for the "best" shield (lowest damage value)
                 */
                if (offhandStack.isOf(Items.TOTEM_OF_UNDYING)) {
                    ItemStack bestShield = null;

                    for (int i = 0; i < 36; i++) {
                        ItemStack current = client.player.getInventory().getStack(i);

                        if (current.isOf(Items.SHIELD)) {
                            /**
                             * Prefer shields with lower damage (more durability left)
                             */
                            if (bestShield == null || current.getDamage() < bestShield.getDamage()) {
                                bestShield = current;
                                bestSlot = i;
                            }
                        }
                    }

                } else {
                    /**
                     * If offhand is NOT a Totem:
                     * → search for any Totem in inventory
                     */
                    for (int i = 0; i < 36; i++) {
                        if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                            bestSlot = i;
                            break;
                        }
                    }
                }

                /**
                 * If a valid target slot was found:
                 * perform swap using appropriate method
                 */
                if (bestSlot != -1) {

                    /**
                     * NOTE:
                     * Minecraft uses different slot mappings server-side.
                     * Hotbar (0–8) is handled differently than main inventory (9–35).
                     */
                    int serverSlot = bestSlot;

                    if (bestSlot < 9) {

                        /**
                         * HOTBAR LOGIC:
                         *
                         * Uses SWAP_ITEM_WITH_OFFHAND packet,
                         * which is more reliable and less desync-prone.
                         */
                        int originalSlot = client.player.getInventory().getSelectedSlot();

                        client.player.getInventory().setSelectedSlot(bestSlot);

                        client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                BlockPos.ORIGIN,
                                Direction.DOWN
                        ));

                        /** Restore original selected slot */
                        client.player.getInventory().setSelectedSlot(originalSlot);

                    } else {

                        /**
                         * INVENTORY LOGIC:
                         *
                         * Uses clickSlot interactions to simulate manual swapping.
                         *
                         * Steps:
                         * 1. Pick up item from inventory
                         * 2. Place into offhand slot (slot 45)
                         * 3. Return previous offhand item (if any)
                         */
                        client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                serverSlot,
                                0,
                                SlotActionType.PICKUP,
                                client.player
                        );

                        client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                45,
                                0,
                                SlotActionType.PICKUP,
                                client.player
                        );

                        client.interactionManager.clickSlot(
                                client.player.currentScreenHandler.syncId,
                                serverSlot,
                                0,
                                SlotActionType.PICKUP,
                                client.player
                        );
                    }
                }
            }
        });
    }
}