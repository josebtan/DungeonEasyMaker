package net.example.dem.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LootTable {

    private final String name;
    private final List<LootEntry> entries = new ArrayList<>();

    public LootTable(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public void addEntry(int weight, String command) {
        entries.add(new LootEntry(weight, command));
    }

    public void removeEntry(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
        }
    }

    /**
     * Elige una entrada al azar, respetando los pesos relativos entre ellas.
     * Entre mas alto el peso, mas probable que salga esa recompensa.
     */
    public LootEntry roll() {
        int totalWeight = entries.stream().mapToInt(LootEntry::getWeight).sum();
        if (totalWeight <= 0 || entries.isEmpty()) {
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (LootEntry entry : entries) {
            cumulative += entry.getWeight();
            if (roll < cumulative) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
