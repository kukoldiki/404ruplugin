package main.java.esco.events;

import arc.Core;
import arc.Events;
import arc.util.Timer;
import arc.struct.Seq;
import main.java.esco.PVars;
import main.java.esco.accent.AccentSystem;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.PlayerStatus;
import main.java.esco.utils.Utils;
import main.java.esco.utils.VotekickSession;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.ActionType;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.Tile;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import static main.java.esco.PVars.*;
import static main.java.esco.database.DatabaseConnector.banPlayerPerm;
import static main.java.esco.database.DatabaseConnector.getPlayerId;

public class ServerEvents {
    private static final Seq<ActionType> nonAfkEvents = Seq.with(ActionType.control, ActionType.command, ActionType.commandUnits, ActionType.commandBuilding, ActionType.pickupBlock, ActionType.dropPayload);
    public static void load() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Timer.schedule(System::gc, 0, 30);
            Timer.schedule(PlayerStatus::updateAll, 0, 10);
            Timer.schedule(() -> {
                if((!pluginVersion.equals(Utils.sha256(mod.file)) || Duration.between(serverStart, Instant.now()).toHours() > Core.settings.getInt("autoRestartInterval", 12)) && Groups.player.isEmpty()) {
                    PVars.whitelistEnabled = true;
                    PVars.shiza.writeChunk();
                    DatabaseConnector.shutdown();
                    Vars.net.dispose();
                    System.exit(0);
                }
            }, 0, 600);
            Vars.netServer.admins.addActionFilter(playerAction -> {
                Player player = playerAction.player;
                if (!PlayerStatus.existsFor(player))
                    return false;
                PlayerStatus playerStatus = PlayerStatus.get(player);
                if (nonAfkEvents.contains(playerAction.type)) {
                    playerStatus.handleAction();
                }

                if (playerStatus.frozen)
                    return false;

                if (VotekickSession.currentSession != null && VotekickSession.currentSession.target == player)
                    return false;

                if (!player.isAdded()) {
                    return false;
                }

                if (player.admin) {
                    return true;
                }

                if (playerAction.type == Administration.ActionType.placeBlock && playerAction.block.equals(Blocks.thoriumReactor)) {
                    Tile tile = playerAction.tile;
                    if (tile.build != null) return true;
                    if (tile.dst(player.team().data().cores.min(c -> c.dst(tile.x, tile.y))) <= 120) { // 15 * 8 == 120 //todo npe somehow no core
                        Call.label(player.con, "[#ff]You can`t build thorium reactors close to core.", 2f, tile.x * 8, tile.y * 8);
                        return false;
                    }
                }
                if(playerStatus.experience < 100 && !playerStatus.hasLinkedDiscord()) {
                    if (playerAction.type == Administration.ActionType.placeBlock && playerAction.block instanceof LogicDisplay) {
                        Tile tile = playerAction.tile;
                        Call.label(player.con, "[#ff]At least level 1 or linked discord is required to place a display.\nTo link a discord account, type /link in chat.", 2f, tile.x * 8, tile.y * 8);
                        return false;
                    }
                    if ((playerAction.type == ActionType.configure || playerAction.type == ActionType.placeBlock) &&
                            playerAction.block instanceof LogicBlock && Gamemode.getGamemode().toString().startsWith("sand")) {
                        Tile tile = playerAction.tile;
                        Call.label(player.con, "[#ff]At least level 1 or linked discord is required to use a processor on sandbox.\nTo link a discord account, type /link in chat.", 2f, tile.x * 8, tile.y * 8);
                        return false;
                    }
                }
                return true;
            });
            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (message.matches(".*\\b(сперма|сперму|спермы|сперме|спермой|эякулят|эякуляция|спермотозоиды|сперматозоид)\\b.*")) {
                    player.sendMessage("[scarlet]Я те бан вставлю.");
                    return null;
                }
                if (Pattern.matches("[A-Za-z0-9+/]{22}==", message)) {
                    player.sendMessage("UUID leak found. Cant send message.");
                    return null;
                }
                if (message.contains("t.me")) {
                    banPlayerPerm(getPlayerId(player), "AutoBan, реклама сторонних проектов(" + message + ")", -3, "*", "chatFilter");
                    if (player.con.isConnected())
                        player.kick("Реклама сторонних проектов");
                    return null;
                }
                return AccentSystem.apply(player, message);
            });
        });
    }
}
