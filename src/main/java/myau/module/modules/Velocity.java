package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ChatUtil;
import myau.util.MoveUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.LinkedList;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int chanceCounter = 0;
    private int delayChanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean jumpFlag = false;
    private boolean reverseFlag = false;
    private boolean delayActive = false;
    private boolean shouldJump = false;
    private int jumpCooldown = 0;

    private boolean forwardActive = false;
    private int forwardTicks = 0;
    private boolean jumped = false;
    private int jumpTicks = 0;

    private long lastAttackTime = 0;
    private int intaveTick = 0;
    private int intaveDamageTick = 0;
    private boolean hasReceivedVelocity = false;

    private boolean blockVelocity = false;
    private boolean isWorking = false;
    private boolean hitable = false;

    private int polarHurtTime = 8;

    private final LinkedList<S32PacketConfirmTransaction> kkcPackets = new LinkedList<>();
    private final TimerUtil kkcActiveTimer = new TimerUtil();
    private final TimerUtil kkcCooldownTimer = new TimerUtil();
    private boolean kkcCancel = false;

    private boolean stopJumping = false;

    private int multReduceTick = 0;

    private boolean sprintResetActive = false;
    private int sprintResetTicks = 0;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{
            "VANILLA", "SIMPLE", "JUMP", "GLITCH", "DELAY", "REVERSE",
            "INTAVE_A", "INTAVE_B", "POLAR", "ADAPT", "SPRINT_RESET", "KKC", "LEGIT"
    });
    public final ModeProperty motionMode = new ModeProperty("motion-mode", 0, new String[]{"NONE", "MULT_REDUCE"});

    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, () -> this.mode.getValue() == 4);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, () -> this.mode.getValue() == 4);
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final FloatProperty intaveFactor = new FloatProperty("intave-factor", 0.6F, 0.0F, 1.0F, () -> this.mode.getValue() == 6);
    public final IntProperty kkcActiveMs = new IntProperty("kkc-active", 600, 0, 2000, () -> this.mode.getValue() == 11);
    public final IntProperty kkcCooldownMs = new IntProperty("kkc-cooldown", 400, 0, 2000, () -> this.mode.getValue() == 11);

    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty noFire = new BooleanProperty("no-fire", true);
    public final BooleanProperty noFluid = new BooleanProperty("no-fluid", true);
    public final BooleanProperty noVehicle = new BooleanProperty("no-vehicle", true);
    public final BooleanProperty invCheck = new BooleanProperty("inv-check", true);
    public final BooleanProperty onlyAura = new BooleanProperty("only-aura", false);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    public Velocity() {
        super("Velocity", false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private boolean shouldCancelVelocity() {
        if ((this.noFire.getValue() && mc.thePlayer.isBurning())
                || (this.noFluid.getValue() && isInLiquidOrWeb())
                || (this.noVehicle.getValue() && mc.thePlayer.isRiding())
                || (this.invCheck.getValue() && mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))) {
            return true;
        }
        if (this.onlyAura.getValue()) {
            KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            return !killAura.isEnabled();
        }
        return false;
    }

    private void tryJump() {
        mc.thePlayer.movementInput.jump = true;
    }

    private void tryForward() {
        mc.thePlayer.movementInput.moveForward = 1.0f;
    }

    private EntityPlayer getClosestPlayer() {
        EntityPlayer closest = null;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (closest == null || mc.thePlayer.getDistanceToEntity(player) < mc.thePlayer.getDistanceToEntity(closest)) {
                closest = player;
            }
        }
        return closest;
    }

    private boolean isCrosshairOnEntity(Entity target) {
        if (target == null) return false;
        float borderSize = target.getCollisionBorderSize();
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
        float pitch = mc.thePlayer.rotationPitch;
        Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        double reach = mc.thePlayer.getDistanceToEntity(target);
        Vec3 eyesPos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 rayEnd = eyesPos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
        return bb.calculateIntercept(eyesPos, rayEnd) != null;
    }

    private void processKkcPackets() {
        if (kkcPackets.isEmpty()) return;
        S32PacketConfirmTransaction p = kkcPackets.poll();
        if (p != null) {
            mc.addScheduledTask(() -> {
                try {
                    mc.thePlayer.sendQueue.handleConfirmTransaction(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (!this.allowNext || !this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                if (this.chanceCounter >= 100) {
                    int m = this.mode.getValue();
                    this.jumpFlag = (m == 2 || m == 4) && event.getY() > 0.0;
                    this.delayActive = m == 5;

                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) return;
        if (this.shouldCancelVelocity()) return;

        if (this.reverseFlag && (this.canDelay() || this.isInLiquidOrWeb() || Myau.delayManager.getDelay() >= (long) this.delayTicks.getValue())) {
            Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
            this.reverseFlag = false;
        }
        if (this.delayActive) {
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
            this.delayActive = false;
        }

        if (this.jumped) {
            this.jumpTicks++;
            if (this.jumpTicks >= 2) {
                this.jumped = false;
                this.jumpTicks = 0;
            }
        }
        if (this.forwardActive) {
            this.forwardTicks++;
            if (this.forwardTicks >= 2) {
                this.forwardActive = false;
                this.forwardTicks = 0;
            }
        }

        int m = this.mode.getValue();

        switch (m) {
            case 3:
                if (mc.thePlayer.hurtTime == 9) {
                    tryJump();
                }
                break;

            case 6:
                if (this.hasReceivedVelocity) {
                    this.intaveTick++;
                    if (mc.thePlayer.hurtTime == 2) {
                        this.intaveDamageTick++;
                        if (mc.thePlayer.onGround && this.intaveTick % 2 == 0 && this.intaveDamageTick <= 10) {
                            tryJump();
                            this.intaveTick = 0;
                        } else {
                            this.intaveDamageTick = 0;
                        }
                        this.hasReceivedVelocity = false;
                    }
                }
                break;

            case 7:
                EntityPlayer targetB = getClosestPlayer();
                if (targetB != null) {
                    this.hitable = isCrosshairOnEntity(targetB);
                    this.blockVelocity = true;
                    if (this.hitable) {
                        if (mc.thePlayer.hurtTime == 9 && !mc.thePlayer.isBurning()) {
                            tryJump();
                        }
                        if (mc.thePlayer.hurtTime > 0) {
                            tryForward();
                        }
                    }
                }
                break;

            case 8:
                if (mc.thePlayer.hurtTime == this.polarHurtTime) {
                    tryJump();
                    this.polarHurtTime = Math.random() > 0.5 ? 8 : 9;
                }
                break;

            case 9:
                EntityPlayer adaptTarget = getClosestPlayer();
                boolean targetFalling = adaptTarget != null && adaptTarget.lastTickPosY - adaptTarget.posY > 0;
                if (mc.thePlayer.hurtTime == 8 || !targetFalling) {
                    this.stopJumping = false;
                }
                if (mc.thePlayer.hurtTime == 9 && !this.stopJumping) {
                    tryJump();
                }
                break;

            case 10:
                if (this.sprintResetActive) {
                    mc.thePlayer.setSprinting(false);
                    mc.thePlayer.movementInput.moveForward = 0.0f;
                    this.sprintResetTicks++;
                    if (this.sprintResetTicks >= 2) {
                        this.sprintResetActive = false;
                        this.sprintResetTicks = 0;
                    }
                }
                break;

            case 12:
                int hurtTime = mc.thePlayer.hurtTime;
                if (hurtTime >= 8) {
                    if (this.jumpCooldown <= 0) {
                        this.shouldJump = true;
                        this.jumpCooldown = 2;
                    }
                } else if (hurtTime <= 1) {
                    this.shouldJump = false;
                    this.jumpCooldown = 0;
                }
                if (this.shouldJump && mc.thePlayer.onGround && this.jumpCooldown <= 0) {
                    mc.thePlayer.jump();
                    this.shouldJump = false;
                }
                if (this.jumpCooldown > 0) {
                    this.jumpCooldown--;
                }
                break;
        }

        if (this.motionMode.getValue() == 1) {
            if (mc.thePlayer.hurtTime >= 6) {
                this.multReduceTick++;
                double decayFactor = this.multReduceTick < 3 ? 0.92 : Math.pow(0.85, this.multReduceTick - 2);
                mc.thePlayer.motionX *= decayFactor;
                mc.thePlayer.motionZ *= decayFactor;
            } else {
                this.multReduceTick = 0;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled()) return;
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting()
                    && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || this.shouldCancelVelocity()) return;

        switch (this.mode.getValue()) {
            case 6:
                if (mc.thePlayer.hurtTime == 9 && (System.currentTimeMillis() - this.lastAttackTime) <= 8000) {
                    float f = this.intaveFactor.getValue();
                    mc.thePlayer.motionX *= f;
                    mc.thePlayer.motionZ *= f;
                }
                this.lastAttackTime = System.currentTimeMillis();
                break;

            case 7:
                if (this.hitable && mc.thePlayer.hurtTime > 0 && this.blockVelocity) {
                    mc.thePlayer.setSprinting(false);
                    if (mc.thePlayer.hurtTime <= 6 && this.isWorking) {
                        float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw);
                        mc.thePlayer.motionX = -Math.sin(yaw * (Math.PI / 180)) * 0.02;
                        mc.thePlayer.motionZ = Math.cos(yaw * (Math.PI / 180)) * 0.02;
                        this.isWorking = false;
                    }
                    this.blockVelocity = false;
                }
                break;

            case 9:
                this.stopJumping = true;
                break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                double motX = (double) packet.getMotionX() / 8000.0;
                double motY = (double) packet.getMotionY() / 8000.0;
                double motZ = (double) packet.getMotionZ() / 8000.0;
                boolean isRealVelocity = !(motX == 0.0 && motY == 0.0 && motZ == -0.078375);

                if (isRealVelocity) {
                    this.hasReceivedVelocity = true;
                }

                int m = this.mode.getValue();
                LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);

                if (m == 4 && !this.reverseFlag && !this.canDelay() && !this.isInLiquidOrWeb()
                        && !this.pendingExplosion
                        && (!this.allowNext || !this.fakeCheck.getValue())
                        && (!longJump.isEnabled() || !longJump.canStartJump())) {
                    this.delayChanceCounter = this.delayChanceCounter % 100 + this.delayChance.getValue();
                    if (this.delayChanceCounter >= 100) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        this.reverseFlag = true;
                        return;
                    }
                } else if (m == 1) {
                    event.setCancelled(true);
                    return;
                } else if (m == 7 && isRealVelocity) {
                    this.isWorking = true;
                } else if (m == 10 && isRealVelocity) {
                    mc.thePlayer.setSprinting(false);
                    this.sprintResetActive = true;
                    this.sprintResetTicks = 0;
                    this.hasReceivedVelocity = false;
                } else if (m == 11) {
                    if (mc.thePlayer.hurtTime == 9 && !this.kkcCancel) {
                        this.kkcActiveTimer.reset();
                    }

                    boolean inActiveWindow = !this.kkcActiveTimer.hasTimeElapsed((long) this.kkcActiveMs.getValue());

                    if (inActiveWindow || this.kkcCancel) {
                        event.setCancelled(true);
                        this.kkcCancel = true;
                        this.kkcCooldownTimer.reset();
                    } else {
                        processKkcPackets();
                        if (this.kkcPackets.isEmpty() && this.kkcCooldownTimer.hasTimeElapsed((long) this.kkcCooldownMs.getValue())) {
                            this.kkcCancel = false;
                        }
                    }
                }

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format("%sVelocity (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                                    Myau.clientName, mc.thePlayer.ticksExisted, motX, motY, motZ)
                    );
                }
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                this.pendingExplosion = true;
                if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                    event.setCancelled(true);
                }
                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format("%sExplosion (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                                    Myau.clientName, mc.thePlayer.ticksExisted,
                                    mc.thePlayer.motionX + (double) packet.func_149149_c(),
                                    mc.thePlayer.motionY + (double) packet.func_149144_d(),
                                    mc.thePlayer.motionZ + (double) packet.func_149147_e())
                    );
                }
            }
        } else if (event.getPacket() instanceof S32PacketConfirmTransaction) {
            if (this.mode.getValue() == 11 && (this.kkcCancel || !this.kkcActiveTimer.hasTimeElapsed((long) this.kkcActiveMs.getValue()))) {
                this.kkcPackets.add((S32PacketConfirmTransaction) event.getPacket());
                event.setCancelled(true);
            }
        } else if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                this.allowNext = false;
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.onDisabled();
    }

    @Override
    public void onDisabled() {
        this.pendingExplosion = false;
        this.allowNext = true;
        this.shouldJump = false;
        this.jumpCooldown = 0;
        this.forwardActive = false;
        this.forwardTicks = 0;
        this.jumped = false;
        this.jumpTicks = 0;
        this.hasReceivedVelocity = false;
        this.intaveTick = 0;
        this.intaveDamageTick = 0;
        this.isWorking = false;
        this.blockVelocity = false;
        this.kkcCancel = false;
        this.kkcPackets.clear();
        this.sprintResetActive = false;
        this.sprintResetTicks = 0;
        this.stopJumping = false;
        this.multReduceTick = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}