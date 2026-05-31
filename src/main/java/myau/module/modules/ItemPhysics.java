package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;

public class ItemPhysics extends Module {
    public static FloatProperty weight;
    public static FloatProperty rotationSpeed;
    public static BooleanProperty realisticMode;

    public ItemPhysics() {
        super("ItemPhysics", false);
        weight = new FloatProperty("weight", 1.0F, 0.1F, 5.0F);
        rotationSpeed = new FloatProperty("rotation-speed", 1.0F, 0.1F, 3.0F);
        realisticMode = new BooleanProperty("realistic-mode", false);
    }

    public float getWeight() {
        return weight.getValue();
    }

    public float getRotationSpeed() {
        return rotationSpeed.getValue();
    }

    public boolean getRealistic() {
        return realisticMode.getValue();
    }
}