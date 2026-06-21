package main.java.esco.events;

import arc.Core;
import arc.Events;
import main.java.esco.PVars;
import main.java.esco.accent.AccentSystem;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.utils.PlayerStatus;
import main.java.esco.utils.Utils;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;

import java.time.Duration;
import java.time.Instant;

import static main.java.esco.PVars.*;
import static main.java.esco.database.DatabaseConnector.handlePlayerLeave;
import static main.java.esco.discord.Bot.sendLeaveMessage;
import static main.java.esco.trails.TrailsHandler.TrailedPlayers;

public class LeaveEvents {
    public static void load() {
        Events.on(EventType.PlayerLeave.class, e -> {
            if(e.player == null)
                return;
            // voteNeed();
            if (verifycodes.containsValue(e.player.uuid()))
                verifycodes.entrySet().removeIf(entry -> entry.getValue().equals(e.player.uuid()));
            TrailedPlayers.remove(e.player);
            SSUsers.remove(e.player.id);
            AccentSystem.remove(e.player);

            handlePlayerLeave(e.player);
            if(PlayerStatus.existsFor(e.player)) {
                sendLeaveMessage(e.player);
                Call.sendMessage("[gray][" + PlayerStatus.get(e.player).id + "][stat]Player " + e.player.coloredName() + " [red]left!");
            }
            PlayerStatus.remove(e.player);
            //Log.info("Player @ leave! [@]", e.player.plainName(), e.player.uuid());
            if((!pluginVersion.equals(Utils.sha256(mod.file)) || Duration.between(serverStart, Instant.now()).toHours() > Core.settings.getInt("autoRestartInterval", 12)) && Groups.player.isEmpty()) {
                PVars.whitelistEnabled = true;
                PVars.shiza.writeChunk();
                DatabaseConnector.shutdown();
                Vars.net.dispose();
                System.exit(0);
            }
        });
    }
}
