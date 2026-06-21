package main.java.esco.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import arc.Core;
import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.Seq;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.menus.MenuBuilder;
import mindustry.gen.Player;
import mindustry.maps.Map;

public class PlayerStatus {
    private static final ConcurrentHashMap<Player, PlayerStatus> statuses = new ConcurrentHashMap<>();

    public final int id;
    public String verifyCode = "";
    public Instant lastAction;
    public boolean trail = false;
    public boolean fromVpn = false;
    public boolean historyEnabled = false;
    public boolean votedNewWave = false;
    public boolean votedSkipMap = false;
    public boolean afk = false;
    public boolean frozen = false;
    public final AccessLevel accessLevel;
    public final Player player;
    public final String defaultName;
    public String customName = "";
    public String customLevel = "";
    public Boolean useCustomLevel = false;
    public long experience = 0;
    public String rankColor = null;
    public String discordId;
    public Map votedMap;

    private PlayerStatus(Player player) {
        accessLevel = DatabaseConnector.getAccessLevel(player);
        id = DatabaseConnector.getPlayerId(player);
        this.player = player;
        defaultName = player.name;
        lastAction = Instant.now();
    }

    public static PlayerStatus get(Player player) {
        return statuses.computeIfAbsent(player, k -> new PlayerStatus(player));
    }

    public static boolean existsFor(Player player) {
        return statuses.containsKey(player);
    }

    public static void remove(Player player) {
        statuses.remove(player);
    }


    private static Seq<Player> getFiltered(Boolf<PlayerStatus> filter) {
        Seq<Player> res = new Seq<>();
        statuses.values().iterator().forEachRemaining(e -> {
            if(filter.get(e))
                res.add(e.player);
        });
        return res;
    }

    public static Seq<Player> getVpnUsers() {
        return getFiltered(p -> p.fromVpn);
    }

    public static Seq<Player> getHistoryUsers() {
        return getFiltered(p -> p.historyEnabled);
    }

    public static Seq<Player> getVotedSkipMap() {
        return getFiltered(p -> p.votedSkipMap);
    }

    public static Seq<Player> getVotedNewWave() {
        return getFiltered(p -> p.votedNewWave);
    }

    public static Seq<Player> getNotAfk() {
        return getFiltered(p -> !p.afk);
    }

    public static void each(Cons<PlayerStatus> func) {
        statuses.values().iterator().forEachRemaining(func::get);
    }

    public void handleAction() {
        boolean wasAfk = afk;
        lastAction = Instant.now();
        afk = false;
        if(wasAfk)
            updateName();
    }

    public static void updateAll() {
        statuses.values().iterator().forEachRemaining(PlayerStatus::updatePlayer);
    }

    private void updatePlayer() {
        if (Duration.between(lastAction, Instant.now()).getSeconds() > Core.settings.getInt("afktimeout", 5 * 60) && !afk) {
            afk = true;
            MenuBuilder menu = new MenuBuilder("AFK", "Afk mode activated.\nPress OK to cancel.");
            menu.add("OK", (pl) -> handleAction());
            menu.onClose((pl) -> handleAction());
        }
    }

    public void addExperience(int amount) {
        experience = Math.max(experience + amount, 0);
        updateName();
    }

    public boolean hasLinkedDiscord() {
        return discordId != null && !discordId.isEmpty() && !discordId.equals("0");
    }

    public int getLevel() {
        return (int) Math.floor(0.5 * (Math.sqrt(0.08 * experience + 1) - 1));
    }

    public String getDisplayLevel() {
        return useCustomLevel && customLevel != null ? customLevel : String.valueOf(getLevel());
    }

    public String getName() {
        return customName == null || customName.isEmpty() ? defaultName : customName;
    }

    public String rankColor() {
        return rankColor == null ? "#ffffffff" : rankColor;
    }

    public String getDisplayName() {
        String name = String.format("[%s]<[green]%s[%s]> [#%s]%s", rankColor(), getDisplayLevel(), rankColor(), player.color.toString(), getName());
        if(fromVpn)
            name = "[stat]<[red]⚠[]>[]" + name;
        if(afk)
            name = "[gray]<AFK>[]" + name;
        if(frozen)
            name = "[#7a9af]<\uF7B5>[]" + name;
        return name;
    }

    public void updateName() {
        player.name(getDisplayName());
    }

    public void refresh(ResultSet rs) throws SQLException {
        customName = rs.getString("Custom_name");
        customLevel = rs.getString("Custom_level");
        useCustomLevel = rs.getBoolean("Use_custom_level");
        experience = rs.getLong("Experience");
        rankColor = rs.getString("Rank_color");
        discordId = rs.getString("discord_id");
        updateName();
    }

    public PlayerStatus refresh(DatabaseConnector.PlayerData data) {
        customName = data.customName();
        customLevel = data.customLevel();
        useCustomLevel = data.useCustomLevel();
        experience = data.experience();
        rankColor = data.rankColor();
        discordId = data.discordId();
        updateName();
        return this;
    }
}
    
