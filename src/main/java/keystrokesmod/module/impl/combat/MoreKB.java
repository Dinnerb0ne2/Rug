package keystrokesmod.module.impl.combat;

import keystrokesmod.event.*;
import keystrokesmod.mixins.impl.client.KeyBindingAccessor;
import keystrokesmod.module.*;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.*;
import keystrokesmod.utility.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public class MoreKB extends Module {
    // 高级设置
    private final ModeValue mode;
    private final SliderSetting chance;
    private final BooleanSetting randomizeTimings;
    private final BooleanSetting adaptiveMode;
    private final BooleanSetting comboMode;
    private final BooleanSetting onlyWhenHitting;
    private final BooleanSetting disableInWater;
    private final BooleanSetting disableOnGround;
    private final BooleanSetting disableWhileBlocking;
    
    // 状态变量
    private boolean canSprint = true;
    private int delayTicksLeft = 0;
    private int reSprintTicksLeft = -1;
    private int comboCounter = 0;
    private long lastAttackTime = 0;
    private double lastTargetDistance = 0;
    private boolean wasCritical;
    
    // 智能调整变量
    private int dynamicDelay = 0;
    private int dynamicRePress = 0;
    private double aggressionFactor = 1.0;
    
    private final Random random = new Random();

    public MoreKB() {
        super("MoreKB", category.combat);
        
        // 主设置
        this.registerSetting(mode = new ModeValue("Mode", this)
                .add(new AdvancedMode("Legit", this))
                .add(new AdvancedMode("LegitSneak", this))
                .add(new AdvancedMode("LegitFast", this))
                .add(new AdvancedMode("Fast", this))
                .add(new AdvancedMode("Packet", this))
                .add(new AdvancedMode("LegitBlock", this))
                .add(new AdvancedMode("LegitInv", this))
                .add(new AdvancedMode("STap", this))
                .add(new AdvancedMode("Hybrid", this))
                .add(new AdvancedMode("Randomized", this))
                .setDefaultValue("Hybrid")
        );
        
        // 通用设置
        this.registerSetting(chance = new SliderSetting("Chance", 100, 0, 100, 1, "%"));
        this.registerSetting(randomizeTimings = new BooleanSetting("Randomize timings", true));
        this.registerSetting(adaptiveMode = new BooleanSetting("Adaptive mode", true));
        this.registerSetting(comboMode = new BooleanSetting("Combo mode", false));
        this.registerSetting(onlyWhenHitting = new BooleanSetting("Only when hitting", true));
        this.registerSetting(disableInWater = new BooleanSetting("Disable in water", true));
        this.registerSetting(disableOnGround = new BooleanSetting("Disable on ground", false));
        this.registerSetting(disableWhileBlocking = new BooleanSetting("Disable while blocking", true));
    }

    @Override
    public void onEnable() {
        mode.enable();
        comboCounter = 0;
        aggressionFactor = 1.0;
    }

    @Override
    public void onDisable() {
        mode.disable();
        resetKeyStates();
    }

    @Override
    public void onUpdate() {
        // 更新动态计时器
        if (adaptiveMode.isToggled()) {
            updateDynamicTimings();
        }
        
        // 处理重新冲刺计时器
        if (reSprintTicksLeft == 0) {
            reSprint();
            reSprintTicksLeft = -1;
        } else if (reSprintTicksLeft > 0) {
            reSprintTicksLeft--;
        }

        // 处理延迟计时器
        if (delayTicksLeft > 0) {
            delayTicksLeft--;
        }
        
        // 组合模式冷却
        if (comboMode.isToggled() && System.currentTimeMillis() - lastAttackTime > 2000) {
            comboCounter = 0;
        }
    }

    private void updateDynamicTimings() {
        // 根据攻击频率调整攻击性
        long timeSinceLastAttack = System.currentTimeMillis() - lastAttackTime;
        if (timeSinceLastAttack < 500) {
            aggressionFactor = Math.min(1.5, aggressionFactor + 0.05);
        } else {
            aggressionFactor = Math.max(0.5, aggressionFactor - 0.02);
        }
        
        // 动态调整计时器
        dynamicDelay = (int) (5 / aggressionFactor);
        dynamicRePress = (int) (3 / aggressionFactor);
    }

    public void stopSprint() {
        if (shouldCancelAction()) return;
        
        canSprint = false;
        int modeIndex = (int) mode.getInput();
        
        // 根据模式执行不同操作
        switch (modeIndex) {
            case 7: // STap
                ((KeyBindingAccessor) mc.gameSettings.keyBindBack).setPressed(true);
                // 故意不break，继续执行case 0
            case 0: // Legit
            case 2: // LegitFast
                ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(false);
                break;
            case 5: // LegitBlock
                sendBlockPacket(true);
                break;
            case 6: // LegitInv
                toggleInventory();
                break;
            case 8: // Hybrid
                if (random.nextBoolean()) {
                    ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(false);
                } else {
                    sendBlockPacket(true);
                }
                break;
            case 9: // Randomized
                executeRandomAction();
                break;
        }
        
        // 发送数据包模式
        if (modeIndex == 4 || modeIndex == 8) {
            mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, false));
        }
    }

    public void reSprint() {
        canSprint = true;
        int modeIndex = (int) mode.getInput();
        
        switch (modeIndex) {
            case 7: // STap
                ((KeyBindingAccessor) mc.gameSettings.keyBindBack).setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
                // 故意不break，继续执行case 0
            case 0: // Legit
            case 2: // LegitFast
                ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
                break;
            case 5: // LegitBlock
                sendBlockPacket(false);
                break;
            case 6: // LegitInv
                if (mc.currentScreen instanceof GuiInventory) {
                    mc.thePlayer.closeScreen();
                }
                break;
            case 8: // Hybrid
                if (random.nextBoolean()) {
                    ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
                } else {
                    sendBlockPacket(false);
                }
                break;
        }
    }

    private void executeRandomAction() {
        int action = random.nextInt(4);
        switch (action) {
            case 0:
                ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(false);
                break;
            case 1:
                ((KeyBindingAccessor) mc.gameSettings.keyBindBack).setPressed(true);
                break;
            case 2:
                sendBlockPacket(true);
                break;
            case 3:
                toggleInventory();
                break;
        }
    }

    private void sendBlockPacket(boolean start) {
        if (start) {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(
                    new BlockPos(-1, -1, -1), 255, null, 0.0F, 0.0F, 0.0F));
        } else {
            mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        }
    }

    private void toggleInventory() {
        ((KeyBindingAccessor) mc.gameSettings.keyBindInventory).setPressed(true);
        KeyBinding.onTick(mc.gameSettings.keyBindInventory.getKeyCode());
        ((KeyBindingAccessor) mc.gameSettings.keyBindInventory).setPressed(false);
        KeyBinding.onTick(mc.gameSettings.keyBindInventory.getKeyCode());
    }

    private void resetKeyStates() {
        ((KeyBindingAccessor) mc.gameSettings.keyBindForward).setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
        ((KeyBindingAccessor) mc.gameSettings.keyBindBack).setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
    }

    protected boolean noSprint() {
        return !canSprint;
    }

    private boolean shouldCancelAction() {
        return (disableInWater.isToggled() && mc.thePlayer.isInWater()) ||
               (disableOnGround.isToggled() && mc.thePlayer.onGround) ||
               (disableWhileBlocking.isToggled() && mc.thePlayer.isBlocking());
    }

    @SubscribeEvent
    public void onMoveInput(MoveInputEvent event) {
        if (noSprint() && MoveUtil.isMoving()) {
            switch ((int) mode.getInput()) {
                case 1: // LegitSneak
                    event.setSneak(true);
                    break;
                case 3: // Fast
                    event.setForward(0.7999f);
                    break;
                case 8: // Hybrid
                    if (random.nextBoolean()) {
                        event.setForward(0.7999f);
                    } else {
                        event.setSneak(true);
                    }
                    break;
                case 9: // Randomized
                    event.setForward((float) (0.7 + random.nextDouble() * 0.3));
                    break;
            }
        }
    }

    @SubscribeEvent
    public void onSprint(SprintEvent event) {
        if (noSprint() && MoveUtil.isMoving() && ((int) mode.getInput() == 2 || (int) mode.getInput() == 8)) {
            event.setSprint(false);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (noSprint() && MoveUtil.isMoving()) {
            if ((int) mode.getInput() == 4 || (int) mode.getInput() == 8) {
                event.setSprinting(false);
            }
        }
        
        // 记录是否为暴击
        wasCritical = event.isCritical();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttack(AttackEntityEvent event) {
        if (!Utils.nullCheck() || event.entityPlayer != mc.thePlayer || delayTicksLeft > 0) return;
        if (!(event.target instanceof EntityLivingBase)) return;
        if (onlyWhenHitting.isToggled() && !isActuallyHitting(event.target)) return;
        if (AntiBot.isBot(event.target)) return;
        if (shouldCancelAction()) return;
        if (((EntityLivingBase) event.target).deathTime != 0) return;
        
        // 概率检查
        if (Math.random() * 100 > chance.getInput()) return;
        
        // 更新攻击信息
        lastAttackTime = System.currentTimeMillis();
        lastTargetDistance = mc.thePlayer.getDistanceToEntity(event.target);
        if (comboMode.isToggled()) {
            comboCounter++;
        }
        
        stopSprint();
        
        // 计算重新冲刺时间
        int baseRePress = adaptiveMode.isToggled() ? dynamicRePress : 
                         randomizeTimings.isToggled() ? Utils.randomizeInt(2, 4) : 3;
        
        // 计算延迟时间
        int baseDelay = adaptiveMode.isToggled() ? dynamicDelay : 
                       randomizeTimings.isToggled() ? Utils.randomizeInt(8, 12) : 10;
        
        // 组合模式加成
        if (comboMode.isToggled() && comboCounter > 3) {
            baseRePress = Math.max(1, baseRePress - 1);
            baseDelay = Math.max(5, baseDelay - 2);
        }
        
        reSprintTicksLeft = baseRePress;
        delayTicksLeft = reSprintTicksLeft + baseDelay;
    }

    private boolean isActuallyHitting(Entity target) {
        // 更精确的命中检测
        return mc.objectMouseOver != null && 
               mc.objectMouseOver.entityHit == target && 
               mc.thePlayer.getDistanceToEntity(target) < 4.5;
    }

    @Override
    public String getInfo() {
        String info = mode.getSelected().getPrettyName();
        if (comboMode.isToggled() && comboCounter > 0) {
            info += " [" + comboCounter + "]";
        }
        return info;
    }

    private class AdvancedMode extends SubMode {
        private final SliderSetting minRePressDelay;
        private final SliderSetting maxRePressDelay;
        private final SliderSetting minDelayBetween;
        private final SliderSetting maxDelayBetween;
        private final BooleanSetting playersOnly;
        private final BooleanSetting notWhileRunner;
        private final BooleanSetting onlyCritical;
        private final SliderSetting maxDistance;

        public AdvancedMode(String name, @NotNull Module parent) {
            super(name, parent);
            
            this.registerSetting(minRePressDelay = new SliderSetting("Min Re-press delay", 2, 0, 10, 1, "ticks"));
            this.registerSetting(maxRePressDelay = new SliderSetting("Max Re-press delay", 4, 0, 10, 1, "ticks"));
            this.registerSetting(minDelayBetween = new SliderSetting("Min delay between", 8, 0, 20, 1, "ticks"));
            this.registerSetting(maxDelayBetween = new SliderSetting("Max delay between", 12, 0, 20, 1, "ticks"));
            this.registerSetting(playersOnly = new BooleanSetting("Players only", true));
            this.registerSetting(notWhileRunner = new BooleanSetting("Not while runner", false));
            this.registerSetting(onlyCritical = new BooleanSetting("Only on critical", false));
            this.registerSetting(maxDistance = new SliderSetting("Max distance", 4.0, 2.0, 6.0, 0.1, "blocks"));
        }

        @Override
        public void guiUpdate() {
            Utils.correctValue(minRePressDelay, maxRePressDelay);
            Utils.correctValue(minDelayBetween, maxDelayBetween);
        }
    }
}