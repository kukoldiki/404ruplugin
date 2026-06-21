package main.java.esco.accent;

import arc.struct.Seq;
import main.java.esco.bundle.Bundle;
import mindustry.gen.Player;

import java.util.concurrent.ConcurrentHashMap;

import static main.java.esco.PVars.chandler;

public class AccentSystem {
    private static final Seq<Accent> availableAccents = Seq.with(new OwOAccent(), new GreloAccent());
    private static final ConcurrentHashMap<Player, Accent> enabledAccents = new ConcurrentHashMap<>();
    public static void load() {
        chandler.addCommand("accent", "[accent]", (args, player)->{
            if(args.length == 1) {
                Accent accent = availableAccents.find(a->a.getName().equalsIgnoreCase(args[0]));
                if(accent == null) {
                    player.sendMessage(Bundle.get("esco.commands.accent.notfound", player.locale));
                    return;
                }
                enabledAccents.remove(player);
                enabledAccents.put(player, accent);
                player.sendMessage(Bundle.get("esco.commands.accent.enabled", player.locale));
            } else {
                if(enabledAccents.remove(player) != null)
                    player.sendMessage(Bundle.get("esco.commands.accent.disabled", player.locale));
                StringBuilder result = new StringBuilder();
                for(Accent a : availableAccents)
                    result.append(a.getName()).append("\n");
                player.sendMessage(result.toString());
            }
        });
    }

    public static String apply(Player player, String message) {
        Accent accent = enabledAccents.get(player);
        if(accent != null)
            message = accent.apply(message);
        return message;
    }

    public static void remove(Player p) {
        enabledAccents.remove(p);
    }

    public static String getAccentName(Player p) {
        Accent a = enabledAccents.get(p);
        return a == null ? "default" : a.getName();
    }

    public static void doBadThings() {}
}
