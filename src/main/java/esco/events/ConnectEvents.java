package main.java.esco.events;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Threads;
import arc.util.Reflect;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.bundle.Bundle;
import main.java.esco.menus.MenuBuilder;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.PlayerStatus;
import main.java.esco.utils.Utils;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Groups;
import mindustry.net.Administration;

import java.net.InetAddress;

import java.util.Optional;

import static main.java.esco.PVars.*;
import static main.java.esco.database.DatabaseConnector.*;
import static main.java.esco.discord.Bot.sendAlertMessage;
import static main.java.esco.discord.Bot.sendJoinMessage;
import static main.java.esco.utils.Utils.getUDPAddress;
import static main.java.esco.utils.Utils.isProxyOrVPN;
import main.java.esco.ddos.ProtectionLayers;

public class ConnectEvents {
    public static void load() {
        Events.on(EventType.PlayerConnect.class, e -> {
            long start = System.nanoTime();
            Player player = e.player;
            if(ProtectionLayers.apply0(player))
                return;
            if (whitelistEnabled) {
                e.player.con.kick("Whitelist is currently enabled. Please try again later.");
                return;
            }
            if(ddosProtection!=null) {
                if(!ddosProtection.apply(player))
                    return;
            }
            if(ProtectionLayers.apply1(player))
                return;
            if(ProtectionLayers.apply2(player))
                return;
            if (player.con.usid.length() > 12 || player.uuid().length() > 24) {
                player.kick("Something went wrong with your data.\nPlease contact admins.\n" + discordLink, 0);
                return;
            }
            player.name = player.name.replaceAll("https?://\\S+", "<link>");
            if (!verifyUSID(player)) {
                long discordId = getPlayerDiscord(player);
                if(discordId > 1) {
                    int connId = CreateUnverifiedConnection(player).orElse(0);
                    if(connId != 0) {
                        String code = Utils.generateAuthCode(4);
                        usidverifycodes.put(code, connId);
                        player.kick(Strings.format(Bundle.get("esco.protection.verifyrequest", player.locale), botPrefix, code, discordLink), 0);
                        return;
                    }
                }
                //player.kick("This account is protected, and we can't identify you.\nTry to connect from previous device.\nPlease contact us in discord if you think is is mistake.\n" + discordLink, 0);
                player.kick(Strings.format(Bundle.get("esco.protection.wrongusid", player.locale), discordLink), 0);
                sendAlertMessage(Strings.format("USID mismatch on @ from @, uuid @, usid @", Gamemode.getGamemode().toString(), player.con.address, "######" + player.uuid().substring(6), player.usid()));
                return;
            }

            Optional<PlayerData> pdataOpt = getOrCreatePlayer(player);
            if(pdataOpt.isEmpty()) {
                player.kick("Failed to get/create database record. Please try again later.", 0);
                return;
            }
            PlayerData pdata = pdataOpt.get();
            if(pdata.shouldRedirect()) {
                Call.connect(player.con, serverIP, Gamemode.getGamemode().getOpposite().port);
                return;
            }
            if(ProtectionLayers.apply3(player))
                return;
            Optional<BanRecord> banOpt = getBan(player);
            if(banOpt.isPresent()) {
                player.kick(banOpt.get().getFormattedReason(player.locale), 1);
                return;
            }

            if (player.plainName().contains("@")) {
                player.con.kick("No \"@\"", 0);
                return;
            }

            if (player.plainName().equalsIgnoreCase("rog")) {
                String link = "esco.admins.rog";
                String reason = Bundle.get(link, player.locale);
                player.con.kick(reason);
                //sendBotBanEmbedWithReason(player.usid(), player.plainName(), "SERVER", Strings.stripColors(reason), 9999999);
                Vars.netServer.admins.banPlayerID(player.uuid());
                Vars.netServer.admins.banPlayerIP(player.ip());
                return;
            }
            PlayerStatus ps = PlayerStatus.get(player).refresh(pdata);
            if(!ps.hasLinkedDiscord() && ps.experience == 0 && isGraylisted(player)) {
                ps.fromVpn = true;
                ps.frozen = true;
                ps.updateName();
                player.sendMessage("Your IP address is graylisted. Please link discord account (/link) to be able to interact with the world");
            }
            long end = System.nanoTime();
            Log.info(Strings.format("Player joins check done in @ ns", end - start));
        });
        Events.on(EventType.PlayerJoin.class, e -> {
            if(e.player == null)
                return;
            Player player = e.player;
            Call.clientPacketReliable(player.con, "SendMeSubtitle", String.valueOf(player.id));
            Administration.PlayerInfo info = Vars.netServer.admins.getInfo(e.player.uuid());
            executor.submit(() -> {
                try {
                    PlayerStatus playerStatus = PlayerStatus.get(player);
                    if (playerStatus.experience > 0 || playerStatus.hasLinkedDiscord())
                        return;
                    boolean vpnUser = isProxyOrVPN(player.con.address);
                    Log.debug("Player @ vpnncheck @", player.uuid(), vpnUser);
                    if(!vpnUser)
                        return;

                    playerStatus.fromVpn = true;
                    playerStatus.frozen = true;
                    playerStatus.updateName();
                    e.player.sendMessage(Bundle.get("esco.admins.fromvpn", e.player.locale));
                } catch (Exception err) {
                    Log.err(err);
                }
            });
            Optional<AdminRecord> adminRecord = getAdminRecord(player);
            adminRecord.ifPresent(ar -> player.admin(ar.accessLevel().hasSufficientLevel(AccessLevel.moderator) && !ar.getIsHidden()));
            Call.sendMessage(Strings.format("[gray][@][stat]Player @ [green]joined!", getPlayerId(player), player.coloredName()));
            Log.info(Strings.format("Player @ joined! [@]", player.plainName(), player.uuid()));
            if (info.timesJoined < 3) {
                // Call.openURI(e.player.con, discordLink);
                String link = "esco.events.join.menu.description";
                String description = Bundle.get(link, player.locale);
                if (description.equals(link))
                    description = Bundle.get("esco.commands.nodescription", player.locale);

                link = "esco.events.join.menu.title";
                String title = Bundle.get(link, player.locale);


                link = "esco.rules.name";
                String rulesName = Bundle.get(link, player.locale);

                MenuBuilder menu = new MenuBuilder(title, description);
                menu.add(rulesName, pl -> Call.infoMessage(player.con, Bundle.get("esco.rules", pl.locale)))
                        .add("[blue]\uE80DDiscord", pl -> Call.openURI(pl.con, discordLink))
                        .add("-", pl -> {
                        });

                menu.show(player);
            }
            sendJoinMessage(player);
        });
    }
}
