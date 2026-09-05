package net.example.dem.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Aislado a propósito en su propia clase: la JVM solo carga (y resuelve las
 * clases de PlaceholderAPI) cuando se invoca un método de ESTA clase. Como
 * solo la llamamos si PlaceholderAPI está instalado y habilitado, el plugin
 * funciona perfectamente sin PAPI presente en el servidor.
 */
public class PapiHook {

    public static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
