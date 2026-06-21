package main.java.esco.events;

import arc.Events;
import arc.util.Strings;
import main.java.esco.utils.PlayerStatus;
import mindustry.game.EventType;

import static main.java.esco.PVars.*;
import static main.java.esco.accent.AccentSystem.getAccentName;
import static main.java.esco.database.DatabaseConnector.logChatMessage;
import static main.java.esco.discord.Bot.sendServerMessage;
import static main.java.esco.utils.Utils.stripFoo;

public class ChatEvents {
    public static void load() {
        Events.on(EventType.PlayerChatEvent.class, e -> {
            PlayerStatus.get(e.player).handleAction();
            if (e.message.isEmpty())
                return;
            if (e.message.startsWith("/"))
                return;
            executor.submit(() -> {
                logChatMessage(e.player, e.message, "global", getAccentName(e.player));
            });

            if (channel != null) {
                sendServerMessage(e.player.plainName() + ": " + Strings.stripColors(stripFoo(e.message)));
            }
        });
    }
}
