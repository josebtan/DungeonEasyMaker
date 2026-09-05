package net.example.dem.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlaceholderContext {

    private final String player;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final Player onlinePlayer; // opcional, habilita soporte de PlaceholderAPI

    public PlaceholderContext(String player, String world, int x, int y, int z, Player onlinePlayer) {
        this.player = player;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.onlinePlayer = onlinePlayer;
    }

    public PlaceholderContext(String player, String world, int x, int y, int z) {
        this(player, world, x, y, z, Bukkit.getPlayerExact(player));
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

        result = applyPlaceholderApiIfPresent(result);
        return result;
    }

    private String applyPlaceholderApiIfPresent(String text) {
        if (onlinePlayer == null) {
            return text;
        }
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return text;
        }
        try {
            return PapiHook.apply(onlinePlayer, text);
        } catch (Throwable t) {
            // Si algo sale mal con PAPI, no queremos tumbar el comando completo.
            return text;
        }
    }
}
