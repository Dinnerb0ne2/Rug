package keystrokesmod.module.impl.player;

import akka.japi.Pair;
import keystrokesmod.Raven;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ModeSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.utils.ModeOnly;
import keystrokesmod.utility.ContainerUtils;
import keystrokesmod.utility.MoveUtil;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemEmptyMap;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InvManager extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", new String[]{"Basic", "OpenInv", "Hypixel", "Ghost"}, 1);
    private final ButtonSetting notWhileMoving = new ButtonSetting("Not while moving", false, new ModeOnly(mode, 0));
    private final SliderSetting minStartDelay = new SliderSetting("Min start delay", 100, 0, 1000, 10, "ms");
    private final SliderSetting maxStartDelay = new SliderSetting("Max start delay", 300, 0, 1000, 10, "ms");
    private final ButtonSetting autoEquipHotbar = new ButtonSetting("Auto-equip hotbar", true);
    private final ButtonSetting equipBetterOnly = new ButtonSetting("Equip better only", true);
    private final ButtonSetting randomizeTaskOrder = new ButtonSetting("Randomize task order", true);
    private final SliderSetting humanizationFactor = new SliderSetting("Humanization", 50, 0, 100, 1, "%");
    private final ButtonSetting armor = new ButtonSetting("Armor", true);
    private final SliderSetting minArmorDelay = new SliderSetting("Min armor delay", 50, 0, 500, 10, "ms", armor::isToggled);
    private final SliderSetting maxArmorDelay = new SliderSetting("Max armor delay", 150, 0, 500, 10, "ms", armor::isToggled);
    private final ButtonSetting clean = new ButtonSetting("Clean", true);
    private final SliderSetting minCleanDelay = new SliderSetting("Min clean delay", 50, 0, 500, 10, "ms", clean::isToggled);
    private final SliderSetting maxCleanDelay = new SliderSetting("Max clean delay", 150, 0, 500, 10, "ms", clean::isToggled);
    private final ButtonSetting keepStackables = new ButtonSetting("Keep stackables", true, clean::isToggled);
    private final SliderSetting minStackSize = new SliderSetting("Min stack size", 16, 1, 64, 1, clean::isToggled);
    private final ButtonSetting sort = new ButtonSetting("Sort", true);
    private final SliderSetting minSortDelay = new SliderSetting("Min sort delay", 50, 0, 500, 10, "ms", sort::isToggled);
    private final SliderSetting maxSortDelay = new SliderSetting("Max sort delay", 150, 0, 500, 10, "ms", sort::isToggled);
    private final SliderSetting swordSlot = new SliderSetting("Sword slot", 1, 0, 9, 1, sort::isToggled);
    private final SliderSetting blockSlot = new SliderSetting("Block slot", 2, 0, 9, 1, sort::isToggled);
    private final SliderSetting enderPearlSlot = new SliderSetting("Ender pearl slot", 3, 0, 9, 1, sort::isToggled);
    private final SliderSetting bowSlot = new SliderSetting("Bow slot", 4, 0, 9, 1, sort::isToggled);
    private final SliderSetting foodSlot = new SliderSetting("Food slot", 5, 0, 9, 1, sort::isToggled);
    private final SliderSetting throwableSlot = new SliderSetting("Throwable slot", 6, 0, 9, 1, sort::isToggled);
    private final SliderSetting rodSlot = new SliderSetting("Rod slot", 7, 0, 9, 1, sort::isToggled);
    private final SliderSetting potionSlot = new SliderSetting("Potion slot", 8, 0, 9, 1, sort::isToggled);
    private final ButtonSetting shuffle = new ButtonSetting("Shuffle", true, () -> armor.isToggled() || clean.isToggled() || sort.isToggled());
    private final ButtonSetting chestStealerIntegration = new ButtonSetting("ChestStealer integration", true);
    private final ButtonSetting silentMode = new ButtonSetting("Silent mode", false);
    private final SliderSetting actionTimeout = new SliderSetting("Action timeout", 5000, 1000, 10000, 100, "ms");

    private State state = State.NONE;
    private long nextTaskTime;
    private boolean invOpen = false;
    private long lastActionTime = 0;
    private final Random random = new Random();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private List<Runnable> currentTasks = new ArrayList<>();
    private int currentTaskIndex = 0;

    public InvManager() {
        super("InvManager", category.player);
        this.registerSetting(
                mode, notWhileMoving, minStartDelay, maxStartDelay,
                autoEquipHotbar, equipBetterOnly, randomizeTaskOrder, humanizationFactor,
                armor, minArmorDelay, maxArmorDelay,
                clean, minCleanDelay, maxCleanDelay, keepStackables, minStackSize,
                sort, minSortDelay, maxSortDelay,
                swordSlot, blockSlot, enderPearlSlot, bowSlot, foodSlot,
                throwableSlot, rodSlot, potionSlot,
                shuffle, chestStealerIntegration, silentMode, actionTimeout
        );
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minStartDelay, maxStartDelay);
        Utils.correctValue(minArmorDelay, maxArmorDelay);
        Utils.correctValue(minCleanDelay, maxCleanDelay);
        Utils.correctValue(minSortDelay, maxSortDelay);
    }

    @Override
    public void onDisable() {
        if (invOpen && (mode.getInput() == 0 || mode.getInput() == 3)) {
            PacketUtils.sendPacket(new C0DPacketCloseWindow());
        }
        state = State.NONE;
        invOpen = false;
        isProcessing.set(false);
        currentTasks.clear();
    }

    @Override
    public void onUpdate() {
        if (System.currentTimeMillis() - lastActionTime > actionTimeout.getInput()) {
            state = State.NONE;
            isProcessing.set(false);
            currentTasks.clear();
        }
        if (isProcessing.get()) return;

        switch ((int) mode.getInput()) {
            case 0: // Basic
                invOpen = !(notWhileMoving.isToggled() && MoveUtil.isMoving()) &&
                        !(mc.currentScreen instanceof GuiChest) &&
                        !(chestStealerIntegration.isToggled() && ModuleManager.chestStealer.isEnabled());
                break;
            case 1: // OpenInv
                invOpen = mc.currentScreen instanceof GuiInventory;
                break;
            case 2: // Hypixel
                invOpen = !(mc.currentScreen instanceof GuiChest) &&
                        !(chestStealerIntegration.isToggled() && ModuleManager.chestStealer.isEnabled());
                break;
            case 3: // Ghost
                invOpen = true;
                break;
        }

        if (!invOpen && autoEquipHotbar.isToggled()) {
            handleHotbarEquip();
        }

        if (invOpen) {
            if (state == State.NONE) {
                state = State.BEFORE;
                int delay = getRandomizedDelay(minStartDelay, maxStartDelay);
                nextTaskTime = System.currentTimeMillis() + delay;
                if (delay == 0) {
                    prepareTasks();
                    state = State.TASKING;
                } else {
                    Raven.getExecutor().schedule(
                            () -> {
                                prepareTasks();
                                state = State.TASKING;
                            },
                            delay,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
        } else {
            state = State.NONE;
        }
    }

    private void handleHotbarEquip() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            // 内联 getArmorType：真实 ContainerUtils 无此方法
            int armorType = -1;
            if (stack.getItem() instanceof ItemArmor) {
                armorType = ((ItemArmor) stack.getItem()).armorType;
            }
            if (armorType != -1) {
                int currentArmorSlot = armorType + 5;
                ItemStack currentArmor = ContainerUtils.getItemStack(currentArmorSlot);
                boolean shouldEquip = true;
                if (equipBetterOnly.isToggled() && currentArmor != null) {
                    // 内联 isBetterArmor：用 getArmorLevel 比较
                    shouldEquip = ContainerUtils.getArmorLevel(stack) > ContainerUtils.getArmorLevel(currentArmor);
                }
                if (shouldEquip) {
                    // silentSwap 退化为普通 windowClick（真实 API 无静默版）
                    mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,
                            i < 9 ? i + 36 : i, 0, 0, mc.thePlayer);
                    mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,
                            currentArmorSlot, 0, 0, mc.thePlayer);
                    if (currentArmor != null) {
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId,
                                i < 9 ? i + 36 : i, 0, 0, mc.thePlayer);
                    }
                    lastActionTime = System.currentTimeMillis();
                    return;
                }
            }
        }
    }

    private void prepareTasks() {
        currentTasks.clear();
        if (armor.isToggled()) currentTasks.add(this::processArmor);
        if (clean.isToggled()) currentTasks.add(this::processClean);
        if (sort.isToggled()) currentTasks.addAll(getSortTasks());
        if (randomizeTaskOrder.isToggled()) Collections.shuffle(currentTasks);
        currentTaskIndex = 0;
        isProcessing.set(true);
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (state != State.TASKING || !isProcessing.get() || currentTasks.isEmpty()) return;
        if (shouldHumanize()) return;
        if (currentTaskIndex < currentTasks.size()) {
            currentTasks.get(currentTaskIndex).run();
            currentTaskIndex++;
            lastActionTime = System.currentTimeMillis();
            if (currentTaskIndex >= currentTasks.size()) {
                isProcessing.set(false);
                state = State.NONE;
            }
        }
    }

    private boolean shouldHumanize() {
        if (humanizationFactor.getInput() == 0) return false;
        if (random.nextInt(100) < humanizationFactor.getInput()) {
            try {
                Thread.sleep(random.nextInt(50));
            } catch (InterruptedException ignored) {}
            return true;
        }
        return false;
    }

    private void processArmor() {
        List<Integer> armorTypes = new ArrayList<>(ContainerUtils.ARMOR_TYPES);
        if (shuffle.isToggled()) Collections.shuffle(armorTypes);
        for (int i : armorTypes) {
            final int curArmorSlot = i + 5;
            final int bestArmorSlot = ContainerUtils.getBestArmor(i, null);
            if (bestArmorSlot != -1 && bestArmorSlot != curArmorSlot) {
                if (ContainerUtils.getItemStack(curArmorSlot) != null) {
                    // silentDrop 退化为 drop
                    ContainerUtils.drop(curArmorSlot);
                } else {
                    // silentClick 退化为 click
                    ContainerUtils.click(bestArmorSlot);
                }
                if (mode.getInput() == 2) MoveUtil.stop();
                nextTaskTime = System.currentTimeMillis() + getRandomizedDelay(minArmorDelay, maxArmorDelay);
                return;
            }
        }
    }

    private void processClean() {
        final IInventory inventory = mc.thePlayer.inventory;
        final List<Pair<Integer, ItemStack>> slots = getDropSlots(inventory);
        if (!slots.isEmpty()) {
            for (Pair<Integer, ItemStack> slot : slots) {
                // silentDrop 退化为 drop
                ContainerUtils.drop(slot.first());
                if (mode.getInput() == 2) MoveUtil.stop();
                nextTaskTime = System.currentTimeMillis() + getRandomizedDelay(minCleanDelay, maxCleanDelay);
                return;
            }
        }
    }

    private void sort(int from, int to) {
        if (to == 0 || from == -1 || to == -1 || from == to) return;
        // silentSort 退化为 sort
        boolean success = ContainerUtils.sort(from, to);
        if (success) {
            if (mode.getInput() == 2) MoveUtil.stop();
            nextTaskTime = System.currentTimeMillis() + getRandomizedDelay(minSortDelay, maxSortDelay);
        }
    }

    private @NotNull List<Pair<Integer, ItemStack>> getDropSlots(@NotNull IInventory inventory) {
        final List<Pair<Integer, ItemStack>> result = new ArrayList<>(inventory.getSizeInventory());
        for (int i = 5; i < 45; i++) {
            final ItemStack stack = ContainerUtils.getItemStack(i);
            if (stack == null || stack.getItem() instanceof ItemEmptyMap) continue;
            if (keepStackables.isToggled() && stack.isStackable() && stack.stackSize >= minStackSize.getInput()) {
                continue;
            }
            if (!ContainerUtils.canDrop(stack, i, null)) continue;
            result.add(new Pair<>(i, stack));
        }
        if (shuffle.isToggled()) Collections.shuffle(result);
        return result;
    }

    private @NotNull List<Runnable> getSortTasks() {
        final List<Runnable> result = new ArrayList<>();
        result.add(() -> sort(ContainerUtils.getBestSword(null, (int) swordSlot.getInput()), (int) swordSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getMostBlocks((int) blockSlot.getInput()), (int) blockSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getBiggestStack(Items.ender_pearl, (int) enderPearlSlot.getInput()), (int) enderPearlSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getBestBow(null), (int) bowSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getBestFood((int) foodSlot.getInput()), (int) foodSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getMostProjectiles((int) throwableSlot.getInput()), (int) throwableSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getBestRod(null), (int) rodSlot.getInput()));
        result.add(() -> sort(ContainerUtils.getBiggestStack(Items.potionitem, (int) potionSlot.getInput()), (int) potionSlot.getInput()));
        return result;
    }

    private int getRandomizedDelay(SliderSetting min, SliderSetting max) {
        int minVal = (int) min.getInput();
        int maxVal = (int) max.getInput();
        if (minVal == maxVal) return minVal;
        double factor = humanizationFactor.getInput() / 100.0;
        int range = maxVal - minVal;
        int baseDelay = minVal + random.nextInt(range + 1);
        int variation = (int) (range * 0.2 * factor);
        return baseDelay + random.nextInt(variation * 2 + 1) - variation;
    }

    @Override
    public String getInfo() {
        return mode.getOptions()[(int) mode.getInput()];
    }

    enum State { NONE, BEFORE, TASKING }
}
