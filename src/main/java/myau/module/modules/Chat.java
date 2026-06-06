package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;

public class Chat extends Module {
    public static Chat INSTANCE;
    public final BooleanProperty infiniteChat;
    public final BooleanProperty noBackground;

    public Chat() {
        super("Chat", false);
        INSTANCE = this;
        this.infiniteChat = new BooleanProperty("Infinite", true);
        this.noBackground = new BooleanProperty("NoBackground", true);
    }
}