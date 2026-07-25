package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.events.WindowClickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int actionDelay = 0;
    private int oDelay = 0;
    private boolean inventoryOpen = false;
    private final TimerUtil autoArmorTime = new TimerUtil();
    public final IntProperty minDelay = new IntProperty("Min-Delay", 1, 0, 20);
    public final IntProperty maxDelay = new IntProperty("Max-Delay", 2, 0, 20);
    public final IntProperty openDelay = new IntProperty("Open-Delay", 1, 0, 20);
    public final BooleanProperty autoArmor = new BooleanProperty("Auto-Armor", true);
    public final IntProperty autoArmorInterval = new IntProperty("Auto-Armor-Interval", 0, 0, 100, this.autoArmor::getValue);
    public final BooleanProperty hotbar = new BooleanProperty("Hotbar", true);
    public final BooleanProperty dropTrash = new BooleanProperty("Drop-Trash", false);
    public final BooleanProperty checkDurability = new BooleanProperty("Check-Durability", true);
    public final IntProperty inv = new IntProperty("Inv", 36, 0, 36);
    public final IntProperty swordSlot = new IntProperty("Sword-Slot", 1, 0, 9);
    public final IntProperty pickaxeSlot = new IntProperty("Pickaxe-Slot", 3, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("Shovel-Slot", 4, 0, 9);
    public final IntProperty axeSlot = new IntProperty("Axe-Slot", 5, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("Blocks-Slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("Blocks", 128, 64, 2304);
    public final IntProperty throwableSlot = new IntProperty("Throwable-Slot", 7, 0, 9);
    public final IntProperty throwable = new IntProperty("Throwable", 64, 0, 2304);
    public final IntProperty goldAppleSlot = new IntProperty("Gold-Apple-Slot", 9, 0, 9);
    public final IntProperty arrowSlot = new IntProperty("Arrow-Slot", 6, 0, 9);
    public final IntProperty arrow = new IntProperty("Arrow", 256, 0, 2304);
    public final IntProperty fishrodSlot = new IntProperty("Fishrod-Slot", 7, 0, 9);
    public final IntProperty fishrod = new IntProperty("Fishrod", 2, 0, 64);
    public final IntProperty bowSlot = new IntProperty("Bow-Slot", 8, 0, 9);

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) {
            return 8 - (slot - 36);
        } else {
            return slot <= 8 ? slot + 36 : slot;
        }
    }

    private void clickSlot(int windowId, int slotId, int mouseButtonClicked, int mode) {
        mc.playerController.windowClick(windowId, slotId, mouseButtonClicked, mode, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null ? stack.stackSize : 0;
        }
    }

    public InvManager() {
        super("InvManager", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.actionDelay > 0) {
                this.actionDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            if (this.isEnabled() && this.isValidGameMode()) {
                if (this.hotbar.getValue() && !(mc.currentScreen instanceof GuiInventory) && this.actionDelay <= 0 && mc.thePlayer.openContainer.windowId == 0) {
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                        if (stack != null && stack.getItem() instanceof ItemArmor) {
                            int armorType = ((ItemArmor) stack.getItem()).armorType;
                            ItemStack currentArmor = mc.thePlayer.inventory.armorItemInSlot(armorType);
                            if (currentArmor == null || !(currentArmor.getItem() instanceof ItemArmor) || ItemUtil.getArmorProtection(stack) > ItemUtil.getArmorProtection(currentArmor)) {
                                boolean changed = mc.thePlayer.inventory.currentItem != i;
                                if (changed) mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(i));
                                mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventoryContainer.getSlot(i + 36).getStack()));
                                if (changed) mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                this.actionDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
                                return;
                            }
                        }
                    }
                }
            }
            if (!(mc.currentScreen instanceof GuiInventory)) {
                this.inventoryOpen = false;
            } else if (!(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
                this.inventoryOpen = false;
            } else {
                if (!this.inventoryOpen) {
                    this.inventoryOpen = true;
                    this.oDelay = this.openDelay.getValue() + 1;
                    this.autoArmorTime.reset();
                }
                if (this.oDelay <= 0 && this.actionDelay <= 0) {
                    if (this.isEnabled() && this.isValidGameMode()) {
                        ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        for (int i = 0; i < 4; i++) {
                            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
                            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
                        }
                        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
                        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, this.checkDurability.getValue());
                        if (inventorySwordSlot == -1)
                            inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
                        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
                        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, this.checkDurability.getValue());
                        if (inventoryPickaxeSlot == -1)
                            inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
                        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
                        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, this.checkDurability.getValue());
                        if (inventoryShovelSlot == -1)
                            inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
                        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
                        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, this.checkDurability.getValue());
                        if (inventoryAxeSlot == -1)
                            inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);
                        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
                        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot, ItemUtil.ItemType.Block);

                        int preferredThrowableHotbarSlot = this.throwableSlot.getValue() - 1;
                        int inventoryThrowableSlot = -1;
                        for (int i = 0; i < 36; i++) {
                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                            if (stack != null && (stack.getItem() == Items.egg || stack.getItem() == Items.snowball)) {
                                inventoryThrowableSlot = i;
                                if (i == preferredThrowableHotbarSlot) break;
                            }
                        }

                        int preferredArrowHotbarSlot = this.arrowSlot.getValue() - 1;
                        int inventoryArrowSlot = -1;
                        for (int i = 0; i < 36; i++) {
                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                            if (stack != null && stack.getItem() == Items.arrow) {
                                inventoryArrowSlot = i;
                                if (i == preferredArrowHotbarSlot) break;
                            }
                        }

                        int preferredFishrodHotbarSlot = this.fishrodSlot.getValue() - 1;
                        int inventoryFishrodSlot = -1;
                        for (int i = 0; i < 36; i++) {
                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                            if (stack != null && stack.getItem() == Items.fishing_rod) {
                                inventoryFishrodSlot = i;
                                if (i == preferredFishrodHotbarSlot) break;
                            }
                        }

                        int preferredGoldAppleHotbarSlot = this.goldAppleSlot.getValue() - 1;
                        int inventoryGoldAppleSlot = ItemUtil.findInventorySlot(preferredGoldAppleHotbarSlot, ItemUtil.ItemType.GoldApple);
                        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
                        int inventoryBowSlot = ItemUtil.findBowInventorySlot(preferredBowHotbarSlot, this.checkDurability.getValue());
                        if (inventoryBowSlot == -1)
                            inventoryBowSlot = ItemUtil.findBowInventorySlot(preferredBowHotbarSlot, false);
                        if (this.autoArmor.getValue() && this.autoArmorTime.hasTimeElapsed(this.autoArmorInterval.getValue() * 50L)) {
                            for (int i = 0; i < 4; i++) {
                                int equippedSlot = equippedArmorSlots.get(i);
                                int inventorySlot = inventoryArmorSlots.get(i);
                                if (equippedSlot != -1 || inventorySlot != -1) {
                                    int playerArmorSlot = 39 - i;
                                    if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                                        if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                                            if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
                                            } else {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
                                            }
                                        } else {
                                            int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(armorToEquipSlot), 0, 1);
                                            this.autoArmorTime.reset();
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                        LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                        if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && inventorySwordSlot != -1) {
                            usedHotbarSlots.add(preferredSwordHotbarSlot);
                            if (inventorySwordSlot != preferredSwordHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventorySwordSlot), preferredSwordHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && inventoryPickaxeSlot != -1) {
                            usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                            if (inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryPickaxeSlot), preferredPickaxeHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && inventoryShovelSlot != -1) {
                            usedHotbarSlots.add(preferredShovelHotbarSlot);
                            if (inventoryShovelSlot != preferredShovelHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryShovelSlot), preferredShovelHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && inventoryAxeSlot != -1) {
                            usedHotbarSlots.add(preferredAxeHotbarSlot);
                            if (inventoryAxeSlot != preferredAxeHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryAxeSlot), preferredAxeHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                            usedHotbarSlots.add(preferredBlocksHotbarSlot);
                            if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredThrowableHotbarSlot >= 0 && preferredThrowableHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredThrowableHotbarSlot) && inventoryThrowableSlot != -1) {
                            usedHotbarSlots.add(preferredThrowableHotbarSlot);
                            if (inventoryThrowableSlot != preferredThrowableHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryThrowableSlot), preferredThrowableHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredArrowHotbarSlot >= 0 && preferredArrowHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredArrowHotbarSlot) && inventoryArrowSlot != -1) {
                            usedHotbarSlots.add(preferredArrowHotbarSlot);
                            if (inventoryArrowSlot != preferredArrowHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryArrowSlot), preferredArrowHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredFishrodHotbarSlot >= 0 && preferredFishrodHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredFishrodHotbarSlot) && inventoryFishrodSlot != -1) {
                            usedHotbarSlots.add(preferredFishrodHotbarSlot);
                            if (inventoryFishrodSlot != preferredFishrodHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryFishrodSlot), preferredFishrodHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredGoldAppleHotbarSlot >= 0 && preferredGoldAppleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGoldAppleHotbarSlot) && inventoryGoldAppleSlot != -1) {
                            usedHotbarSlots.add(preferredGoldAppleHotbarSlot);
                            if (inventoryGoldAppleSlot != preferredGoldAppleHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryGoldAppleSlot), preferredGoldAppleHotbarSlot, 2);
                                return;
                            }
                        }
                        if (preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBowHotbarSlot) && inventoryBowSlot != -1) {
                            usedHotbarSlots.add(preferredBowHotbarSlot);
                            if (inventoryBowSlot != preferredBowHotbarSlot) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBowSlot), preferredBowHotbarSlot, 2);
                                return;
                            }
                        }
                        if (this.dropTrash.getValue()) {
                            int currentBlockCount = 0;
                            int currentThrowableCount = 0;
                            int currentArrowCount = 0;
                            int currentFishrodCount = 0;

                            for (int i = 0; i < 36; i++) {
                                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                if (stack != null) {
                                    if (ItemUtil.isBlock(stack)) currentBlockCount += stack.stackSize;
                                    if (stack.getItem() == Items.egg || stack.getItem() == Items.snowball) currentThrowableCount += stack.stackSize;
                                    if (stack.getItem() == Items.arrow) currentArrowCount += stack.stackSize;
                                    if (stack.getItem() == Items.fishing_rod) currentFishrodCount += stack.stackSize;
                                }
                            }

                            for (int i = 0; i < 36; i++) {
                                if (!equippedArmorSlots.contains(i)
                                        && !inventoryArmorSlots.contains(i)
                                        && inventorySwordSlot != i
                                        && inventoryPickaxeSlot != i
                                        && inventoryShovelSlot != i
                                        && inventoryAxeSlot != i
                                        && inventoryBlocksSlot != i
                                        && inventoryThrowableSlot != i
                                        && inventoryArrowSlot != i
                                        && inventoryFishrodSlot != i
                                        && inventoryGoldAppleSlot != i
                                        && inventoryBowSlot != i) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null) {
                                        boolean isBlock = ItemUtil.isBlock(stack);
                                        boolean isThrowable = stack.getItem() == Items.egg || stack.getItem() == Items.snowball;
                                        boolean isArrow = stack.getItem() == Items.arrow;
                                        boolean isFishrod = stack.getItem() == Items.fishing_rod;

                                        if (isBlock && currentBlockCount > this.blocks.getValue()) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            currentBlockCount -= stack.stackSize;
                                            return;
                                        }
                                        if (isThrowable && currentThrowableCount > this.throwable.getValue()) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            currentThrowableCount -= stack.stackSize;
                                            return;
                                        }
                                        if (isArrow && currentArrowCount > this.arrow.getValue()) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            currentArrowCount -= stack.stackSize;
                                            return;
                                        }
                                        if (isFishrod && currentFishrodCount > this.fishrod.getValue()) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            currentFishrodCount -= stack.stackSize;
                                            return;
                                        }
                                        if (!isBlock && !isThrowable && !isArrow && !isFishrod && ItemUtil.isNotSpecialItem(stack)) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        this.actionDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
    }

    @Override
    public void verifyValue(String mode) {
        switch (mode) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }
}