package keystrokesmod.script;

import keystrokesmod.Rug;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.Utils;
import org.lwjgl.Sys;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class Manager extends Module {
    private long lastLoad;
    public final String documentationURL = "https://blowsy.gitbook.io/raven";
    public Manager() {
        super("Manager", category.scripts);
        this.registerSetting(new ButtonSetting("Load scripts", () -> {
            if (Rug.scriptManager.compiler == null) {
                Utils.sendMessage("&cCompiler error, JDK not found");
            }
            else {
                final long currentTimeMillis = System.currentTimeMillis();
                if (Utils.getDifference(this.lastLoad, currentTimeMillis) > 1500) {
                    this.lastLoad = currentTimeMillis;
                    Rug.scriptManager.loadScripts();
                    if (Rug.scriptManager.scripts.isEmpty()) {
                        Utils.sendMessage("&7No scripts found.");
                    }
                    else {
                        Utils.sendMessage("&7Loaded &b" + Rug.scriptManager.scripts.size() + " &7script" + ((Rug.scriptManager.scripts.size() == 1) ? "." : "s."));
                    }
                }
                else {
                    Utils.sendMessage("&cYou are on cooldown.");
                }
            }
        }));
        this.registerSetting(new ButtonSetting("Open folder", () -> {
            try {
                Desktop.getDesktop().open(Rug.scriptManager.directory);
            }
            catch (IOException ex) {
                Rug.scriptManager.directory.mkdirs();
                Utils.sendMessage("&cError locating folder, recreated.");
            }
        }));
        this.registerSetting(new ButtonSetting("View documentation", () -> {
            try {
                Desktop.getDesktop().browse(new URI(documentationURL));
            } catch (Throwable t) {
                Sys.openURL(documentationURL);
            }
        }));
        this.canBeEnabled = false;
        this.ignoreOnSave = true;
    }
}
