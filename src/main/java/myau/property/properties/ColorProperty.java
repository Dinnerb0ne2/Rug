package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;

import java.util.function.BooleanSupplier;

public class ColorProperty extends Property<Integer> {
    public ColorProperty(String name, Integer color) {
        this(name, color, null);
    }

    public ColorProperty(String name, Integer color, BooleanSupplier check) {
        super(name, color, rgb -> true, check);
    }

    @Override
    public String getValuePrompt() {
        return "RGBA";
    }

    @Override
    public String formatValue() {
        return String.format("%08X", this.getValue());
    }

    @Override
    public boolean parseString(String string) {
        String clean = string.replace("#", "");
        if (clean.length() == 6) {
            clean = "FF" + clean;
        }
        try {
            long value = Long.parseLong(clean, 16);
            return this.setValue((int) value);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return this.parseString(jsonObject.get(this.getName()).getAsString());
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), String.format("%08X", this.getValue()));
    }
}