package net.example.dem.objective;

import java.util.HashMap;
import java.util.Map;

public class ObjectiveManager {

    private final Map<String, GroupState> activeGroups = new HashMap<>();

    public Map<String, GroupState> getActiveGroups() {
        return activeGroups;
    }
}
