package myau.module.modules;

import myau.module.Module;
import myau.property.properties.ModeProperty;

public class ClientSetting extends Module {
    public final ModeProperty soundMode = new ModeProperty("sound", 1, new String[]{"None", "Vanilla", "Augustus", "FDP"});

    public ClientSetting() {
        super("ClientSetting", false);
    }
}