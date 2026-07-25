package myau.command;

import java.util.ArrayList;
import java.util.List;

public abstract class Command {
    public final ArrayList<String> names;

    public Command(ArrayList<String> arrayList) {
        this.names = arrayList;
    }

    public abstract void runCommand(ArrayList<String> args);

    public List<String> getCompletions(int argIndex, String currentArg) {
        return new ArrayList<>();
    }
}