package main.java.esco.commands;

import arc.Events;
import arc.Core;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.Threads;
import arc.util.Timer;
import main.java.esco.AI.loveAI;
import main.java.esco.PVars;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.bundle.Bundle;
import main.java.esco.commandRegister.CommandRegister;
import main.java.esco.menus.ScrollableMenu;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.PlayerStatus;
import main.java.esco.utils.VotekickSession;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.net.Administration.PlayerInfo;
import mindustry.type.UnitType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static main.java.esco.PVars.*;
import static main.java.esco.database.DatabaseConnector.*;
import static main.java.esco.discord.Bot.sendAlertMessage;
import static main.java.esco.utils.Utils.*;

public class ClientCommands {
    public ClientCommands(CommandHandler handler) {
        CommandRegister customHandler = new CommandRegister(handler);
        chandler = customHandler;

        customHandler.addCommand("winwave", "<wave>", AccessLevel.moderator, (args, player) -> {
            try {
                int wave = Integer.parseInt(args[0]);
                if(wave <= 0) {
                    player.sendMessage("[green]Incorrect wave number");
                    return;
                }
                Vars.state.rules.winWave = wave;
                player.sendMessage("[green]OK");
                Call.setRules(Vars.state.rules);
            } catch (NumberFormatException e) {
                player.sendMessage("[scarlet]Amount must be positive integer!");
            }
        });

        customHandler.addCommand("rules", "", (args, player) -> {
            Call.infoMessage(player.con, Bundle.get("esco.rules", player.locale));
        });

        customHandler.addCommand("discord", "", (args, player) -> {
            Call.openURI(player.con, discordLink);
        });

        customHandler.addCommand("rtv", "[map/wave]", (args, player) -> {
            if (Gamemode.getGamemode().toString().contains("hub")) {
                player.sendMessage(Bundle.get("esco.commands.rtv.hub", player.locale));
                return;
            }
            int requiredVotes = Math.max(1, (int) Math.ceil(PlayerStatus.getNotAfk().size * 0.6));
            PlayerStatus ps = PlayerStatus.get(player);
            if (args.length == 0 || "map".equals(args[0])) {
                if (!ps.votedSkipMap) {
                    ps.votedSkipMap = true;
                    Call.sendMessage(player.coloredName() + " [stat]Voted to skip map! votes " + PlayerStatus.getVotedSkipMap().size + "/" + requiredVotes);
                } else {
                    player.sendMessage(Bundle.get("esco.alreadyVoted", player.locale));
                }
                if (PlayerStatus.getVotedSkipMap().size >= requiredVotes) {
                    Events.fire(new GameOverEvent(Team.derelict));
                    PlayerStatus.each(p -> p.votedSkipMap = false);
                }
            } else if("wave".equals(args[0])) {
                if (Groups.unit.count(u -> !u.team.equals(Vars.state.rules.defaultTeam)) > 20) {
                    player.sendMessage(Bundle.get("esco.commands.vnw.manyEnemies", player.locale));
                    return;
                }
                if (!ps.votedNewWave) {
                    ps.votedNewWave = true;
                    Call.sendMessage(player.coloredName() + " [stat]Voted to skip wave! Votes " + PlayerStatus.getVotedNewWave().size + "/" + requiredVotes);
                } else {
                    player.sendMessage(Bundle.get("esco.alreadyVoted", player.locale));
                }

                if (PlayerStatus.getVotedNewWave().size == requiredVotes) {
                    Call.sendMessage("[green]Skipping wave!");
                    PlayerStatus.each(p -> p.votedNewWave = false);
                    Core.app.post(Vars.logic::runWave);
                }
            } else {
                player.sendMessage("[scarlet]Incorrect rtv type! \"wave\" or \"map\" expected");
            }
        });

        customHandler.addCommand("wnv", "", (args, player) -> {
            player.sendMessage("[scarlet]Use /rtv wave");
        });

        customHandler.addCommand("nextmap", "[mapid]", (args, player) -> {
            if(args.length == 0) {
                ObjectIntMap<Map> votesMap = new ObjectIntMap<>();
                PlayerStatus.each(ps -> {
                    if(ps.votedMap == null)
                        return;
                    votesMap.put(ps.votedMap, votesMap.get(ps.votedMap, 0) + 1);
                });
                StringBuilder output = new StringBuilder("Votes: ");
                Seq<ObjectIntMap.Entry<Map>> votes = votesMap.entries().toArray();
                votes.sort(e -> e.value);
                votes.each(e -> output.append(Strings.format("[white]@ votes for @\n", e.value, e.key.name())));
                Call.infoMessage(player.con, output.toString());
                return;
            }
            if(!Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]Map id must be integer");
                return;
            }
            getMap(Integer.parseInt(args[0])).ifPresentOrElse(maprecord -> {
                Map map = Vars.maps.byName(maprecord.getName());
                if(map == null) {
                    player.sendMessage("[scarlet]Map not found on server");
                    return;
                }
                PlayerStatus.get(player).votedMap = map;
                Call.sendChatMessage(Strings.format("@ [stat] has voted for map @ [stat](id @)", player.name, map.name(), maprecord.getId()));
            }, () -> player.sendMessage("[scarlet]Map id not found"));
        });

        customHandler.addCommand("link", "", (args, player) -> {
            long dsid = getPlayerDiscord(player);
            if (dsid != -1L && dsid != 0L) {
                player.sendMessage("[scarlet]Already linked!");
                return;
            }
            String code = generateAuthCode(6);
            Call.infoMessage(player.con, "[tan]Join our Discord and in any channel type \"" + botPrefix + "link " + code + "\" to link your Discord!");
            verifycodes.put(code, player.uuid());
        });

        customHandler.addCommand("hub", "[server]", (args, player) -> {
            String target = "hub";
            Gamemode targetMode;
            if (args.length > 0)
                target = args[0];
            if (Gamemode.getGamemode().getVersion() == 8)
                targetMode = Gamemode.getGamemode(target + "8");
            else
                targetMode = Gamemode.getGamemode(target);
            if (targetMode == Gamemode.unknown) {
                player.sendMessage(Bundle.get("esco.commands.hub.wrongServer", player.locale));
                return;
            }
            if (Gamemode.getGamemode() == targetMode) {
                player.sendMessage(Bundle.get("esco.commands.hub.already", player.locale));
                return;
            }

            player.sendMessage(Bundle.get("esco.commands.hub.ok", player.locale));
            //Call.connect(player.con, "121.127.37.17", 6571);
            //Call.connect(player.con, "95.215.56.128", 6571);
            //Call.connect(player.con, "194.164.245.98", 6567);
            Call.connect(player.con, serverIP, targetMode.port);
        });

        customHandler.addCommand("stats", "[plid/pluuid]", (args, player) -> {
            PlayerData dat = null;
            if (args.length > 0) {
                try {
                    dat = getPlayerData(Integer.parseInt(args[0])).orElse(null);
                } catch (NumberFormatException e) {
                    dat = getPlayerData(args[0]).orElse(null);
                }
            } else {
                dat = getPlayerData(player.uuid()).orElse(null);
            }
            if (dat != null) {
                Call.infoMessage(player.con, "[tan]Level:[blue] " + dat.getLevel() +
                        "\n[tan]Total exp:[blue] " + dat.experience() +
                        "\n[tan]Blocks placed:[blue] " + dat.blocksPlaced() +
                        "\n[tan]Blocks broken:[blue] " + dat.blocksBroken() +
                        "\n[tan]Total playtime:[blue] " + Math.floor(dat.playtime() / 3600) + "h" +
                        "\n[tan]Waves survived:[blue] " + dat.wavesSurvived() +
                        "\n[tan]Loses:[blue]: " + dat.loses() +
                        "\n[tan]Wins:[blue] " + dat.wins() +
                        "\n[tan]Next level: [blue] " + (dat.getLevel() + 1) +
                        "\n[tan]Exp needed to next level[blue] " + dat.getExperienceForLevel(dat.getLevel() + 1) +
                        "\n[tan]Exp remaining[blue] " + (dat.getExperienceForLevel(dat.getLevel() + 1) - dat.experience()) +
                        "\n[tan]ID:[blue] " + dat.id());
            } else {
                player.sendMessage(Bundle.get("esco.commands.playerNotFound", player.locale));
            }
        });
        customHandler.addCommand("top", "[experience/playtime]", (args, player) -> {
            StringBuilder resultMenu = new StringBuilder();

            String key;
            if(args.length == 0 || 'e' == args[0].charAt(0))
                key = "experience";
            else
                key = "playtime";
            List<PlayerData> topPlayers = getTopPlayers(key);
            for (PlayerData pl : topPlayers)
                resultMenu.append(Strings.format("@ - @exp - @h", pl.getDisplayName(), pl.experience(), Math.floor(pl.playtime() / 3600)));

            Call.infoMessage(player.con, resultMenu.toString());
        });

        customHandler.addCommand("history", "", (args, player) -> {
            PlayerStatus playerStatus = PlayerStatus.get(player);
            if (playerStatus.historyEnabled) {
                playerStatus.historyEnabled = false;
                Call.hideHudText(player.con);
                player.sendMessage(Bundle.get("esco.commands.history.disabled", player.locale));
            } else {
                playerStatus.historyEnabled = true;
                player.sendMessage(Bundle.get("esco.commands.history.enabled", player.locale));
            }
        });

        customHandler.addCommand("time", "", (args, player) -> {
            Instant currentTime = Instant.now();
            Duration mapDuration = Duration.between(startTime, currentTime);
            Duration duration = Duration.between(serverStart, currentTime);
            player.sendMessage(Strings.format("Map running time: @", formatTime(mapDuration.getSeconds())));
            player.sendMessage(Strings.format("Server running time: @", formatTime(duration.getSeconds())));
        });

        customHandler.addCommand("name", "[new-nickname...]", (args, player) -> {
            PlayerStatus playerStatus = PlayerStatus.get(player);

            if (playerStatus.experience < 600) {
                player.sendMessage(Bundle.get("esco.commands.name.level", player.locale));
                return;
            }
            if (args.length == 0) {
                updateCustomName(player, "");
                player.sendMessage(Bundle.get("esco.commands.name.reset", player.locale));
                return;
            }
            if (Strings.stripColors(args[0]).length() > 40) {
                player.sendMessage(Bundle.get("esco.commands.name.toolong", player.locale));
                return;
            }
            playerStatus.customName = args[0];
            playerStatus.updateName();
            updateCustomName(player, args[0]);
            player.sendMessage("[green]Successfully set your name to " + args[0]);

        });

        customHandler.addCommand("protection", "", (args, player) -> {
            getPlayerData(player).ifPresent(pdata -> {
                updateProtectionState(player, !pdata.protection());
                player.sendMessage("[gold]Protection is " + (!pdata.protection() ? "[green]Enabled[]." : "[red]Disabled[]."));
            });
        });

        customHandler.addCommand("custom", "", (args, player) -> {
            PlayerStatus playerStatus = PlayerStatus.get(player);
            String customLevel = playerStatus.customLevel;
            if (customLevel == null || customLevel.isEmpty()) {
                player.sendMessage(Bundle.get("esco.commands.custom.noName", player.locale));
                return;
            }
            playerStatus.useCustomLevel = !playerStatus.useCustomLevel;
            updateCustomLevelState(player, playerStatus.useCustomLevel);
            player.sendMessage("[green]Now using " + (playerStatus.useCustomLevel ? "custom level." : "level by experience."));

        });

        customHandler.addCommand("elite", "", (args, player) -> {
            getPlayerData(player).ifPresent(pdata -> {
                if(pdata.experience() < 300) {
                    player.sendMessage("[red]At least 300 exp is required!");
                    return;
                }
                boolean newState = !pdata.elite();
                boolean succsess = updateEliteState(player, newState);
                if(!succsess) {
                    player.sendMessage("Something went wrong. Please contact admins. (/discord)");
                    sendAlertMessage(Strings.format("Something went wrong, can't apply elite status to player @", pdata.id()));
                    return;
                }
                player.sendMessage("[green]Now using " + (newState ? "[gold]elite" : "[green]basic") + " [green]server");
                if(!Gamemode.getGamemode().separated)
                    return;
                Call.connect(player.con, serverIP, Gamemode.getGamemode().getOpposite().port);
            });
        });

        customHandler.addCommand("votekick", "[player] [reason...]", (args, player) -> {
            if (PlayerStatus.get(player).experience < 50) {
                player.sendMessage("[scarlet]At least 50 experience is required to begin a votekick session.");
                return;
            }
            if (VotekickSession.currentSession != null) {
                player.sendMessage("[scarlet]A vote is already in progress.");
                return;
            }
            if (args.length == 0) {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");

                Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
                });
                player.sendMessage(builder.toString());
                return;
            }
            if (args.length == 1) {
                player.sendMessage("[orange]You need a valid reason to kick the player. Add a reason after the player name.");
                return;
            }
            Player found;
            if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                int id = Strings.parseInt(args[0].substring(1));
                found = Groups.player.find(p -> p.id() == id);
            } else {
                found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
            }
            if (found == null) {
                player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                return;
            }
            if (found == player) {
                player.sendMessage("[scarlet]You can't vote to kick yourself.");
                return;
            }
            if (getAccessLevel(found).hasSufficientLevel(AccessLevel.moderator)) {
                player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                return;
            }
            if (found.team() != player.team()) {
                player.sendMessage("[scarlet]Only players on your team can be kicked.");
                return;
            }
            VotekickSession session = new VotekickSession(found, player, args[1]);
            session.vote(player, "y");
        });

        customHandler.addCommand("vote", "<y/n>", (args, player) -> {
            if (VotekickSession.currentSession == null) {
                player.sendMessage("[scarlet]Nobody is being voted on.");
                return;
            }
            VotekickSession.currentSession.vote(player, args[0]);
        });

        customHandler.addCommand("t", "<message...>", (args, player) -> {
            String message = Vars.netServer.admins.filterMessage(player, args[0]);
            if (message != null) {
                String raw = "[#" + player.team().color.toString() + "]<T> " + Vars.netServer.chatFormatter.format(player, message);
                Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
                executor.submit(() -> {
                    logChatMessage(player, message, "team");
                });
            }
        });

        customHandler.addCommand("vanish", "<t/f>", AccessLevel.moderator, (args, player) -> {
            int newState = switch (args[0]) {
                case "t", "1", "y", "yes", "true", "+", "ye", "yea", "yeah", "on", "yep" -> 1;
                case "f", "0", "n", "no", "false", "-", "nope", "off" -> -1;
                default -> 0;
            };
            if (newState == 0) {
                player.sendMessage("[scarlet]Wrong option! Legal options: t/f, true/false, 1/0, yes/no, y/n (hidden/shown))");
                return;
            }
            updateAdminVanishState(player, newState > 0);
            player.admin(newState <= 0);
        });

        customHandler.addCommand("a", "<message...>", AccessLevel.moderator, (args, player) -> {
            executor.submit(() -> {
                String raw = "[#ff4000]<A> " + Vars.netServer.chatFormatter.format(player, args[0]);
                for (Player p : Groups.player) {
                    if(getAccessLevel(p).hasSufficientLevel(AccessLevel.moderator)) {
                        p.sendMessage(raw);
                    }
                }
                logChatMessage(player, args[0], "admin");
            });
        });

        customHandler.addCommand("killall", "", AccessLevel.admin, (args, player) -> {
            Groups.unit.each(u -> {
                if (u.getPlayer() == null) {
                    u.kill();
                }
            });
            sendAlertMessage("Admin " + getPlayerId(player) + " action **kill all units** on " + Gamemode.getGamemode().toString());
        });
        /**Runwave crash server.*/
        if (!Gamemode.getGamemode().toString().contains("v8")) {
            customHandler.addCommand("runwave", "[amount]", AccessLevel.moderator, (args, player) -> {
                if (args.length != 1) {
                    Vars.logic.runWave();
                    player.sendMessage("[green]Wave spawned.");
                } else {
                    try {
                        int amount = Integer.parseInt(args[0]);
                        if (amount > 10) {
                            player.sendMessage("[scarlet]Too many waves!");
                            return;
                        }
                        for (int i = 0; i < amount; i++)
                            Timer.schedule(() -> {
                              Core.app.post(() -> Vars.logic.runWave());
                            }, i * 1);
                        player.sendMessage("[green] " + amount + " waves spawned.");
                    } catch (NumberFormatException e) {
                        player.sendMessage("[scarlet]Amount must be positive integer!");
                        return;
                    }
                }

                sendAlertMessage("Admin " + getPlayerId(player) + " action **skip wave** on " + Gamemode.getGamemode().toString());
            });
        }

        customHandler.addCommand("kickvpn", "", AccessLevel.admin, (args, player) -> {

            PlayerStatus.getVpnUsers().each(p -> p.kick("For now, you cant play from vpn."));
            sendAlertMessage("Admin " + getPlayerId(player) + " action **kick all vpn/proxy users** on " + Gamemode.getGamemode().toString());
        });

        customHandler.addCommand("pardon", "<ID>", AccessLevel.admin, (args, player) -> {
            PlayerInfo info = Vars.netServer.admins.getInfoOptional(args[0]);

            if (info != null) {
                info.lastKicked = 0;
                Vars.netServer.admins.kickedIPs.remove(info.lastIP);
                player.sendMessage("Pardoned player: " + info.plainLastName());
            } else {
                player.sendMessage("That ID can't be found.");
            }
        });

        customHandler.addCommand("artv", "", AccessLevel.moderator, (args, player) -> {
            Events.fire(new GameOverEvent(Team.derelict));
        });
        customHandler.addCommand("info", "<id/uuid>", AccessLevel.moderator, (args, player) -> {
            Optional<PlayerData> dbdata;
            if (Strings.canParseInt(args[0])) {
                dbdata = getPlayerData(Integer.parseInt(args[0]));
            } else {
                dbdata = getPlayerData(args[0]);
            }

            if (!dbdata.isPresent()) {
                player.sendMessage("[scarlet]Player not found.");
                return;
            }

            PlayerData pdata = dbdata.get();

            AccessLevel adminLevel = getAccessLevel(player);
            AccessLevel targetLevel = getAccessLevel(pdata.uuid());

            if(!adminLevel.hasSufficientLevel(targetLevel)) {
                player.sendMessage("[scarlet]You cant view info of this player!");
                return;
            }

            StringBuilder info = new StringBuilder();
            info.append("ID: ").append(pdata.id());
            if(adminLevel.hasSufficientLevel(AccessLevel.hadmin))
                info.append("\nUUID: " + pdata.uuid());
            info.append("\nIP: " + pdata.lastIP());
            info.append("\nName: " + pdata.lastName());
            if (pdata.customName() != null)
                info.append("\n[white]Custom name: " + pdata.customName());
            if (pdata.customLevel() != null)
                info.append("\n[white]Custom level: " + pdata.customLevel());
            if (pdata.rankColor() != null)
                info.append("\n[white]Rank color: " + pdata.rankColor());
            info.append("\n[white]Experience: " + pdata.experience());
            info.append("\nWins: " + pdata.wins());
            info.append("\nLoses: " + pdata.loses());
            info.append("\nPlaced: " + pdata.blocksPlaced());
            info.append("\nBroken: " + pdata.blocksBroken());
            info.append("\nWaves survived: " + pdata.wavesSurvived());
            if (pdata.firstJoinTime() != null)
                info.append("\nFirst join: " + pdata.firstJoinTime());
            info.append("\nMobile: " + (pdata.isMobile() ? "Yes" : "No"));
            info.append("\nLocale: " + pdata.locale());
            info.append("\nColor: " + pdata.color().toString());
            Call.infoMessage(player.con, info.toString());
        });

        customHandler.addCommand("s", "<text...>", AccessLevel.hadmin, (args, player) -> {
            Call.sendMessage("[scarlet][Server]:[white] " + args[0]);
        });

        // idk
        customHandler.addCommand("manifold", "", AccessLevel.hadmin, (args, player) -> {
            @Nullable Unit unit; // why
            Team team = player.team();
            UnitType unitType = UnitTypes.manifold;
            if (!Vars.net.client()) {
                unit = unitType.create(team);
                if (unit instanceof BuildingTetherc bt) {
                    bt.building(team.core());
                }
                unit.set(player.x, player.y);
                unit.rotation = 90f;
                unit.add();
                // Call.unitTetherBlockSpawned(tile, unit.id);
                unit.controller(new loveAI(player));
                player.sendMessage("[green]Spawned at [white]" + Math.floor(player.x / 8) + " " + Math.floor(player.y / 8) + "[lightgray](" + Math.floor(player.x) + "," + Math.floor(player.y));
            } else player.sendMessage("[scarlet]How?");
        });

        customHandler.addCommand("ban", "<id> <time> <server> <reason...>", AccessLevel.moderator, (args, player) -> {
            getPlayerData(player).ifPresent(pdata -> {
                String timeArg = args[1];
                String server = args[2];
                if (server.equalsIgnoreCase("here"))
                    server = Gamemode.getGamemode().toString();
                if (server.equalsIgnoreCase("all"))
                    server = "*";
                if (Gamemode.getGamemode(server) == Gamemode.unknown && !server.equals("*")) {
                    player.sendMessage("[scarlet]Wrong server!");
                    return;
                }
                int targetId = 0;
                String targetUUID = args[0];
                boolean useDBID = Strings.canParseInt(args[0]);
                if (useDBID) {
                    targetId = Integer.parseInt(args[0]);
                    if (!getPlayerData(targetId).isPresent()) {
                        player.sendMessage("[scarlet]Wrong player!");
                        return;
                    }
                } else {
                    if (!getPlayerData(targetUUID).isPresent()) {
                        player.sendMessage("[scarlet]Wrong player!");
                        return;
                    }
                }
                long banDuration = parseTime(timeArg);
                if (/*!Character.isDigit(timeArg.charAt(0))*/ banDuration == -1) {
                    if (useDBID) {
                        banPlayerPerm(targetId, args[3], pdata.id(), server, "command");
                    } else {
                        banPlayerPerm(targetUUID, args[3], pdata.id(), server, "command");
                    }
                    player.sendMessage("[green]Banned.");
                    return;
                }

                if (useDBID) {
                    banPlayer(targetId, args[3], Instant.now().plus(Duration.ofSeconds(banDuration)), pdata.id(), server, "command");
                } else {
                    banPlayer(targetUUID, args[3], Instant.now().plus(Duration.ofSeconds(banDuration)), pdata.id(), server, "command");
                }
                player.sendMessage("[green]Banned.");
            });
        });

        customHandler.addCommand("freeze", "<id/uuid>", AccessLevel.moderator, (args, player) -> {
            Player target = Groups.player.find(p -> p.uuid().equals(args[0]));
            if (Strings.canParseInt(args[0]))
                target = getPlayerById(Integer.parseInt(args[0]));

            if (target == null) {
                player.sendMessage("[scarlet]No player found");
                return;
            }
            PlayerStatus playerStatus = PlayerStatus.get(target);
            if (playerStatus.frozen) {
                playerStatus.frozen = false;
                target.sendMessage(Bundle.get("esco.admins.unfroozen", target.locale));
                player.sendMessage("[green]Unfreezed.");
            } else {
                playerStatus.frozen = true;
                target.sendMessage(Bundle.get("esco.admins.froozen", target.locale));
                player.sendMessage("[green]Freezed.");
            }
            playerStatus.updateName();
        });

        customHandler.addCommand("maps", "", AccessLevel.player, (args, player) -> {
            int linesPerPage = 20;
            StringBuilder sb = new StringBuilder();
            List<MapRecord> maps = getMapsList(Gamemode.getGamemode().toString());
            ScrollableMenu menu = new ScrollableMenu("Maps");
            int counter = 0;
            for (MapRecord map : maps) {
                sb.append(Strings.format("[white][@]: @\n", map.getId(), map.getName()));
                counter++;
                if (counter >= linesPerPage) {
                    counter = 0;
                    sb.insert(0, "[id]: name\n");
                    menu.addPage(sb.toString());
                    sb.setLength(0);
                }
            }
            if (!sb.isEmpty()) {
                sb.insert(0, "[id]: name\n");
                menu.addPage(sb.toString());
            }

            menu.show(player);
        });

        customHandler.addCommand("mapinfo", "[id]", AccessLevel.player, (args, player) -> {
            Threads.thread(() -> {
                Optional<MapStats> stats;
                if (args.length != 0 && Strings.canParseInt(args[0])) {
                    stats = getMapStats(Integer.parseInt(args[0]));
                } else {
                    stats = getMapStats(Vars.state.map.name());
                }
                if (!stats.isPresent()) {
                    player.sendMessage("[scarlet]Map not found!");
                    return;
                }
                MapStats mapStats = stats.get();
                Call.infoMessage(player.con, Strings.format(
                        "Map @ [white]on @ (ID @)\n" +
                                "File: @.msav\n" +
                                "[blue]skips: []@\n" +
                                "[green]wins: []@\n" +
                                "[red]loses: []@\n" +
                                "Duration (min/avg/max):\n" +
                                "@/@/@\n" +
                                "Wave (min/avg/max): @/@/@",
                        mapStats.getName(),
                        mapStats.getServer(),
                        mapStats.getId(),
                        mapStats.getFileName(),
                        mapStats.getSkips(),
                        mapStats.getWins(),
                        mapStats.getLosses(),
                        formatTime(mapStats.getMinDuration()),
                        formatTime(mapStats.getAvgDuration()),
                        formatTime(mapStats.getMaxDuration()),
                        mapStats.getMinWave(),
                        mapStats.getAvgWave(),
                        mapStats.getMaxWave()
                ));
            });
        });

        customHandler.addCommand("trace", "<query...>", AccessLevel.hadmin, (args, player) -> {
            Threads.thread(() -> {
                player.sendMessage("[stat]Please wait.");
                List<String> infos = searchPlayers(args[0]);
                ScrollableMenu menu = new ScrollableMenu("Trace info for query " + args[0]);
                for (String info : infos) {
                    menu.addPage(info);
                }
                menu.show(player);
            });
        });
    }
}
