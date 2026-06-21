package main.java.esco.events;

import arc.Events;
import arc.util.Timer;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.PlayerStatus;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.game.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import main.java.esco.commands.ClientCommands;
import static main.java.esco.PVars.*;
import static main.java.esco.database.DatabaseConnector.*;
import static main.java.esco.discord.Bot.sendServerMessage;

public class MapEvents {
    public static void load() {
        Events.on(EventType.ConfigEvent.class, e -> {
            if ((e.value instanceof String) && (e.value.toString().contains("CLaJ") ||
                    Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b(?::\\d{1,5})?").matcher(e.value.toString()).find())) {

                banPlayerPerm(getPlayerId(e.player), "AutoBan, реклама сторонних проектов (" + e.value + ")", -3, "*", "AutoBan");
                if (e.player.con.isConnected()) e.player.kick("Реклама сторонних проектов");
            }
        });
        Events.on(EventType.WorldLoadEvent.class, event -> {
            startTime = Instant.now();
            Timer.schedule(() -> {
                createMapOrDoNothing(Vars.state.map.name(), Vars.state.map.file.nameWithoutExtension());
            }, 1);
            sendServerMessage("Map loaded!");
        });
        Events.on(EventType.WaveEvent.class, e -> {
            List<String> uuidsToUpdate = new ArrayList<>();
            for (Player player : Groups.player) {
                PlayerStatus playerStatus = PlayerStatus.get(player);
                if (playerStatus.frozen || playerStatus.afk)
                    return;

                uuidsToUpdate.add(player.uuid());
                int expChange = Gamemode.getGamemode().waveCost;
                playerStatus.addExperience(expChange);
            }
            if (!uuidsToUpdate.isEmpty()) {
                batchIncrementWaves(uuidsToUpdate);
            }
        });
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if (e.unit == null || !e.unit.isPlayer())
                return;
            if (PlayerStatus.get(e.unit.getPlayer()).frozen)
                return;

            if (e.breaking)
                incrementBreak(e.unit.getPlayer());
            else
                incrementPlace(e.unit.getPlayer());
        });
        Events.on(EventType.GameOverEvent.class, e -> {
            PlayerStatus.each(p -> {
                p.votedSkipMap = false;
                p.votedNewWave = false;
                p.votedMap = null;
            });
            if (!e.winner.equals(Team.derelict)) {
                for (Player player : Groups.player) {
                    PlayerStatus playerStatus = PlayerStatus.get(player);
                    if(playerStatus.afk)
                        continue;
                    int expChange;
                    if (player.team().equals(e.winner)) {
                        incrementWins(player.uuid());
                        expChange = Gamemode.getGamemode().winCost;
                    } else {
                        incrementLoses(player.uuid());
                        expChange = Gamemode.getGamemode().loseCost;
                    }
                    playerStatus.addExperience(expChange);
                }
            }
            updateMapStatistic(Vars.state.map.name(), e.winner.equals(Vars.state.rules.defaultTeam), e.winner.equals(Team.derelict), Vars.state.wave, startTime, Duration.between(startTime, Instant.now()));

        });

    }
}
