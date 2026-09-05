package net.example.dem.util;

public class PlaceholderContext {

    private final String player;
    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public PlaceholderContext(String player, String world, int x, int y, int z) {
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static PlaceholderContext ofPlayerOnly(String player) {
        return new PlaceholderContext(player, "", 0, 0, 0);
    }

    public String apply(String command) {
        String result = command;
        if (player != null) {
            result = result.replace("[player]", player)
                    .replace("[playerName]", player)
                    .replace("%player%", player);
        }
        if (world != null && !world.isEmpty()) {
            result = result.replace("[world]", world);
        }
        result = result.replace("[x]", String.valueOf(x))
                .replace("[y]", String.valueOf(y))
                .replace("[z]", String.valueOf(z));
        return result;
    }
}
