package net.example.dem.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Ejecuta listas de comandos definidas por el usuario, soportando:
 *  - Placeholders: [player], [playerName], %player%, [world], [x], [y], [z]
 *  - Delay opcional por comando, con el formato:  delay:<segundos>|<comando>
 *    Ejemplo: "delay:2.5|broadcast &6¡Cuidado!"
 *
 * El delay es SIEMPRE relativo al momento en que se ejecuta runAll (no se acumula
 * entre comandos), así que "delay:5|comandoA" y "delay:5|comandoB" corren juntos,
 * 5 segundos después de que se disparó el evento.
 */
public class CommandRunner {

    private static final String DELAY_PREFIX = "delay:";

    public static void runAll(Plugin plugin, List<String> rawCommands, PlaceholderContext context) {
        for (String raw : rawCommands) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            long delayTicks = 0;
            String commandPart = trimmed;

            if (trimmed.toLowerCase().startsWith(DELAY_PREFIX)) {
                int pipeIndex = trimmed.indexOf('|');
                if (pipeIndex > DELAY_PREFIX.length()) {
                    String delayValue = trimmed.substring(DELAY_PREFIX.length(), pipeIndex).trim();
                    try {
                        double seconds = Double.parseDouble(delayValue);
                        delayTicks = Math.round(seconds * 20L);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Delay inválido en comando: '" + trimmed + "' (se ejecuta sin delay)");
                    }
                    commandPart = trimmed.substring(pipeIndex + 1).trim();
                }
            }

            String finalCommand = context != null ? context.apply(commandPart) : commandPart;

            if (delayTicks <= 0) {
                execute(finalCommand);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> execute(finalCommand), delayTicks);
            }
        }
    }

    private static void execute(String command) {
        if (command.startsWith("broadcast ")) {
            String message = command.substring("broadcast ".length());
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
