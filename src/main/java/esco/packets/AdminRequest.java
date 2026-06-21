package main.java.esco.packets;

import arc.Events;
import arc.util.Strings;
import main.java.esco.utils.Gamemode;
import main.java.esco.bundle.Bundle;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.utils.PlayerStatus;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import java.lang.reflect.Constructor;


import static arc.util.Log.info;
import static arc.util.Log.warn;
import static main.java.esco.database.DatabaseConnector.getAccessLevel;
import static main.java.esco.database.DatabaseConnector.getPlayerId;
import static main.java.esco.discord.Bot.sendAlertMessage;

public class AdminRequest {
    public static void adminReq(NetConnection con, AdminRequestCallPacket packet) {
        Player player = con.player, other = packet.other;
        AccessLevel playerLevel = getAccessLevel(player);
        if (!playerLevel.hasSufficientLevel(AccessLevel.moderator)) {
            warn("ACCESS DENIED: Player @ / @ attempted to perform admin action '@' on '@' without proper security access.",
                    player.plainName(), player.con == null ? "null" : player.con.address, packet.action.name(), other == null ? null : other.plainName());
            return;
        }

        Events.fire(new EventType.AdminRequestEvent(con.player, packet.other, packet.action));
        switch (packet.action) {
            case ban -> {
                if(getAccessLevel(other).greatherThan(playerLevel)) {
                    player.sendMessage("[scarlet]You can't freeze this player!");
                    return;
                }
                PlayerStatus playerStatus = PlayerStatus.get(other);
                if (playerStatus.frozen) {
                    playerStatus.frozen = false;
                    other.sendMessage(Bundle.get("esco.admins.unfroozen", other.locale));
                    player.sendMessage("[green]Unfreezed.");
                } else {
                    playerStatus.frozen = true;
                    other.sendMessage(Bundle.get("esco.admins.froozen", other.locale));
                    player.sendMessage("[green]Freezed.");
                }
                playerStatus.updateName();
                Call.infoMessage(player.con, Strings.format("[scarlet]Use /ban. Id of player @ [scarlet]is @.", other.coloredName(), getPlayerId(other)));
            }
            case kick -> {
                packet.other.kick(Packets.KickReason.kick);
                info("&lc@ &fi&lk[&lb@&fi&lk]&fb has kicked @ &fi&lk[&lb@&fi&lk]&fb.", player.plainName(), player.uuid(), other.plainName(), other.uuid());
            }
            case wave -> {
                Vars.logic.skipWave();
                Call.sendMessage("[stat]Admin " + player.coloredName() + "\f[stat] skipped wave!");
            }
            case switchTeam -> {
                if (packet.params instanceof Team team) {
                    if (team == Team.derelict) {
                        other.team(team);
                        other.sendMessage("[stat]Ваша команда была изменена на []" + team.coloredName());
                    } else if (Gamemode.getGamemode() == Gamemode.pvp || Gamemode.getGamemode() == Gamemode.sandbox) {
                        other.team(team);
                        other.sendMessage("[stat]Ваша команда была изменена на []" + team.coloredName());
                    } else if (getAccessLevel(player).hasSufficientLevel(AccessLevel.hadmin)) {
                        other.team(team);
                        other.sendMessage("[stat]Ваша команда была изменена на []" + team.coloredName());
                    } else {
                        player.sendMessage("No access.");
                    }
                }
            }
            case trace -> {
                if (other.admin) {
                    warn("ACCESS DENIED: Player @ / @ attempted to perform admin action '@' on '@' without proper security access.",
                            player.plainName(), player.con == null ? "null" : player.con.address, packet.action.name(), other.plainName());
                    sendAlertMessage("Admin " + getPlayerId(player) + " action **tried to use trace on admin** on " + Gamemode.getGamemode().toString());
                    return;
                }
                Administration.PlayerInfo stats = Vars.netServer.admins.getInfo(other.uuid());

                boolean hideInfo = getAccessLevel(other).greatherThan(playerLevel);

                String[] hiddenIpArr = new String[1];
                hiddenIpArr[0] = "ADMIN";
                String visibleUUID = other.uuid();
                if (!playerLevel.hasSufficientLevel(AccessLevel.hadmin))
                    visibleUUID = "" + getPlayerId(other);
                if (hideInfo)
                    visibleUUID = "ADMIN";

                Administration.TraceInfo info = createTraceInfoInstance(
                        hideInfo ? "ADMIN" : other.con.address,
                        visibleUUID,
                        other.locale,
                        packet.other.con.modclient, packet.other.con.mobile,
                        stats.timesJoined, stats.timesKicked,
                        hideInfo ? hiddenIpArr : stats.ips.toArray(String.class),
                        stats.names.toArray(String.class)
                );

                if (player.con != null) {
                    Call.traceInfo(player.con, other, info);
                    warn("Admin @ requested trace info of player @", player.plainName(), other.plainName());
                }
            }
        }
    }
    public static TraceInfo createTraceInfoInstance(
            String ip, String uuid, String locale,
            boolean modded, boolean mobile,
            int timesJoined, int timesKicked,
            String[] ips, String[] names
    ) {
        try {
            // Пытаемся найти конструктор с параметром "locale" (9 аргументов)
            Class<?>[] paramTypesWithLocale = new Class<?>[] {
                    String.class, String.class, String.class, boolean.class, boolean.class,
                    int.class, int.class, String[].class, String[].class
            };
            Constructor<TraceInfo> constructorWithLocale =
                    TraceInfo.class.getDeclaredConstructor(paramTypesWithLocale);
            constructorWithLocale.setAccessible(true); // Обходим модификаторы доступа
            return constructorWithLocale.newInstance(
                    ip, uuid, locale, modded, mobile, timesJoined, timesKicked, ips, names
            );
        } catch (NoSuchMethodException | SecurityException e) {
            try {
                // Если первого конструктора нет — ищем вариант без "locale" (8 аргументов)
                Class<?>[] paramTypesWithoutLocale = new Class<?>[] {
                        String.class, String.class, boolean.class, boolean.class,
                        int.class, int.class, String[].class, String[].class
                };
                Constructor<TraceInfo> constructorWithoutLocale =
                        TraceInfo.class.getDeclaredConstructor(paramTypesWithoutLocale);
                constructorWithoutLocale.setAccessible(true);
                return constructorWithoutLocale.newInstance(
                        ip, uuid, modded, mobile, timesJoined, timesKicked, ips, names
                );
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create TraceInfo instance", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot create instance", e);
        }
    }
}
