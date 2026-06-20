package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;

public class Chat extends Module {
    public static Chat INSTANCE;
    public final BooleanProperty noBackground;

    public Chat() {
        super("Chat", false);
        INSTANCE = this;
        this.noBackground = new BooleanProperty("NoBackground", true);
    }
}