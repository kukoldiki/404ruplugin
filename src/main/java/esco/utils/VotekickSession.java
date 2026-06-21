package main.java.esco.utils;

import arc.Events;
import arc.util.Strings;
import arc.util.Timer;
import main.java.esco.menus.MenuBuilder;
import main.java.esco.PVars;
import main.java.esco.accessLevel.AccessLevel;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


import static main.java.esco.database.DatabaseConnector.*;


public class VotekickSession {
    public static VotekickSession currentSession;

    static {
        Events.on(EventType.PlayerLeave.class, e -> {
            if (currentSession == null)
                return;
            currentSession.voted.remove(e.player.uuid());
            if (currentSession.target == e.player)
                banPlayer(currentSession.targetId, "Leave during voting\n" + currentSession.reason, Instant.now().plusSeconds(60 * 60 * 24), currentSession.initiatorId, Gamemode.getGamemode().toString(), "Votekick");
        });
    }

    public final String reason;
    public final Player initiator;
    public final Player target;
    public final Timer.Task task;
    public final Timer.Task menuTask;
    public final int initiatorId;
    public final int targetId;
    public final ConcurrentHashMap<String, Integer> voted = new ConcurrentHashMap<>();

    public VotekickSession(Player target, Player initiator, String reason) {
        this.target = target;
        this.initiator = initiator;
        this.reason = reason;
        this.targetId = getPlayerId(target);
        this.initiatorId = getPlayerId(initiator);
        this.task = Timer.schedule(() -> {
            int votes = getVotes();
            //int banTime = Core.settings.getInt("votekicktime" + votes, 60 * 60);
            int banTime = Math.min((int)Math.pow(votes, 3.2f), 1440) * 60;
            if (banTime > 0 && votes > 1) {
                boolean banSuccess = banPlayerWithVotes(
                    targetId,
                    "Votekick: " + reason,
                    Instant.now().plusSeconds(banTime),
                    initiatorId,
                    Gamemode.getGamemode().toString(),
                    "Votekick",
                    voted
                );

                if (banSuccess) {
                    String time = banTime >= 3600
                        ? banTime / 3600 + " hours"
                        : banTime / 60 + " minutes";

                    Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] @ [orange]will be banned from the server for @.", target.name, time));
                    //sendDiscordMessage(time);
                } else {
                    Call.sendMessage("[scarlet]Failed to process votekick. Please contact admins.");
                }
                target.kick("Votekick");
            } else {
                Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes (@) to kick[orange] @", votes, target.name));
            }
            currentSession = null;
        }, 45);
        this.menuTask = Timer.schedule(() -> {
            if(!target.con.isConnected() && target.con.hasDisconnected)
                return;
            for (Player player : Groups.player) {
                if (voted.containsKey(player.uuid()))
                    continue;
                MenuBuilder menu = new MenuBuilder("Votekick", Strings.format("Vote kick @ [white]for @", target.name, reason));
                menu.add("[blue]unsure", (pl) -> {}).row();
                menu.add("[green]YES", (pl) -> {
                    if (currentSession != null)
                        vote(pl, "y");
                }).row();
                menu.add("[red]NO", (pl) -> {
                    if (currentSession != null)
                        vote(pl, "n");
                });
                if (getAccessLevel(player).hasSufficientLevel(AccessLevel.moderator)) {
                    menu.row();
                    menu.add("[blue]CANCEL", (pl) -> {
                        if (currentSession != null)
                            vote(pl, "c");
                    });
                }
                menu.show(player);
            }
        }, 30);
        Call.sendMessage(Strings.format("[stat]Votekick started. Initiator: @ [stat]Target: @ [stat]Reason: @", initiator.name(), target.name(), reason));
        currentSession = this;
    }

    public void vote(Player player, String vote) {
        if (getAccessLevel(player).hasSufficientLevel(AccessLevel.moderator) && vote.equalsIgnoreCase("c")) {
            Call.sendMessage("[lightgray]Vote canceled by admin.");
            task.cancel();
            menuTask.cancel();
            currentSession = null;
            return;
        }

        if (target == player) {
            player.sendMessage("[scarlet]You can't vote on your own trial.");
            return;
        }

        if (target.team() != player.team()) {
            player.sendMessage("[scarlet]You can't vote for other teams.");
            return;
        }

        int sign = switch (vote.toLowerCase()) {
            case "y", "yes" -> 1;
            case "n", "no" -> -1;
            default -> 0;
        };

        if (sign == 0) {
            player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
            return;
        }
        Optional<PlayerData> dbdata = getPlayerData(player);
        if (dbdata.isEmpty()) {
            player.sendMessage("[scarlet]Something went wrong with your data. Please contact admins.");
            return;
        }
        PlayerData pdata = dbdata.get();
        if (pdata.experience() < 50) {
            player.sendMessage("[scarlet]At least 50 experience is required to vote.");
            return;
        }
        if (!pdata.rankColor().equalsIgnoreCase("white")) {
            sign *= 2;
        }

        int lastVote = voted.getOrDefault(player.uuid(), 0);

        if (lastVote == sign) {
            player.sendMessage("[scarlet]You've already voted. Sit down.");
            return;
        }

        voted.put(player.uuid(), sign);
        Call.sendMessage(Strings.format("[lightgray]@ [lightgray]has voted @[lightgray] to kick @. Vote: @", player.coloredName(), target.coloredName(), sign > 0 ? "[green]yes" : "[red]no", getVotes()));
        Call.sendMessage("[orange]Type /vote <y/n> to agree."); // foo
    }

    public int getVotes() {
        int votes = 0;
        for (int vote : voted.values())
            votes += vote;
        return votes;
    }
}
