package myau.module;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.modules.*;
import myau.util.ChatUtil;
import myau.util.SoundUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final Map<Class<?>, Module.Category> categoryByClass = new HashMap<>();
    private boolean sound = false;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<Class<?>, Module>() {
        @Override
        public Module put(Class<?> key, Module value) {
            if (value != null) {
                value.category = resolveCategory(key);
            }
            return super.put(key, value);
        }
    };

    public ModuleManager() {
        initCategories();
    }

    private void initCategories() {
        registerCategory(Module.Category.COMBAT,
                AimAssist.class,
                AutoClicker.class,
                KillAura.class,
                Wtap.class,
                Velocity.class,
                Freeze.class,
                Reach.class,
                TargetStrafe.class,
                NoHitDelay.class,
                AntiFireball.class,
                LagRange.class,
                HitBox.class,
                MoreKB.class,
                Refill.class,
                HitSelect.class
        );

        registerCategory(Module.Category.MOVEMENT,
                AntiAFK.class,
                Fly.class,
                Speed.class,
                LongJump.class,
                Sprint.class,
                SafeWalk.class,
                Jesus.class,
                Blink.class,
                NoFall.class,
                NoSlow.class,
                KeepSprint.class,
                Eagle.class,
                NoJumpDelay.class,
                AntiVoid.class
        );

        registerCategory(Module.Category.RENDER,
                ESP.class,
                Chams.class,
                FullBright.class,
                Tracers.class,
                NameTags.class,
                Xray.class,
                ItemPhysics.class,
                TargetHUD.class,
                Indicators.class,
                BedESP.class,
                ItemESP.class,
                ViewClip.class,
                NoHurtCam.class,
                HUD.class,
                GuiModule.class,
                ChestESP.class,
                Trajectories.class,
                Radar.class
        );

        registerCategory(Module.Category.PLAYER,
                AutoHeal.class,
                AutoTool.class,
                ChestStealer.class,
                InvManager.class,
                InvWalk.class,
                Scaffold.class,
                AutoBlockIn.class,
                SpeedMine.class,
                FastPlace.class,
                GhostHand.class,
                MCF.class,
                AntiDebuff.class
        );

        registerCategory(Module.Category.MISC,
                Spammer.class,
                BedNuker.class,
                BedTracker.class,
                LightningTracker.class,
                NoRotate.class,
                NickHider.class,
                AntiObbyTrap.class,
                AntiObfuscate.class,
                AutoAnduril.class,
                InventoryClicker.class,
                ExploitFixer.class
        );
    }

    private void registerCategory(Module.Category category, Class<?>... moduleClasses) {
        for (Class<?> moduleClass : moduleClasses) {
            this.categoryByClass.put(moduleClass, category);
        }
    }

    private Module.Category resolveCategory(Class<?> moduleClass) {
        Module.Category category = this.categoryByClass.get(moduleClass);
        if (category == null) {
            throw new IllegalStateException("Missing category for module: " + moduleClass.getName());
        }
        return category;
    }

    public Module getModule(String string) {
        return this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : this.modules.values()) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public void playSound() {
        this.sound = true;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if (module instanceof GuiModule) {
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", Myau.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sound) {
                this.sound = false;
                SoundUtil.playSound("random.click");
            }
        }
    }
}
