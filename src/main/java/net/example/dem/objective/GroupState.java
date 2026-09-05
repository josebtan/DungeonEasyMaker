package net.example.dem.objective;

import java.util.List;

public class GroupState {

    private int remaining;
    private final List<String> onCompleteCommands;

    public GroupState(int remaining, List<String> onCompleteCommands) {
        this.remaining = remaining;
        this.onCompleteCommands = onCompleteCommands;
    }

    public int decrementAndGet() {
        remaining--;
        return remaining;
    }

    public int getRemaining() {
        return remaining;
    }

    public List<String> getOnCompleteCommands() {
        return onCompleteCommands;
    }
}
