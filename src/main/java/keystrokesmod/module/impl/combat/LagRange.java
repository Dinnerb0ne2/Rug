package keystrokesmod.module.impl.combat;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ModeSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.classes.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LagRange extends Module {
    private final SliderSetting lagTime;
    private final SliderSetting minRange;
    private final SliderSetting maxRange;
    private final SliderSetting delay;
    private final SliderSetting fov;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting onlyOnGround;
    private final ButtonSetting onlyWhenAttacking;
    private final ButtonSetting randomizeLagTime;
    private final SliderSetting randomizationFactor;
    private final ModeSetting priorityMode;
    private final ButtonSetting checkPing;
    private final SliderSetting maxPing;
    private final ButtonSetting dynamicRange;
    private final SliderSetting rangeAdjustSpeed;
    private final ButtonSetting bypassMode;
    private final SliderSetting bypassVariance;

    private long lastLagTime = 0;
    private double currentDynamicRange;
    private int bypassCounter = 0;
    private final Map<EntityPlayer, Long> playerLastSeenMap = new HashMap<>();
    private final Map<EntityPlayer, Integer> playerPingMap = new HashMap<>();

    private static final String[] PRIORITY_MODES = {"Distance", "Health", "Ping", "Threat"};
    private static final int PING_UPDATE_INTERVAL = 5000;

    public LagRange() {
        super("LagRange", category.combat);

        this.registerSetting(new DescriptionSetting("Basic Settings"));
        this.registerSetting(lagTime = new SliderSetting("Lag time", 50, 0, 500, 10, "ms"));
        this.registerSetting(randomizeLagTime = new ButtonSetting("Randomize lag time", true));
        this.registerSetting(randomizationFactor = new SliderSetting("Randomization %", 20, 5, 50, 5));
        this.registerSetting(minRange = new SliderSetting("Min range", 3.6, 0, 8, 0.1));
        this.registerSetting(maxRange = new SliderSetting("Max range", 5, 0, 8, 0.1));
        this.registerSetting(dynamicRange = new ButtonSetting("Dynamic range", false));
        this.registerSetting(rangeAdjustSpeed = new SliderSetting("Range adjust speed", 0.1, 0.01, 0.5, 0.01));
        this.registerSetting(delay = new SliderSetting("Delay", 150, 50, 2000, 50, "ms"));
        this.registerSetting(fov = new SliderSetting("FOV", 180, 0, 360, 30));

        this.registerSetting(new DescriptionSetting("Target Selection"));
        this.registerSetting(priorityMode = new ModeSetting("Priority mode", PRIORITY_MODES, 0));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(checkPing = new ButtonSetting("Check ping", false));
        this.registerSetting(maxPing = new SliderSetting("Max ping", 200, 50, 1000, 50, "ms"));

        this.registerSetting(new DescriptionSetting("Activation Conditions"));
        this.registerSetting(onlyOnGround = new ButtonSetting("Only on ground", false));
        this.registerSetting(onlyWhenAttacking = new ButtonSetting("Only when attacking", false));

        this.registerSetting(new DescriptionSetting("Anti-Cheat Bypass"));
        this.registerSetting(bypassMode = new ButtonSetting("Bypass mode", true));
        this.registerSetting(bypassVariance = new SliderSetting("Bypass variance", 30, 5, 100, 5, "%"));

        currentDynamicRange = minRange.getInput();
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (!shouldStart()) return;

        long calculatedLagTime = (long) lagTime.getInput();
        if (randomizeLagTime.isToggled()) {
            double randomFactor = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * (randomizationFactor.getInput() / 50);
            calculatedLagTime = (long) (calculatedLagTime * randomFactor);
        }
        if (bypassMode.isToggled()) {
            bypassCounter++;
            if (bypassCounter % 3 == 0) {
                double variance = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * (bypassVariance.getInput() / 50);
                calculatedLagTime = (long) (calculatedLagTime * variance);
            }
        }
        try {
            Thread.sleep(calculatedLagTime);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        lastLagTime = System.currentTimeMillis();
        updateDynamicRange();
    }

    private boolean shouldStart() {
        if (!Utils.nullCheck()) return false;
        if (onlyOnGround.isToggled() && !mc.thePlayer.onGround) return false;
        if (onlyWhenAttacking.isToggled() && !mc.thePlayer.isSwingInProgress) return false;
        if (!Utils.isMoving()) return false;
        if (fov.getInput() == 0) return false;
        if (System.currentTimeMillis() - lastLagTime < delay.getInput()) return false;

        if (checkPing.isToggled() && mc.theWorld.getTotalWorldTime() % PING_UPDATE_INTERVAL == 0) {
            updatePlayerPings();
        }

        EntityPlayer target = selectTarget();
        if (target == null) return false;

        updatePlayerLastSeen();

        double distance = new Vec3(target).distanceTo(mc.thePlayer);
        double effectiveMinRange = dynamicRange.isToggled() ? currentDynamicRange : minRange.getInput();
        return distance >= effectiveMinRange && distance <= maxRange.getInput();
    }

    private EntityPlayer selectTarget() {
        List<EntityPlayer> validPlayers = mc.theWorld.playerEntities.stream()
                .filter(p -> p != mc.thePlayer)
                .filter(p -> !ignoreTeammates.isToggled() || !Utils.isTeamMate(p))
                .filter(p -> !Utils.isFriended(p))
                .filter(p -> !AntiBot.isBot(p))
                .filter(p -> fov.getInput() == 360 || Utils.inFov((float) fov.getInput(), p))
                .filter(p -> !checkPing.isToggled() || playerPingMap.getOrDefault(p, 0) <= maxPing.getInput())
                .collect(Collectors.toList());

        if (validPlayers.isEmpty()) return null;

        switch ((int) priorityMode.getInput()) {
            case 0:
                return validPlayers.stream()
                        .min(Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)))
                        .orElse(null);
            case 1:
                return validPlayers.stream()
                        .min(Comparator.comparingDouble(EntityLivingBase::getHealth))
                        .orElse(null);
            case 2:
                if (!checkPing.isToggled()) return validPlayers.get(0);
                return validPlayers.stream()
                        .min(Comparator.comparingInt(p -> playerPingMap.getOrDefault(p, 0)))
                        .orElse(null);
            case 3:
                return validPlayers.stream()
                        .max(Comparator.comparingDouble(this::calculateThreatScore))
                        .orElse(null);
            default:
                return validPlayers.get(0);
        }
    }

    private double calculateThreatScore(EntityPlayer player) {
        double distanceScore = 1 - (mc.thePlayer.getDistanceToEntity(player) / 10.0);
        double healthScore = 1 - (player.getHealth() / player.getMaxHealth());
        double pingScore = checkPing.isToggled() ? (1 - (playerPingMap.getOrDefault(player, 0) / maxPing.getInput())) : 0.5;
        double lastSeenScore = (System.currentTimeMillis() - playerLastSeenMap.getOrDefault(player, 0L)) / 1000.0;
        return (distanceScore * 0.4) + (healthScore * 0.3) + (pingScore * 0.2) + (lastSeenScore * 0.1);
    }

    private void updatePlayerLastSeen() {
        mc.theWorld.playerEntities.forEach(player -> {
            if (player != mc.thePlayer) playerLastSeenMap.put(player, System.currentTimeMillis());
        });
    }

    private void updatePlayerPings() {
        mc.theWorld.playerEntities.forEach(player -> {
            if (player != mc.thePlayer) {
                playerPingMap.put(player, ThreadLocalRandom.current().nextInt(50, 300));
            }
        });
    }

    private void updateDynamicRange() {
        if (!dynamicRange.isToggled()) return;
        double targetRange = (minRange.getInput() + maxRange.getInput()) / 2;
        double adjustment = rangeAdjustSpeed.getInput() * (targetRange - currentDynamicRange);
        currentDynamicRange = Utils.limit(
                currentDynamicRange + adjustment,
                minRange.getInput(),
                maxRange.getInput()
        );
    }

    @Override
    public void onDisable() {
        playerLastSeenMap.clear();
        playerPingMap.clear();
        currentDynamicRange = minRange.getInput();
        bypassCounter = 0;
    }

    @Override
    public String getInfo() {
        return String.format("%dms%s", (int) lagTime.getInput(), dynamicRange.isToggled() ? " (D)" : "");
    }
}
