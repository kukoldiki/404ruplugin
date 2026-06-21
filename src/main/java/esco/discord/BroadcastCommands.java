package main.java.esco.discord;

import arc.util.Strings;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.Utils;
import net.dv8tion.jda.api.entities.Message;

import java.time.Duration;
import java.time.Instant;

import static main.java.esco.PVars.*;
import static main.java.esco.discord.CommandHandler.broadcastCommands;
import static main.java.esco.discord.CommandHandler.registerBroadcastCommand;
import static main.java.esco.utils.Utils.formatTime;

public class BroadcastCommands {
    public static void load() {
        registerBroadcastCommand("help", "See help message", (e, a)->{
            Message message = e.getMessage();

            StringBuilder s = new StringBuilder();

            broadcastCommands.each(c->s.append("- ").append(c.name).append(" - ").append(c.desc).append("\n"));

            message.reply(s.toString()).queue();
        });

        registerBroadcastCommand("ver", "Get plugin versio", (e, a)->{
            e.getMessage().reply(Strings.format("@: @ (current @ latest @) running for @",
                    Gamemode.getGamemode().toString(),
                    pluginVersion.equals(Utils.sha256(mod.file)) ? ":green_square:" : ":orange_square:",
                    pluginVersion.substring(0, 6),
                    Utils.sha256(mod.file).substring(0, 6),
                    formatTime(Duration.between(serverStart, Instant.now()).getSeconds()))).queue();
        });
    }
}