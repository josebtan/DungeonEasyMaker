package net.example.dem.loot;

public class LootEntry {

    private final int weight;
    private final String command;

    public LootEntry(int weight, String command) {
        this.weight = weight;
        this.command = command;
    }

    public int getWeight() {
        return weight;
    }

    public String getCommand() {
        return command;
    }
}
