package keystrokesmod.module.impl.combat;

import akka.japi.Pair;
import keystrokesmod.event.MoveEvent;
import keystrokesmod.event.RotationEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.RotationHandler;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ComboSetting;
import keystrokesmod.script.classes.Vec3;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Comparator;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimerRange extends Module {
    // General settings
    private final DescriptionSetting modeDesc;
    private final ComboSetting mode;
    private final SliderSetting chance;
    private final ButtonSetting randomizeValues;
    
    // Timer settings
    private final SliderSetting timerTicks;
    private final SliderSetting timerSpeed;
    private final SliderSetting timerRandomness;
    
    // Lag settings
    private final SliderSetting lagTicks;
    private final SliderSetting lagRandomness;
    private final ButtonSetting lagPacketModify;
    private final SliderSetting lagPacketOffset;
    
    // Range settings
    private final SliderSetting minRange;
    private final SliderSetting maxRange;
    private final SliderSetting rangeCheckInterval;
    private final ButtonSetting dynamicRange;
    
    // Target selection
    private final SliderSetting fov;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting preferClosest;
    private final ButtonSetting preferLowHealth;
    private final SliderSetting targetSwitchDelay;
    
    // Conditions
    private final ButtonSetting onlyOnGround;
    private final ButtonSetting clearMotion;
    private final ButtonSetting notWhileKB;
    private final SliderSetting kbThreshold;
    private final ButtonSetting notWhileScaffold;
    private final ButtonSetting onlyWhileMoving;
    private final ButtonSetting onlyWhenHoldingWeapon;
    private final ButtonSetting onlyInCombat;
    private final SliderSetting combatTimeout;
    
    // Bypass settings
    private final ButtonSetting randomizePackets;
    private final ButtonSetting splitPackets;
    private final ButtonSetting delaySomePackets;
    private final SliderSetting bypassDelay;
    
    // Visual settings
    private final ButtonSetting renderTarget;
    private final ComboSetting renderMode;
    private final SliderSetting renderWidth;
    private final SliderSetting renderColorR;
    private final SliderSetting renderColorG;
    private final SliderSetting renderColorB;
    private final SliderSetting renderColorA;
    
    private final Queue<Packet<?>> delayedPackets = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> bypassDelayedPackets = new ConcurrentLinkedQueue<>();
    private State state = State.NONE;
    private int hasLag = 0;
    private int timerTicksUsed = 0;
    private long lastTimerTime = -1;
    private long lastTargetSwitchTime = -1;
    private long lastCombatTime = -1;
    private float yaw, pitch;
    private double motionX, motionY, motionZ;
    private EntityPlayer currentTarget;
    private Random random = new Random();
    private double currentTimerSpeed = 1.0;
    private int currentLagTicks = 0;
    private boolean isInCombat = false;
    
    // Advanced prediction
    private Vec3 predictedTargetPos;
    private Vec3 predictedPlayerPos;
    private int predictionTicks = 2;

    public TimerRange() {
        super("TimerRange+", category.combat);
        
        // General settings
        this.registerSetting(modeDesc = new DescriptionSetting("Advanced TimerRange module"));
        this.registerSetting(mode = new ComboSetting("Mode", new String[]{"Normal", "Smart", "Aggressive", "Bypass"}, 0));
        this.registerSetting(chance = new SliderSetting("Activation chance", 100, 0, 100, 1, "%"));
        this.registerSetting(randomizeValues = new ButtonSetting("Randomize values", true));
        
        // Timer settings
        this.registerSetting(new DescriptionSetting("Timer Settings"));
        this.registerSetting(timerTicks = new SliderSetting("Timer ticks", 2, 0, 10, 1));
        this.registerSetting(timerSpeed = new SliderSetting("Timer speed", 2.0, 1.0, 5.0, 0.1));
        this.registerSetting(timerRandomness = new SliderSetting("Timer randomness", 0.2, 0.0, 1.0, 0.05));
        
        // Lag settings
        this.registerSetting(new DescriptionSetting("Lag Settings"));
        this.registerSetting(lagTicks = new SliderSetting("Lag ticks", 2, 0, 10, 1));
        this.registerSetting(lagRandomness = new SliderSetting("Lag randomness", 0, 0, 5, 1));
        this.registerSetting(lagPacketModify = new ButtonSetting("Modify lag packets", false));
        this.registerSetting(lagPacketOffset = new SliderSetting("Packet offset", 0.01, 0.001, 0.1, 0.001));
        
        // Range settings
        this.registerSetting(new DescriptionSetting("Range Settings"));
        this.registerSetting(minRange = new SliderSetting("Min range", 3.2, 0, 8, 0.1));
        this.registerSetting(maxRange = new SliderSetting("Max range", 5.5, 0, 8, 0.1));
        this.registerSetting(rangeCheckInterval = new SliderSetting("Check interval", 5, 1, 20, 1, "ticks"));
        this.registerSetting(dynamicRange = new ButtonSetting("Dynamic range", true));
        
        // Target selection
        this.registerSetting(new DescriptionSetting("Target Selection"));
        this.registerSetting(fov = new SliderSetting("FOV", 180, 0, 360, 30));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(preferClosest = new ButtonSetting("Prefer closest", true));
        this.registerSetting(preferLowHealth = new ButtonSetting("Prefer low health", false));
        this.registerSetting(targetSwitchDelay = new SliderSetting("Target switch delay", 500, 0, 2000, 50, "ms"));
        
        // Conditions
        this.registerSetting(new DescriptionSetting("Activation Conditions"));
        this.registerSetting(onlyOnGround = new ButtonSetting("Only on ground", false));
        this.registerSetting(clearMotion = new ButtonSetting("Clear motion", false));
        this.registerSetting(notWhileKB = new ButtonSetting("Not while KB", false));
        this.registerSetting(kbThreshold = new SliderSetting("KB threshold", 0.5, 0, 2, 0.1));
        this.registerSetting(notWhileScaffold = new ButtonSetting("Not while scaffold", true));
        this.registerSetting(onlyWhileMoving = new ButtonSetting("Only while moving", true));
        this.registerSetting(onlyWhenHoldingWeapon = new ButtonSetting("Only with weapon", true));
        this.registerSetting(onlyInCombat = new ButtonSetting("Only in combat", false));
        this.registerSetting(combatTimeout = new SliderSetting("Combat timeout", 2000, 500, 5000, 100, "ms"));
        
        // Bypass settings
        this.registerSetting(new DescriptionSetting("Bypass Settings"));
        this.registerSetting(randomizePackets = new ButtonSetting("Randomize packets", true));
        this.registerSetting(splitPackets = new ButtonSetting("Split packets", false));
        this.registerSetting(delaySomePackets = new ButtonSetting("Delay some packets", false));
        this.registerSetting(bypassDelay = new SliderSetting("Bypass delay", 50, 0, 200, 5, "ms"));
        
        // Visual settings
        this.registerSetting(new DescriptionSetting("Visual Settings"));
        this.registerSetting(renderTarget = new ButtonSetting("Render target", true));
        this.registerSetting(renderMode = new ComboSetting("Render mode", new String[]{"Box", "Outline", "Both"}, 0));
        this.registerSetting(renderWidth = new SliderSetting("Line width", 2.0, 0.5, 5.0, 0.5));
        this.registerSetting(renderColorR = new SliderSetting("Color R", 255, 0, 255, 1));
        this.registerSetting(renderColorG = new SliderSetting("Color G", 0, 0, 255, 1));
        this.registerSetting(renderColorB = new SliderSetting("Color B", 0, 0, 255, 1));
        this.registerSetting(renderColorA = new SliderSetting("Color A", 150, 0, 255, 5));
    }

    @Override
    public void onEnable() {
        state = State.NONE;
        hasLag = 0;
        timerTicksUsed = 0;
        lastTimerTime = -1;
        lastTargetSwitchTime = -1;
        lastCombatTime = -1;
        currentTarget = null;
        delayedPackets.clear();
        bypassDelayedPackets.clear();
        isInCombat = false;
    }

    @Override
    public void onDisable() {
        done();
        mc.timer.timerSpeed = 1.0f;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) return;
        
        // Combat detection
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            lastCombatTime = System.currentTimeMillis();
            isInCombat = true;
        } else if (System.currentTimeMillis() - lastCombatTime > combatTimeout.getInput()) {
            isInCombat = false;
        }
        
        // Randomize values if enabled
        if (randomizeValues.isToggled() && state == State.NONE) {
            currentTimerSpeed = timerSpeed.getInput() * (1 + (random.nextDouble() - 0.5) * timerRandomness.getInput());
            currentLagTicks = lagTicks.getInput() + random.nextInt((int)lagRandomness.getInput() + 1);
        } else {
            currentTimerSpeed = timerSpeed.getInput();
            currentLagTicks = lagTicks.getInput();
        }
        
        // Process bypass delayed packets
        if (!bypassDelayedPackets.isEmpty() && System.currentTimeMillis() - lastTimerTime > bypassDelay.getInput()) {
            synchronized (bypassDelayedPackets) {
                Packet<?> packet = bypassDelayedPackets.poll();
                if (packet != null) {
                    PacketUtils.sendPacket(packet);
                }
            }
        }
        
        switch (state) {
            case NONE:
                if (shouldStart()) {
                    if (random.nextInt(100) < chance.getInput()) {
                        state = State.TIMER;
                        timerTicksUsed = 0;
                    }
                }
                break;
                
            case TIMER:
                mc.timer.timerSpeed = (float) currentTimerSpeed;
                timerTicksUsed++;
                
                if (timerTicksUsed >= timerTicks.getInput()) {
                    yaw = RotationHandler.getRotationYaw();
                    pitch = RotationHandler.getRotationPitch();
                    motionX = mc.thePlayer.motionX;
                    motionY = mc.thePlayer.motionY;
                    motionZ = mc.thePlayer.motionZ;
                    hasLag = 0;
                    state = State.LAG;
                    
                    // Predict positions for next tick
                    predictPositions();
                }
                break;
                
            case LAG:
                if (hasLag >= currentLagTicks) {
                    done();
                } else {
                    hasLag++;
                    
                    // Modify player position slightly during lag phase for bypass
                    if (lagPacketModify.isToggled() && hasLag % 2 == 0) {
                        double offset = lagPacketOffset.getInput();
                        mc.thePlayer.posX += (random.nextDouble() - 0.5) * offset;
                        mc.thePlayer.posY += (random.nextDouble() - 0.5) * offset;
                        mc.thePlayer.posZ += (random.nextDouble() - 0.5) * offset;
                    }
                }
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent event) {
        if (event.getPacket() == null) return;
        
        switch (state) {
            case TIMER:
                synchronized (delayedPackets) {
                    if (randomizePackets.isToggled()) {
                        modifyPacketRandomly(event.getPacket());
                    }
                    
                    if (splitPackets.isToggled() && event.getPacket() instanceof C03PacketPlayer) {
                        splitPacket((C03PacketPlayer) event.getPacket());
                    } else {
                        delayedPackets.add(event.getPacket());
                    }
                    
                    event.setCanceled(true);
                }
                break;
                
            case LAG:
                if (event.getPacket() instanceof C03PacketPlayer) {
                    if (lagPacketModify.isToggled()) {
                        modifyLagPacket((C03PacketPlayer) event.getPacket());
                    }
                    event.setCanceled(true);
                } else {
                    synchronized (delayedPackets) {
                        if (delaySomePackets.isToggled() && random.nextBoolean()) {
                            bypassDelayedPackets.add(event.getPacket());
                        } else {
                            delayedPackets.add(event.getPacket());
                        }
                        event.setCanceled(true);
                    }
                }
                break;
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (state == State.LAG) {
            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent event) {
        if (state == State.TIMER && currentTarget != null) {
            // Adjust rotation more precisely during timer phase
            float[] rotations = Utils.getRotationsNeeded(currentTarget);
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);
        }
    }

    @SubscribeEvent
    public void onMove(@NotNull MoveEvent event) {
        if (state == State.LAG) {
            event.setCanceled(true);
            mc.thePlayer.motionX = motionX;
            mc.thePlayer.motionY = motionY;
            mc.thePlayer.motionZ = motionZ;
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (renderTarget.isToggled() && currentTarget != null) {
            Color color = new Color(
                (int)renderColorR.getInput(),
                (int)renderColorG.getInput(),
                (int)renderColorB.getInput(),
                (int)renderColorA.getInput()
            );
            
            AxisAlignedBB bb = currentTarget.getEntityBoundingBox()
                .offset(-mc.getRenderManager().renderPosX, 
                       -mc.getRenderManager().renderPosY, 
                       -mc.getRenderManager().renderPosZ);
                
            GL11.glLineWidth((float)renderWidth.getInput());
            
            if (renderMode.getMode() == 0 || renderMode.getMode() == 2) {
                RenderUtils.drawFilledBox(bb, color.getRGB());
            }
            
            if (renderMode.getMode() == 1 || renderMode.getMode() == 2) {
                RenderUtils.drawOutlinedBox(bb, color.getRGB());
            }
            
            GL11.glLineWidth(1.0f);
        }
    }

    private void done() {
        state = State.NONE;
        hasLag = 0;
        timerTicksUsed = 0;
        lastTimerTime = System.currentTimeMillis();
        mc.timer.timerSpeed = 1.0f;

        synchronized (delayedPackets) {
            for (Packet<?> p : delayedPackets) {
                PacketUtils.sendPacket(p);
            }
            delayedPackets.clear();
        }

        if (clearMotion.isToggled()) {
            mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        } else {
            mc.thePlayer.motionX = motionX;
            mc.thePlayer.motionY = motionY;
            mc.thePlayer.motionZ = motionZ;
        }
    }

    private boolean shouldStart() {
        if (!Utils.nullCheck()) return false;
        if (ModuleManager.blink.isEnabled()) return false;
        if (onlyOnGround.isToggled() && !mc.thePlayer.onGround) return false;
        if (notWhileKB.isToggled() && mc.thePlayer.hurtTime > 0 && 
            (Math.abs(mc.thePlayer.motionX) > kbThreshold.getInput() || 
             Math.abs(mc.thePlayer.motionZ) > kbThreshold.getInput())) return false;
        if (notWhileScaffold.isToggled() && ModuleManager.scaffold.isEnabled()) return false;
        if (onlyWhileMoving.isToggled() && !Utils.isMoving()) return false;
        if (onlyWhenHoldingWeapon.isToggled() && !Utils.isHoldingWeapon()) return false;
        if (onlyInCombat.isToggled() && !isInCombat) return false;
        if (fov.getInput() == 0) return false;
        if (System.currentTimeMillis() - lastTimerTime < delay.getInput()) return false;
        if (mc.thePlayer.ticksExisted % rangeCheckInterval.getInput() != 0) return false;

        // Advanced target selection
        EntityPlayer target = selectTarget();
        if (target == null) return false;

        currentTarget = target;
        
        double distance = new Vec3(target).distanceTo(mc.thePlayer);
        double effectiveMinRange = minRange.getInput();
        double effectiveMaxRange = maxRange.getInput();
        
        if (dynamicRange.isToggled()) {
            // Adjust range based on target's movement
            double speed = Math.sqrt(target.motionX * target.motionX + target.motionZ * target.motionZ);
            effectiveMinRange = Math.max(minRange.getInput() - speed * 2, 2.0);
            effectiveMaxRange = Math.min(maxRange.getInput() + speed * 2, 6.0);
        }
        
        return distance >= effectiveMinRange && distance <= effectiveMaxRange;
    }

    private EntityPlayer selectTarget() {
        List<EntityPlayer> potentialTargets = mc.theWorld.playerEntities.parallelStream()
            .filter(p -> p != mc.thePlayer)
            .filter(p -> !ignoreTeammates.isToggled() || !Utils.isTeamMate(p))
            .filter(p -> !Utils.isFriended(p))
            .filter(p -> !AntiBot.isBot(p))
            .filter(p -> fov.getInput() >= 360 || Utils.inFov((float) fov.getInput(), p))
            .collect(Collectors.toList());
            
        if (potentialTargets.isEmpty()) return null;
        
        // Apply target selection preferences
        Comparator<EntityPlayer> comparator = null;
        
        if (preferLowHealth.isToggled() && preferClosest.isToggled()) {
            comparator = Comparator.comparingDouble((EntityPlayer p) -> mc.thePlayer.getDistanceSqToEntity(p) * p.getHealth())
                                 .thenComparingDouble(p -> p.getHealth());
        } 
        else if (preferLowHealth.isToggled()) {
            comparator = Comparator.comparingDouble(EntityPlayer::getHealth);
        }
        else if (preferClosest.isToggled()) {
            comparator = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
        }
        
        EntityPlayer selected = null;
        
        if (comparator != null) {
            selected = potentialTargets.stream()
                .min(comparator)
                .orElse(null);
        } else {
            // Random selection if no preference
            selected = potentialTargets.get(random.nextInt(potentialTargets.size()));
        }
        
        // Check target switch delay
        if (selected != currentTarget && 
            System.currentTimeMillis() - lastTargetSwitchTime < targetSwitchDelay.getInput()) {
            return currentTarget;
        }
        
        if (selected != currentTarget) {
            lastTargetSwitchTime = System.currentTimeMillis();
        }
        
        return selected;
    }

    private void predictPositions() {
        if (currentTarget == null) return;
        
        // Simple linear prediction
        predictedTargetPos = new Vec3(
            currentTarget.posX + currentTarget.motionX * predictionTicks,
            currentTarget.posY + currentTarget.motionY * predictionTicks,
            currentTarget.posZ + currentTarget.motionZ * predictionTicks
        );
        
        predictedPlayerPos = new Vec3(
            mc.thePlayer.posX + mc.thePlayer.motionX * predictionTicks,
            mc.thePlayer.posY + mc.thePlayer.motionY * predictionTicks,
            mc.thePlayer.posZ + mc.thePlayer.motionZ * predictionTicks
        );
    }

    private void modifyPacketRandomly(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
            if (random.nextBoolean()) {
                playerPacket.y += (random.nextDouble() - 0.5) * 0.001;
            }
            if (random.nextBoolean()) {
                playerPacket.x += (random.nextDouble() - 0.5) * 0.001;
            }
            if (random.nextBoolean()) {
                playerPacket.z += (random.nextDouble() - 0.5) * 0.001;
            }
        }
    }

    private void modifyLagPacket(C03PacketPlayer packet) {
        double offset = lagPacketOffset.getInput();
        packet.x += (random.nextDouble() - 0.5) * offset;
        packet.y += (random.nextDouble() - 0.5) * offset;
        packet.z += (random.nextDouble() - 0.5) * offset;
    }

    private void splitPacket(C03PacketPlayer original) {
        // Split into two packets with slightly different positions
        C03PacketPlayer.C04PacketPlayerPosition packet1 = new C03PacketPlayer.C04PacketPlayerPosition(
            original.x + 0.001, original.y, original.z, original.onGround
        );
        
        C03PacketPlayer.C06PacketPlayerPosLook packet2 = new C03PacketPlayer.C06PacketPlayerPosLook(
            original.x - 0.001, original.y, original.z, 
            original.getYaw(mc.thePlayer.rotationYaw), 
            original.getPitch(mc.thePlayer.rotationPitch), 
            original.onGround
        );
        
        delayedPackets.add(packet1);
        delayedPackets.add(packet2);
    }

    @Override
    public String getInfo() {
        return mode.getMode() + "|" + (int)timerSpeed.getInput() + "x";
    }

    enum State {
        NONE,
        TIMER,
        LAG
    }
}