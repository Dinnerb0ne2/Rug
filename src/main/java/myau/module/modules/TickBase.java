package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TickBase extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Stuck", "Boost"});
    public final IntProperty minTicks = new IntProperty("MinTicks", 2, 0, 20);
    public final IntProperty maxTicks = new IntProperty("MaxTicks", 4, 0, 20);
    public final FloatProperty minStartRange = new FloatProperty("MinStartRange", 0.0F, 0.0F, 8.0F);
    public final FloatProperty maxStartRange = new FloatProperty("MaxStartRange", 3.0F, 0.0F, 8.0F);
    public final IntProperty startChance = new IntProperty("StartChance", 100, 0, 100);
    public final IntProperty minNextDelay = new IntProperty("MinNextDelay", 100, 0, 5000);
    public final IntProperty maxNextDelay = new IntProperty("MaxNextDelay", 500, 0, 5000);
    public final IntProperty targetMinHurtTime = new IntProperty("TargetMinHurtTime", 0, 0, 10);
    public final IntProperty targetMaxHurtTime = new IntProperty("TargetMaxHurtTime", 10, 0, 10);
    public final BooleanProperty calculateTicksWithRange = new BooleanProperty("CalcTicksWithRange", true);
    public final BooleanProperty lagReset = new BooleanProperty("LagReset", false);
    public final BooleanProperty attackToStart = new BooleanProperty("AttackToStart", false);
    public final BooleanProperty onlyKillAura = new BooleanProperty("OnlyKillAura", true);
    public final BooleanProperty onlyMove = new BooleanProperty("OnlyMove", true);
    public final BooleanProperty onlySprint = new BooleanProperty("OnlySprint", false);

    private final TimerUtil delayTimer = new TimerUtil();
    private int curDelay = 0;
    private boolean runTimer = false;
    private int skipTicks = 0;
    private int boostTicks = 0;
    private boolean isStuck = false; // 取代 UpdateEvent.setCancelled，用于拦截发包
    private boolean isCallingRunTick = false; // 防止 runTick 递归死循环

    public TickBase() {
        super("TickBase", false);
    }

    @Override
    public void onEnabled() {
        resetState();
    }

    @Override
    public void onDisabled() {
        resetState();
    }

    private void resetState() {
        skipTicks = 0;
        boostTicks = 0;
        runTimer = false;
        isStuck = false;
        isCallingRunTick = false;
        delayTimer.reset();
        curDelay = (int) RandomUtil.nextLong(minNextDelay.getValue(), maxNextDelay.getValue());
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (onlySprint.getValue() && !mc.thePlayer.isSprinting()) return;
        if (onlyMove.getValue() && (mc.thePlayer.movementInput.moveForward == 0 && mc.thePlayer.movementInput.moveStrafe == 0)) return;

        if (runTimer || !attackToStart.getValue() || !delayTimer.hasTimeElapsed(curDelay)) return;
        if (onlyKillAura.getValue() && !Myau.moduleManager.modules.get(KillAura.class).isEnabled()) return;

        Entity target = event.getTarget();
        if (target instanceof EntityLivingBase && isValidTarget((EntityLivingBase) target)) {
            EntityLivingBase l = (EntityLivingBase) target;
            double range = RotationUtil.distanceToEntity(l);

            if (l.hurtTime >= targetMinHurtTime.getValue() && l.hurtTime <= targetMaxHurtTime.getValue()
                    && range >= minStartRange.getValue() && range <= maxStartRange.getValue()) {

                if (startChance.getValue() >= RandomUtil.nextLong(0, 100)) {
                    startTimer(range);
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (onlySprint.getValue() && !mc.thePlayer.isSprinting()) return;
        if (onlyMove.getValue() && (mc.thePlayer.movementInput.moveForward == 0 && mc.thePlayer.movementInput.moveStrafe == 0)) return;

        if (runTimer || !delayTimer.hasTimeElapsed(curDelay) || attackToStart.getValue()) return;
        if (onlyKillAura.getValue() && !Myau.moduleManager.modules.get(KillAura.class).isEnabled()) return;

        List<EntityLivingBase> targets = getTargets(maxStartRange.getValue() + 1.0);
        if (!targets.isEmpty()) {
            targets.sort(Comparator.comparingDouble(e -> RotationUtil.distanceToEntity(e)));
            EntityLivingBase target = targets.get(0);
            double range = RotationUtil.distanceToEntity(target);

            if (target.hurtTime >= targetMinHurtTime.getValue() && target.hurtTime <= targetMaxHurtTime.getValue()
                    && range >= minStartRange.getValue() && range <= maxStartRange.getValue()) {

                if (startChance.getValue() >= RandomUtil.nextLong(0, 100)) {
                    startTimer(range);
                }
            }
        }
    }

    private void startTimer(double range) {
        runTimer = true;
        if (calculateTicksWithRange.getValue()) {
            double ratio = Math.min(1.0, range / 10.0 + Math.random() * (1.0 - range / 10.0));
            int ticks = (int) (minTicks.getValue() + ratio * (maxTicks.getValue() - minTicks.getValue()));
            boostTicks = skipTicks = ticks;
        } else {
            boostTicks = skipTicks = (int) RandomUtil.nextLong(minTicks.getValue(), maxTicks.getValue());
        }
        curDelay = (int) RandomUtil.nextLong(minNextDelay.getValue(), maxNextDelay.getValue());
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || isCallingRunTick) return;

        if (runTimer) {
            isStuck = false;
            if (mode.getValue() == 0) { // Stuck
                if (skipTicks > 0) {
                    skipTicks--;
                    isStuck = true; // 标记暂停，用于拦截发包
                } else if (boostTicks > 0) {
                    isCallingRunTick = true; // 加锁防止递归
                    try {
                        while (boostTicks > 0) {
                            boostTicks--;
                            ((IAccessorMinecraft) mc).callRunTick(); // 强行运行 Tick
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    isCallingRunTick = false; // 解锁
                } else {
                    runTimer = false;
                }
            } else if (mode.getValue() == 1) { // Boost
                if (boostTicks > 0) {
                    isCallingRunTick = true;
                    try {
                        while (boostTicks > 0) {
                            boostTicks--;
                            ((IAccessorMinecraft) mc).callRunTick();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    isCallingRunTick = false;
                } else if (skipTicks > 0) {
                    skipTicks--;
                    isStuck = true;
                } else {
                    runTimer = false;
                }
            }
            delayTimer.reset();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        // 暂停阶段：取消发送位置包，让服务端以为你卡住了
        if (isStuck && event.getPacket() instanceof C03PacketPlayer) {
            event.setCancelled(true);
        }
        // 被强制拉回时重置
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            if (runTimer && lagReset.getValue()) {
                resetState();
            }
        }
    }

    private List<EntityLivingBase> getTargets(double range) {
        List<EntityLivingBase> targets = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && isValidTarget((EntityLivingBase) entity)) {
                if (RotationUtil.distanceToEntity((EntityLivingBase) entity) <= range) {
                    targets.add((EntityLivingBase) entity);
                }
            }
        }
        return targets;
    }

    private boolean isValidTarget(EntityLivingBase e) {
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) return false;
        if (e.deathTime > 0) return false;
        if (!(e instanceof EntityPlayer)) return false;
        EntityPlayer ep = (EntityPlayer) e;
        if (TeamUtil.isFriend(ep)) return false;
        return true;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue() == 0 ? "Stuck" : "Boost"};
    }
}