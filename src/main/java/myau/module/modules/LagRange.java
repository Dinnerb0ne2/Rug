package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int tickIndex = -1;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;
    private long lastReleaseTime = 0;

    public final BooleanProperty dynamic = new BooleanProperty("Dynamic", true);
    public final IntProperty minDelay = new IntProperty("MinDelay", 350, 0, 2000);
    public final IntProperty maxDelay = new IntProperty("MaxDelay", 400, 0, 2000);
    public final IntProperty coolDownTimer = new IntProperty("CoolDown", 0, 0, 1000);
    public final FloatProperty attackRange = new FloatProperty("AttackRange", 3.0F, 0.0F, 6.0F);
    public final FloatProperty initialRange = new FloatProperty("InitialRange", 8.0F, 0.0F, 12.0F);
    public final IntProperty hurtTime = new IntProperty("HurtTime", 3, 0, 10);
    public final BooleanProperty onlyAura = new BooleanProperty("OnlyAura", true);
    public final BooleanProperty weaponsOnly = new BooleanProperty("WeaponsOnly", true);
    public final BooleanProperty allowTools = new BooleanProperty("AllowTools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("BotCheck", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    public final ModeProperty showPosition = new ModeProperty("ShowPosition", 0, new String[]{"NONE", "DEFAULT", "HUD"});

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    private boolean isPlayerMovingCloser(EntityPlayer target) {
        if (currentPosition == null || lastPosition == null) return true;
        double prevDist = target.getDistanceSq(lastPosition.xCoord, lastPosition.yCoord, lastPosition.zCoord);
        double currDist = target.getDistanceSq(currentPosition.xCoord, currentPosition.yCoord, currentPosition.zCoord);
        return currDist < prevDist;
    }

    private void resetLagState() {
        Myau.lagManager.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L; // 修复：必须清零，防止下次累加导致延迟爆炸
        this.hasTarget = false;
        lastReleaseTime = System.currentTimeMillis();
    }

    public LagRange() {
        super("LagRange", false);
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    Myau.lagManager.setDelay(0);
                    this.hasTarget = false;
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);

                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        if (ka.isEnabled() || !this.onlyAura.getValue()) {
                            List<EntityPlayer> players = mc.theWorld
                                    .loadedEntityList
                                    .stream()
                                    .filter(entity -> entity instanceof EntityPlayer)
                                    .map(entity -> (EntityPlayer) entity)
                                    .filter(this::isValidTarget)
                                    .collect(Collectors.toList());
                            if (players.isEmpty()) {
                                if (this.tickIndex > 0) resetLagState();
                            } else {
                                double height = mc.thePlayer.getEyeHeight();
                                Vec3 eyePosition = Myau.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                                Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                                Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                                for (EntityPlayer player : players) {
                                    double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                                    
                                    // 如果已经在攻击范围内，不需要滞后
                                    if (distance < (double) this.attackRange.getValue()) {
                                        continue;
                                    }
                                    
                                    // 如果超出初始范围，跳过
                                    if (distance > (double) this.initialRange.getValue()) {
                                        continue;
                                    }

                                    // 如果没有在靠近目标，跳过
                                    if (!isPlayerMovingCloser(player)) {
                                        continue;
                                    }

                                    // 如果在冷却中，跳过
                                    if (System.currentTimeMillis() - lastReleaseTime < coolDownTimer.getValue()) {
                                        continue;
                                    }

                                    double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                    double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                    
                                    // 如果当前位置比旧位置更靠近目标
                                    if (distance < targetDist || distance < eyeDist) {
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            this.delayCounter = 0L; // 修复：计算前强制清零
                                            long delayMs = minDelay.getValue() + (long) (Math.random() * (maxDelay.getValue() - minDelay.getValue()));
                                            for (this.delayCounter = this.delayCounter + delayMs;
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        Myau.lagManager.setDelay(this.tickIndex);
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                        } else {
                            if (this.tickIndex > 0) resetLagState();
                        }
                    } else {
                        if (this.tickIndex > 0) resetLagState();
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Myau.lagManager.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            Packet<?> packet = event.getPacket();
            
            // 修复：增加防拉回保护
            if (packet instanceof S08PacketPlayerPosLook) {
                if (this.tickIndex > 0) {
                    resetLagState();
                }
                return;
            }

            // 修复：只要发出攻击包，说明 KillAura 判定距离已够，必须立刻释放真实位置让服务器校验通过
            if (packet instanceof C02PacketUseEntity && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity entity = ((C02PacketUseEntity) packet).getEntityFromWorld(mc.theWorld);
                if (entity instanceof EntityLivingBase) {
                    if (this.tickIndex > 0) {
                        resetLagState();
                    }
                }
            }
            
            if (this.shouldResetOnPacket(packet)) {
                if (this.tickIndex > 0) {
                    resetLagState();
                }
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        resetLagState();
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%dms", this.minDelay.getValue())};
    }
}