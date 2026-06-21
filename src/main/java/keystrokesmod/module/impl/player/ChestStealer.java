package keystrokesmod.module.impl.player;

import keystrokesmod.Raven;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.RenderContainerEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ContainerUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {
    private static final ButtonSetting customChest = new ButtonSetting("Custom chest", false);
    private static final ButtonSetting silent = new ButtonSetting("Silent", false);
    private static final ButtonSetting notMoving = new ButtonSetting("Not Moving", false);
    public static ButtonSetting allowMouseControl = new ButtonSetting("Allow mouse control", false);
    private static State state = State.NONE;
    private final SliderSetting minStartDelay = new SliderSetting("Min start delay", 100, 0, 500, 10, "ms");
    private final SliderSetting maxStartDelay = new SliderSetting("Max start delay", 200, 0, 500, 10, "ms");
    private final SliderSetting minStealDelay = new SliderSetting("Min steal delay", 100, 0, 500, 10, "ms");
    private final SliderSetting maxStealDelay = new SliderSetting("Max steal delay", 150, 0, 500, 10, "ms");
    private final SliderSetting smartDelayFactor = new SliderSetting("Smart delay factor", 1.5, 0.5, 3.0, 0.1);
    private final ButtonSetting shuffle = new ButtonSetting("Shuffle", false);
    private final ButtonSetting autoClose = new ButtonSetting("Auto close", true);
    private final ButtonSetting autoCloseIfInvFull = new ButtonSetting("Auto close if inv full", true, autoClose::isToggled);
    private final SliderSetting minCloseDelay = new SliderSetting("Min close delay", 50, 0, 500, 10, "ms", autoClose::isToggled);
    private final SliderSetting maxCloseDelay = new SliderSetting("Max close delay", 100, 0, 500, 10, "ms", autoClose::isToggled);
    private final ButtonSetting ignoreTrash = new ButtonSetting("Ignore trash", false);
    private final ButtonSetting prioritizeEquipment = new ButtonSetting("Prioritize equipment", true);
    private final ButtonSetting avoidBadPotions = new ButtonSetting("Avoid bad potions", true);
    private final ButtonSetting avoidSpawnEggs = new ButtonSetting("Avoid spawn eggs", true);
    
    private final Set<Integer> stole = new HashSet<>();
    private long nextStealTime;
    private long nextCloseTime;
    private int lastStolenSlot = -1;
    private int consecutiveSteals = 0;
    private long lastActionTime = 0;

    public ChestStealer() {
        super("ChestStealer", category.player);
        this.registerSetting(minStartDelay, maxStartDelay, minStealDelay, maxStealDelay, smartDelayFactor,
                shuffle, autoClose, autoCloseIfInvFull, minCloseDelay, maxCloseDelay,
                customChest, ignoreTrash, silent, notMoving, allowMouseControl,
                prioritizeEquipment, avoidBadPotions, avoidSpawnEggs);
    }

    public static boolean noChestRender() {
        return ModuleManager.chestStealer != null && ModuleManager.chestStealer.isEnabled()
                && silent.isToggled() && ContainerUtils.isChest(customChest.isToggled());
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minStartDelay, maxStartDelay);
        Utils.correctValue(minStealDelay, maxStealDelay);
        Utils.correctValue(minCloseDelay, maxCloseDelay);
    }

    @SubscribeEvent
    public void onRenderContainer(RenderContainerEvent event) {
        if (silent.isToggled() && ContainerUtils.isChest(customChest.isToggled()))
            event.setCanceled(true);
    }

    @Override
    public void onUpdate() {
        if (ContainerUtils.isChest(customChest.isToggled())) {
            if (state == State.NONE) {
                state = State.BEFORE;
                int delay = Utils.randomizeInt(minStartDelay.getInput(), maxStartDelay.getInput());
                nextStealTime = System.currentTimeMillis();
                if (delay == 0) {
                    state = State.STEAL;
                } else {
                    Raven.getExecutor().schedule(
                            () -> state = State.STEAL,
                            delay,
                            TimeUnit.MILLISECONDS
                    );
                }
            }
        } else {
            state = State.NONE;
            stole.clear();
            consecutiveSteals = 0;
            lastStolenSlot = -1;
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (notMoving.isToggled() && ContainerUtils.isChest(customChest.isToggled())) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }

        if (allowMouseControl.isToggled() && ContainerUtils.isChest(customChest.isToggled())) {
            Utils.mc.inGameHasFocus = true;
            Utils.mc.mouseHelper.grabMouseCursor();
        }

        switch (state) {
            case STEAL:
                while (nextStealTime <= System.currentTimeMillis()) {
                    if (!ContainerUtils.isChest(customChest.isToggled())
                            || (autoCloseIfInvFull.isToggled() && ContainerUtils.inventoryFull())) {
                        close();
                        return;
                    }
                    
                    final ContainerChest containerChest = (ContainerChest) mc.thePlayer.openContainer;
                    final List<Integer> items = getUnStoleItems(containerChest);
                    
                    if (items.isEmpty()) {
                        close();
                        return;
                    }

                    final int slot = items.get(0);
                    stole.add(slot);
                    
                    // Prioritize equipment by placing in hotbar
                    ItemStack stack = containerChest.getLowerChestInventory().getStackInSlot(slot);
                    boolean isEquipment = isEquipment(stack);
                    int targetSlot = isEquipment && prioritizeEquipment.isToggled() ? 
                            findBestHotbarSlot(stack) : -1;
                    
                    ContainerUtils.steal(containerChest, slot, targetSlot);
                    
                    // Update smart delay tracking
                    updateSmartDelay(slot);
                    
                    // Calculate next steal time with smart delay
                    nextStealTime = System.currentTimeMillis() + calculateSmartDelay();
                }
                break;
            case AFTER:
                if (autoClose.isToggled() && nextCloseTime <= System.currentTimeMillis()) {
                    mc.thePlayer.closeScreen();
                    state = State.NONE;
                    consecutiveSteals = 0;
                    lastStolenSlot = -1;
                }
                break;
        }
    }

    private void updateSmartDelay(int currentSlot) {
        long currentTime = System.currentTimeMillis();
        if (lastStolenSlot == currentSlot) {
            consecutiveSteals++;
        } else {
            consecutiveSteals = 1;
        }
        lastStolenSlot = currentSlot;
        lastActionTime = currentTime;
    }

    private long calculateSmartDelay() {
        // Base random delay
        long baseDelay = Utils.randomizeInt(minStealDelay.getInput(), maxStealDelay.getInput());
        
        // Apply smart delay factor based on consecutive steals
        if (consecutiveSteals > 3) {
            double factor = smartDelayFactor.getInput() * (1 + (consecutiveSteals - 3) * 0.2);
            baseDelay = (long) (baseDelay * factor);
        }
        
        // Add some additional randomness
        baseDelay += ThreadLocalRandom.current().nextInt(-20, 20);
        
        return Math.max(baseDelay, minStealDelay.getInput());
    }

    private void close() {
        nextCloseTime = System.currentTimeMillis() + 
                Utils.randomizeInt((int) minCloseDelay.getInput(), (int) maxCloseDelay.getInput());
        state = State.AFTER;
    }

    private @NotNull List<Integer> getUnStoleItems(@NotNull ContainerChest containerChest) {
        IInventory chest = containerChest.getLowerChestInventory();
        List<Integer> items = new ArrayList<>(chest.getSizeInventory());
        
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (stole.contains(i)) continue;
            ItemStack stack = chest.getStackInSlot(i);
            if (stack == null) continue;
            
            // Skip empty maps
            if (stack.getItem() instanceof ItemEmptyMap) continue;
            
            // Skip spawn eggs if enabled
            if (avoidSpawnEggs.isToggled() && stack.getItem() instanceof ItemMonsterPlacer) continue;
            
            // Skip bad potions if enabled
            if (avoidBadPotions.isToggled() && isBadPotion(stack)) continue;
            
            // Skip trash if enabled
            if (ignoreTrash.isToggled() && ContainerUtils.canDrop(stack, -1, mc.thePlayer.inventory)) continue;
            
            items.add(i);
        }

        // Sort items by priority (equipment first if enabled)
        if (prioritizeEquipment.isToggled()) {
            items.sort((a, b) -> {
                ItemStack stackA = chest.getStackInSlot(a);
                ItemStack stackB = chest.getStackInSlot(b);
                boolean isEquipmentA = isEquipment(stackA);
                boolean isEquipmentB = isEquipment(stackB);
                
                if (isEquipmentA && !isEquipmentB) return -1;
                if (!isEquipmentA && isEquipmentB) return 1;
                return 0;
            });
        } else if (shuffle.isToggled()) {
            Collections.shuffle(items);
        }

        return items;
    }

    private boolean isEquipment(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemArmor || 
               item instanceof ItemSword || 
               item instanceof ItemTool || 
               item instanceof ItemBow;
    }

    private boolean isBadPotion(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemPotion)) return false;
        
        ItemPotion potion = (ItemPotion) stack;
        List<PotionEffect> effects = potion.getEffects(stack);
        
        if (effects == null || effects.isEmpty()) return false;
        
        for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            if (Potion.potionTypes[id].isBadEffect()) {
                return true;
            }
        }
        return false;
    }

    private int findBestHotbarSlot(ItemStack stack) {
        if (stack == null) return -1;
        
        // Try to find an empty hotbar slot first
        for (int i = 0; i < 9; i++) {
            ItemStack hotbarStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (hotbarStack == null) {
                return i;
            }
        }
        
        // If no empty slots, try to find a slot with the same item type
        for (int i = 0; i < 9; i++) {
            ItemStack hotbarStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (hotbarStack != null && hotbarStack.getItem() == stack.getItem()) {
                return i;
            }
        }
        
        // If still no match, return a random hotbar slot
        return ThreadLocalRandom.current().nextInt(0, 9);
    }

    enum State {
        NONE,
        BEFORE,
        STEAL,
        AFTER
    }
}