package myau.module;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.modules.ClientSetting;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.util.ChatUtil;
import myau.util.SoundUtil;

import java.util.LinkedHashMap;

public class ModuleManager {
    private boolean sound = false;
    private boolean soundEnable = true;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();

    public Module getModule(String string) {
        return this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz) {
        return this.modules.get(clazz);
    }

    public void playSound(boolean enable) {
        this.sound = true;
        this.soundEnable = enable;
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
                ClientSetting clientSetting = (ClientSetting) this.modules.get(ClientSetting.class);
                if (clientSetting != null) {
                    int mode = clientSetting.soundMode.getValue();
                    if (mode == 0) {
                        return;
                    } else if (mode == 1) {
                        SoundUtil.playSound("random.click");
                    } else if (mode == 2) {
                        SoundUtil.playSound(this.soundEnable ? "myau:augustus_enable" : "myau:augustus_disable");
                    } else if (mode == 3) {
                        SoundUtil.playSound(this.soundEnable ? "myau:fdp_enable" : "myau:fdp_disable");
                    }
                } else {
                    SoundUtil.playSound("random.click");
                }
            }
        }
    }
}