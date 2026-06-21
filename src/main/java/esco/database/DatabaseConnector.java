package main.java.esco.database;

import arc.Core;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Threads;
import arc.util.serialization.JsonReader;
import lombok.Getter;
import lombok.Setter;
import main.java.esco.PVars;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.bundle.Bundle;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.PlayerStatus;
import main.java.esco.utils.Utils;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.jetbrains.annotations.NotNull;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static main.java.esco.PVars.serverIP;
import static main.java.esco.discord.Bot.sendAlertMessage;
import static main.java.esco.utils.Utils.getUDPAddress;

public class DatabaseConnector {
    private static final String JDBC_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASS");

    private static final ConcurrentHashMap<Integer, StatsChange> cachedStats = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> cachedIds = new ConcurrentHashMap<>();

    private static final DataSource dataSource = createDataSource();

    public static boolean timeDebugEnabled = false;
    public static final ConcurrentHashMap<String, Long> timeDebug = new ConcurrentHashMap<>();

    static {
        BanListener.startListener(dataSource);
    }

    /**
     * Connection pooling
     */
    private static DataSource createDataSource() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found", e);
        }
        
        HikariConfig config = getHikariConfig();

        // Настройки для производительности
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true"); // Критично для batch insert!

        return new HikariDataSource(config);
    }

    @NotNull
    private static HikariConfig getHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        // Оптимальные настройки для PostgreSQL
        config.setMaximumPoolSize(10);  // Формула: (количество ядер * 2) + количество дисков
        config.setMinimumIdle(5);
        config.setConnectionTimeout(3000);  // 3 секунды
        config.setIdleTimeout(600000);      // 10 минут
        config.setMaxLifetime(1800000);     // 30 минут
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("PostgreSQLPool");
        return config;
    }

    public static void logMetrics() {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        Log.info(Strings.format("Pool stats: active=@, idle=@, total=@",
                hikariDataSource.getHikariPoolMXBean().getActiveConnections(),
                hikariDataSource.getHikariPoolMXBean().getIdleConnections(),
                hikariDataSource.getHikariPoolMXBean().getTotalConnections()));
    }

    /**
     * Удалить из памяти стату игрока
     *
     * @see main.java.esco.events.EventManager
     */
    public static void handlePlayerLeave(Player player) {
        cachedIds.remove(player.id);
        if (!cachedStats.containsKey(player.id))
            return;
        StatsChange stats = cachedStats.get(player.id);
        executeUpdate(
                "UPDATE Connections SET blocks_placed = ?, blocks_broken = ?, disconnect_timestamp = NOW(), finished = true WHERE id = ?",
                stmt -> {
                    stmt.setInt(1, stats.getTotalPlacedBlocks());
                    stmt.setInt(2, stats.getTotalBrokenBlocks());
                    stmt.setLong(3, stats.getConId());
                }
        );
        updatePlayerStats(player);
        cachedStats.remove(player.id);
    }

    public static void shutdown() {
        for (Map.Entry<Integer, StatsChange> entry : cachedStats.entrySet()) {
            StatsChange stats = entry.getValue();

            executeUpdate("WITH updated_connection AS (" +
                            "    UPDATE connections " +
                            "    SET " +
                            "        blocks_placed = ?," +
                            "        blocks_broken = ?," +
                            "        disconnect_timestamp = NOW()," +
                            "        finished = true " +
                            "    WHERE id = ? " +
                            "    RETURNING player_id, blocks_placed, blocks_broken" +
                            ")" +
                            "UPDATE players " +
                            "SET " +
                            "    blocks_placed = players.blocks_placed + updated_connection.blocks_placed," +
                            "    blocks_broken = players.blocks_broken + updated_connection.blocks_broken," +
                            "    playtime = players.playtime + ? " +
                            "FROM updated_connection " +
                            "WHERE players.id = updated_connection.player_id;",
                    stmt -> {
                        stmt.setInt(1, stats.getTotalPlacedBlocks());
                        stmt.setInt(2, stats.getTotalBrokenBlocks());
                        stmt.setLong(3, stats.getConId());
                        stmt.setLong(4, stats.getPlaytime());
                    }
            );
        }
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    /**
     * Обновить стату игрока
     *
     * @see main.java.esco.events.EventManager
     */
    public static boolean updatePlayerStats(Player player) {
        if (!cachedStats.containsKey(player.id)) {
            return false;
        }
        StatsChange stats = cachedStats.get(player.id);
        return executeUpdate(
                "UPDATE Players SET blocks_placed = blocks_placed + ?, blocks_broken = blocks_broken + ?, playtime = playtime + ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setInt(1, stats.getPlacedBlocks());
                    stmt.setInt(2, stats.getBrokenBlocks());
                    stmt.setLong(3, stats.getPlaytime());
                    stmt.setString(4, player.uuid());
                    stats.reset(); //ohno govnocode
                }
        );
    }

    /**
     * Получить игрока по айди
     */
    public static Player getPlayerById(int id) {
        for (Map.Entry<Integer, Integer> entry : cachedIds.entrySet()) {
            if (id == entry.getValue()) {
                Player found = Groups.player.getByID(entry.getKey());
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /**
     * Получить айди игрока
     */
    public static int getPlayerId(Player player) {
        if (player == null)
            return 0;
        if (cachedIds.containsKey(player.id))
            return cachedIds.get(player.id);
        return executeQueryAsync(
                "SELECT id FROM players WHERE uuid = ?",
                stmt -> {
                    stmt.setString(1, player.uuid());
                },
                rs -> rs.getInt("id")
        ).orElse(0);
    }

    public static boolean isGraylisted(Player player) {
        return executeQueryAsync(
                "SELECT 1 " +
                        "FROM graylist " +
                        "WHERE " +
                        "    Is_active = TRUE " +
			"    AND ( " +
                        "        CAST(? AS INET) <<= subnet " +
                        "        OR CAST(? AS INET) <<= subnet " +
                        "    ) " +
                        "LIMIT 1;",
                stmt -> {
                    String ipudp = getUDPAddress(player);
                    stmt.setString(1, ipudp);
                    stmt.setString(2, player.con.address);
                },
                rs -> 1
        ).isPresent();
    }

    /**
     * Получить бан игрока.
     */
    public static Optional<BanRecord> getBan(Player player) {
        return executeQueryAsync(
                "SELECT * " +
                        "FROM Bans " +
                        "WHERE " +
                        "	(Server = ? OR Server = '*') " +
                        "	AND Is_active = TRUE " +
                        "	AND (Unban_timestamp IS NULL OR Unban_timestamp > NOW()) " +
                        "	AND ( " +
                        "	    CAST(? AS INET) << subnet " +
                        "   	OR Player_id = (SELECT Id FROM Players WHERE Uuid = ?) " +
                        "	    OR Player_id IN ( " +
                        "		    SELECT Player_id FROM Connections " +
                        "		    WHERE Ip = CAST(? AS INET) " +
                        "		    OR Ip = CAST(? AS INET) " +
                        "		    OR Ip_udp = CAST(? AS INET) " +
                        "		    OR Ip_udp = CAST(? AS INET) " +
                        "		    OR usid = ? " +
                        "	    ) " +
                        "	) " +
                        "ORDER BY " +
                        "	CASE WHEN Unban_timestamp IS NULL THEN 1 ELSE 0 END DESC, " +
                        "	Unban_timestamp DESC " +
                        "LIMIT 1;",
                stmt -> {
                    String ipudp = getUDPAddress(player);
                    stmt.setString(1, Gamemode.getGamemode().toString());
                    stmt.setString(2, player.con.address);
                    stmt.setString(3, player.uuid());
                    stmt.setString(4, player.con.address);
                    stmt.setString(5, ipudp);
                    stmt.setString(6, player.con.address);
                    stmt.setString(7, ipudp);
                    stmt.setString(8, player.usid());
                },
                DatabaseConnector::mapResultSetToBan
        );
    }

    public static boolean verifyUSID(Player player) {
        return executeQueryAsync(
                "SELECT (NOT EXISTS ( " +
                        "SELECT 1 " +
                        "FROM players p " +
                        "WHERE p.uuid = ? " +
                        "AND p.protection " +
                        "AND EXISTS ( " +
                        "SELECT 1 " +
                        "FROM connections c " +
                        "WHERE c.player_id = p.id " +
                        "AND c.server = ? " +
                        "AND verified " +
                        ") " +
                        ") AND NOT EXISTS (" +
                        "SELECT 1 " +
                        "FROM admins a " +
                        "WHERE a.player_id = ( " +
                        "SELECT id FROM players WHERE uuid = ? " +
                        ")" +
                        ")) OR EXISTS ( " +
                        "SELECT 1 " +
                        "FROM players p " +
                        "JOIN connections c ON c.player_id = p.id " +
                        "WHERE p.uuid = ? " +
                        "AND (c.usid = ? OR c.ip = ?::inet) " +
                        "AND verified " +
                        ");",
                stmt -> {
                    stmt.setString(1, player.uuid());
                    stmt.setString(2, Gamemode.getGamemode().toString());
                    stmt.setString(3, player.uuid());
                    stmt.setString(4, player.uuid());
                    stmt.setString(5, player.usid());
                    stmt.setString(6, player.con.address);
                },
                rs -> rs.getBoolean(1)
        ).orElse(false);
    }

    public static Optional<Integer> CreateUnverifiedConnection(Player player) {
        return executeQueryAsync(
                "WITH insert_connection AS ( " +
                        "   INSERT INTO connections (player_id, server, name, plain_name, usid, mobile, ip, ip_udp, team, disconnect_timestamp, finished, verified) " +
                        "   SELECT Id, ?, ?, ?, ?, ?, CAST(? AS INET), CAST(? AS INET), ?, NOW(), true, false FROM players WHERE uuid = ? " +
                        "   RETURNING id AS connection_id " +
                        ") " +
                        "SELECT " +
                        "   ic.connection_id AS id " +
                        "FROM insert_connection ic ",
                stmt -> {
                    stmt.setString(1, Gamemode.getGamemode().toString());
                    stmt.setString(2, player.name);
                    stmt.setString(3, Strings.stripColors(player.name));
                    stmt.setString(4, player.usid());
                    stmt.setBoolean(5, player.con.mobile);
                    stmt.setString(6, player.con.address);
                    stmt.setString(7, getUDPAddress(player));
                    stmt.setInt(8, player.team().id);
                    stmt.setString(9, player.uuid());
                },
                rs -> rs.getInt("id")
        );
    }

    public static boolean verifyConnection(int conid, String discordid) {
        return executeUpdate("UPDATE connections SET verified = true WHERE id = ? AND player_id = (SELECT player_id FROM players WHERE discord_id = ?)",
                stmt -> {
                    stmt.setInt(1, conid);
                    stmt.setString(2, discordid);
                });
    }

    /**
     * Получить или создать игрока
     *
     * @return Данные игрока, опцинонально
     */
    public static Optional<PlayerData> getOrCreatePlayer(Player player) {
        return executeQueryAsync(
                "WITH updated_player AS ( " +
                        "   INSERT INTO Players (Uuid, Last_name, Last_ip, rank_color, mobile, locale, color) " +
                        "   VALUES (?, ?, CAST(? AS INET), 'white', ?, ?, ?) " +
                        "   ON CONFLICT (Uuid) DO UPDATE SET " +
                        "	   Last_name = EXCLUDED.Last_name, " +
                        "	   Last_ip = EXCLUDED.Last_ip, " +
                        "	   mobile = EXCLUDED.mobile, " +
                        "	   locale = EXCLUDED.locale, " +
                        "	   color = EXCLUDED.color " +
                        "   RETURNING * " +
                        "), " +
                        "insert_connection AS ( " +
                        "   INSERT INTO connections (player_id, server, name, plain_name, usid, mobile, ip, ip_udp, team) " +
                        "   SELECT Id, ?, ?, ?, ?, ?, Last_ip, CAST(? AS INET), ? FROM updated_player " +
                        "   RETURNING id AS connection_id " +
                        ") " +
                        "SELECT " +
                        "   up.*, " +
                        "   ic.connection_id " +
                        "FROM updated_player up " +
                        "CROSS JOIN insert_connection ic; ",
                stmt -> {
                    stmt.setString(1, player.uuid());
                    stmt.setString(2, player.name);
                    stmt.setString(3, player.con.address);
                    stmt.setBoolean(4, player.con.mobile);
                    stmt.setString(5, player.locale);
                    stmt.setString(6, '#' + player.color.toString());

                    stmt.setString(7, Gamemode.getGamemode().toString());
                    stmt.setString(8, player.name);
                    stmt.setString(9, Strings.stripColors(player.name));
                    stmt.setString(10, player.usid());
                    stmt.setBoolean(11, player.con.mobile);
                    stmt.setString(12, getUDPAddress(player));
                    stmt.setInt(13, player.team().id);
                },
                rs -> {
                    cachedIds.put(player.id, rs.getInt("id"));
                    cachedStats.put(player.id, new StatsChange(player.id, rs.getInt("connection_id")));
                    return mapResultSetToPlayer(rs);
                }
        );
    }

    /**
     * Получить данные игрока
     */
    public static Optional<PlayerData> getPlayerData(int playerId) {
        return executeQueryAsync(
                "SELECT * FROM Players WHERE Id = ?",
                stmt -> stmt.setInt(1, playerId),
                DatabaseConnector::mapResultSetToPlayer
        );
    }

    /**
     * Получить данные игрока
     */
    public static Optional<PlayerData> getPlayerData(Player player) {
        return getPlayerData(player.uuid());
    }

    /**
     * Получить данные игрока
     */
    public static Optional<PlayerData> getPlayerData(String uuid) {
        Groups.player.each(p -> {
            if (p.uuid().equals(uuid))
                updatePlayerStats(p);
        });
        return executeQueryAsync(
                "SELECT * FROM Players WHERE Uuid = ?",
                stmt -> stmt.setString(1, uuid),
                DatabaseConnector::mapResultSetToPlayer
        );
    }

    public static List<String> searchPlayers(String query) {
        return executeQueryList(
                "SELECT " +
                        "    p.*, " +
                        "    COALESCE(array_agg(DISTINCT c.ip) FILTER (WHERE c.ip IS NOT NULL), '{}') AS ips, " +
                        "    COALESCE(array_agg(DISTINCT nl.name) FILTER (WHERE nl.name IS NOT NULL), '{}') AS names " +
                        "FROM " +
                        "    Players p " +
                        "LEFT JOIN " +
                        "    connections c ON p.Id = c.player_id  " +
                        "LEFT JOIN " +
                        "    Name_list nl ON p.Id = nl.Player_id " +
                        "WHERE " +
                        "    p.Id::TEXT = ? OR " +
                        "    p.Last_name ILIKE '%' || ? || '%' OR " +
                        "    p.Custom_name ILIKE '%' || ? || '%' OR " +
                        "    p.Uuid = ? OR " +
                        "    c.Usid = ? OR " +
                        "    c.Ip <<= safe_cast_to_inet(?) OR " +
                        "    c.Plain_name ILIKE '%' || ? || '%' OR " +
                        "    nl.Plain ILIKE '%' || ? || '%' " +
                        "GROUP BY " +
                        "    p.id;",
                stmt -> {
                    for (int i = 1; i <= 8; i++)
                        stmt.setString(i, query);
                },
                DatabaseConnector::mapResultSetToPlayerData
        );
    }

    public static List<MapRecord> getMapsList(String server) {
        return executeQueryList(
                "SELECT * FROM maps WHERE server = ? ORDER BY Id ASC",
                stmt -> stmt.setString(1, server),
                DatabaseConnector::mapResultSetToMap
        );
    }

    public static Optional<MapRecord> getMap(Integer id) {
        return executeQueryAsync(
                "SELECT * FROM maps WHERE id = ?",
                stmt -> stmt.setInt(1, id),
                DatabaseConnector::mapResultSetToMap
        );
    }

    public static boolean updateMapStatistic(String mapName, boolean win, boolean skiped, int wave, Instant start, Duration duration) {
        return executeUpdate("INSERT INTO rounds (win, skiped, wave, start, duration, map) VALUES (?, ?, ?, ?::TIMESTAMP, ?::INTERVAL, (SELECT Id FROM maps WHERE name = ? AND server = ?))",
                stmt -> {
                    stmt.setBoolean(1, win);
                    stmt.setBoolean(2, skiped);
                    stmt.setInt(3, wave);
                    stmt.setString(4, start.toString()); //bad code here since 22 apr 2025
                    stmt.setString(5, duration.toString()); //if something goes wrong, the problem is here.
                    stmt.setString(6, mapName);
                    stmt.setString(7, Gamemode.getGamemode().toString());
                });
    }

    public static boolean createMapOrDoNothing(String name, String fileName) {
        return executeUpdate("INSERT INTO maps (name, file, server) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                stmt -> {
                    stmt.setString(1, name);
                    stmt.setString(2, fileName);
                    stmt.setString(3, Gamemode.getGamemode().toString());
                }
        );
    }

    public static Optional<MapStats> getMapStats(int id) {
        return executeQueryAsync(
                "SELECT " +
                        "    m.id, " +
                        "    m.name, " +
                        "    m.file, " +
                        "    m.server, " +
                        "    COUNT(r.*) FILTER (WHERE r.skiped) AS skips, " +
                        "    COUNT(r.*) FILTER (WHERE r.win) AS wins, " +
                        "    COUNT(r.*) FILTER (WHERE NOT r.win AND NOT r.skiped) AS losses, " +
                        "    EXTRACT(EPOCH FROM MIN(r.duration))::INTEGER AS min_duration, " +
                        "    EXTRACT(EPOCH FROM AVG(r.duration))::INTEGER AS avg_duration, " +
                        "    EXTRACT(EPOCH FROM MAX(r.duration))::INTEGER AS max_duration, " +
                        "    MIN(r.wave) AS min_wave, " +
                        "    AVG(r.wave)::INTEGER AS avg_wave, " +
                        "    MAX(r.wave) AS max_wave " +
                        "FROM maps m " +
                        "LEFT JOIN rounds r ON m.id = r.map " +
                        "WHERE m.id = ? " +
                        "GROUP BY m.id;",
                stmt -> stmt.setInt(1, id),
                DatabaseConnector::mapResultSetToMapStats
        );
    }

    public static Optional<MapStats> getMapStats(String name) {
        return executeQueryAsync(
                "SELECT " +
                        "    m.id, " +
                        "    m.name, " +
                        "    m.file, " +
                        "    m.server, " +
                        "    COUNT(r.*) FILTER (WHERE r.skiped) AS skips, " +
                        "    COUNT(r.*) FILTER (WHERE r.win) AS wins, " +
                        "    COUNT(r.*) FILTER (WHERE NOT r.win AND NOT r.skiped) AS losses, " +
                        "    EXTRACT(EPOCH FROM MIN(r.duration))::INTEGER AS min_duration, " +
                        "    EXTRACT(EPOCH FROM AVG(r.duration))::INTEGER AS avg_duration, " +
                        "    EXTRACT(EPOCH FROM MAX(r.duration))::INTEGER AS max_duration, " +
                        "    MIN(r.wave) AS min_wave, " +
                        "    AVG(r.wave)::INTEGER AS avg_wave, " +
                        "    MAX(r.wave) AS max_wave " +
                        "FROM maps m " +
                        "LEFT JOIN rounds r ON m.id = r.map " +
                        "WHERE m.name = ? " +
                        "GROUP BY m.id;",
                stmt -> stmt.setString(1, name),
                DatabaseConnector::mapResultSetToMapStats
        );
    }

    /**
     * Получить уровень доступа игрока
     */
    public static AccessLevel getAccessLevel(Player player) {
        return getAccessLevel(player.uuid());
    }

    public static AccessLevel getAccessLevel(String uuid) {
        Optional<String> res = executeQueryAsync(
                "SELECT a.access_level FROM admins a JOIN players p ON p.id = a.player_id WHERE p.uuid = ? AND a.is_active = true",
                stmt -> stmt.setString(1, uuid),
                rs -> rs.getString("access_level")
        );
        return AccessLevel.parseLevel(res.orElse("player"));
    }

    public static AccessLevel getAccessLevelByDiscord(String discordId) {
        Optional<String> res = executeQueryAsync(
                "SELECT a.access_level FROM admins a JOIN players p ON p.id = a.player_id WHERE p.discord_id = ? AND a.is_active = true",
                stmt -> stmt.setString(1, discordId),
                rs -> rs.getString("access_level")
        );
        return AccessLevel.parseLevel(res.orElse("player"));
    }

    /**
     * Получить запись админа игрока.
     */
    public static Optional<AdminRecord> getAdminRecord(Player player) {
        return executeQueryAsync(
                "SELECT a.* FROM admins a JOIN players p ON p.id = a.player_id WHERE p.uuid = ? AND a.is_active = true",
                stmt -> stmt.setString(1, player.uuid()),
                DatabaseConnector::mapResultSetToAdminRecord
        );
    }

    public static boolean logChatMessage(Player player, String message, String chatType) {
        return executeUpdate(
                "INSERT INTO messages (server, sender, message, plain, chat) VALUES (?, (SELECT id FROM players WHERE uuid = ?), ?, ?, ?)",
                stmt -> {
                    stmt.setString(1, Gamemode.getGamemode().toString());
                    stmt.setString(2, player.uuid());
                    stmt.setString(3, message);
                    stmt.setString(4, Strings.stripColors(message));
                    stmt.setString(5, chatType);
                }
        );
    }

    public static boolean logChatMessage(Player player, String message, String chatType, String accent) {
        return executeUpdate(
                "INSERT INTO messages (server, sender, message, plain, chat, accent) VALUES (?, (SELECT id FROM players WHERE uuid = ?), ?, ?, ?, ?)",
                stmt -> {
                    stmt.setString(1, Gamemode.getGamemode().toString());
                    stmt.setString(2, player.uuid());
                    stmt.setString(3, message);
                    stmt.setString(4, Strings.stripColors(message));
                    stmt.setString(5, chatType);
                    stmt.setString(6, accent);
                }
        );
    }

    /**
     * Добавить подеды в стату игрока
     */
    public static boolean incrementWins(String uuid) {
        int expChange = Gamemode.getGamemode().winCost;
        return executeUpdate(
                "UPDATE Players SET wins = wins + 1, experience = experience + ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setInt(1, expChange);
                    stmt.setString(2, uuid);
                }
        );
    }

    /**
     * Добавить проигрыши в стату игрока
     */
    public static boolean incrementLoses(String uuid) {
        int expChange = Gamemode.getGamemode().loseCost;
        return executeUpdate(
                "UPDATE Players SET loses = loses + 1, experience = GREATEST(experience + ?, 0) WHERE Uuid = ?",
                stmt -> {
                    stmt.setInt(1, expChange);
                    stmt.setString(2, uuid);
                }
        );
    }

    /**
     * Добавить опыта в стату игрока
     */
    public static boolean giveExperience(Player player, int exp) {
        boolean result = executeUpdate(
                "UPDATE Players SET experience = GREATEST(experience + ?, 0) WHERE Uuid = ?",
                stmt -> {
                    stmt.setInt(1, exp);
                    stmt.setString(2, player.uuid());
                }
        );
        updateLevel(player);
        return result;
    }

    /**
     * Добавить прожитых волн в стату игрока
     */
    public static boolean incrementWaves(String uuid) {
        int expChange = Gamemode.getGamemode().waveCost;
        if (expChange == 0)
            return false;
        return executeUpdate(
                "UPDATE Players SET Waves_survived = Waves_survived + 1, experience = experience + ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setInt(1, expChange);
                    stmt.setString(2, uuid);
                }
        );
    }

    public static boolean batchIncrementWaves(List<String> uuids) {
        int expChange = Gamemode.getGamemode().waveCost;
        if (expChange == 0 || uuids.isEmpty()) {
            return false;
        }
        return executeUpdate(
                "UPDATE Players SET Waves_survived = Waves_survived + 1, experience = experience + ? WHERE Uuid = ANY(?)",
                stmt -> {
                    stmt.setInt(1, expChange);
                    Array uuidArray = stmt.getConnection().createArrayOf("VARCHAR", uuids.toArray(new String[0]));
                    stmt.setArray(2, uuidArray);
                }
        );
    }

    /**
     * Добавить построенных блоков в стату игрока
     */
    public static void incrementPlace(Player player) {
        if (!cachedStats.containsKey(player.id))
            return;
        cachedStats.get(player.id).incrementPlace();
    }

    /**
     * Добавить сломанных блоков в стату игрока
     */
    public static void incrementBreak(Player player) {
        if (!cachedStats.containsKey(player.id))
            return;
        cachedStats.get(player.id).incrementBreak();
    }

    /**
     * Обновить лвл игрока
     */
    public static void updateLevel(Player player) {
        getPlayerData(player);
    }

    /**
     * Обновить кастомное имя игрока в БД
     */
    public static boolean updateCustomName(Player player, String newName) {
        boolean res = executeUpdate(
                "WITH updated_player AS ( " +
                        "   UPDATE Players SET custom_name = ? WHERE Uuid = ? " +
                        "   RETURNING Id, custom_name " +
                        ") " +
                        "INSERT INTO Name_list (Player_id, Name, plain) " +
                        "SELECT Id, custom_name, ? FROM updated_player " +
                        "ON CONFLICT (Player_id, Name) DO NOTHING",
                stmt -> {
                    stmt.setString(1, newName);
                    stmt.setString(2, player.uuid());
                    stmt.setString(3, Strings.stripColors(newName));
                }
        );
        return res;
    }

    /**
     * Обновить включен ли кастомный лвл
     */
    public static boolean updateCustomLevelState(Player player, boolean newState) {
        return executeUpdate(
                "UPDATE Players SET Use_custom_level = ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setBoolean(1, newState);
                    stmt.setString(2, player.uuid());
                }
        );
    }

    public static boolean updateEliteState(Player player, boolean newState) {
        return executeUpdate(
                "UPDATE Players SET elite = ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setBoolean(1, newState);
                    stmt.setString(2, player.uuid());
                }
        );
    }

    public static boolean updateProtectionState(Player player, boolean newState) {
        return executeUpdate(
                "UPDATE Players SET protection = ? WHERE Uuid = ?",
                stmt -> {
                    stmt.setBoolean(1, newState);
                    stmt.setString(2, player.uuid());
                }
        );
    }

    /**
     * Обновить включено ли скрытие админки
     */
    public static boolean updateAdminVanishState(Player player, boolean newState) {
        return executeUpdate(
                "UPDATE Admins SET hidden = ? WHERE player_id = (SELECT id FROM players WHERE uuid = ?)",
                stmt -> {
                    stmt.setBoolean(1, newState);
                    stmt.setString(2, player.uuid());
                }
        );
    }

    public static boolean updateConSchemesizeState(Player player, boolean state) {
        StatsChange stats = cachedStats.get(player.id);
        return executeUpdate(
                "UPDATE Connections SET schemesize = ? WHERE id = ?",
                stmt -> {
                    stmt.setBoolean(1, state);
                    stmt.setLong(2, stats.getConId());
                }
        );
    }

    public static boolean updateConFooState(Player player, boolean state) {
        StatsChange stats = cachedStats.get(player.id);
        return executeUpdate(
                "UPDATE Connections SET foos = ? WHERE id = ?",
                stmt -> {
                    stmt.setBoolean(1, state);
                    stmt.setLong(2, stats.getConId());
                }
        );
    }

    /**
     * Забанить игрока
     */
    public static boolean banPlayer(int targetId, String reason, Instant unbanTime, int adminId, String server, String source) {
        boolean result = executeUpdate(
                "INSERT INTO Bans (Player_id, Reason, unban_timestamp, Admin_id, server, source) VALUES (?, ?, ?, ?, ?, ?)",
                stmt -> {
                    stmt.setInt(1, targetId);
                    stmt.setString(2, reason);
                    stmt.setTimestamp(3, Timestamp.from(unbanTime));
                    stmt.setInt(4, adminId);
                    stmt.setString(5, server);
                    stmt.setString(6, source);
                }
        );
        Player player = getPlayerById(targetId);
        if (player != null) {
            getBan(player).ifPresent(r -> {
                player.kick(r.getFormattedReason(player.locale));
            });
        }
        return result;
    }

    /**
     * Забанить игрока пермой
     */
    public static boolean banPlayerPerm(int targetId, String reason, int adminId, String server, String source) {
        boolean result = executeUpdate(
                "INSERT INTO Bans (Player_id, Reason, Admin_id, server, source) VALUES (?, ?, ?, ?, ?)",
                stmt -> {
                    stmt.setInt(1, targetId);
                    stmt.setString(2, reason);
                    stmt.setInt(3, adminId);
                    stmt.setString(4, server);
                    stmt.setString(5, source);
                }
        );
        Player player = getPlayerById(targetId);
        if (player != null) {
            getBan(player).ifPresent(r -> {
                player.kick(r.getFormattedReason(player.locale));
            });
        }
        return result;
    }

    /**
     * Забанить игрока
     */
    public static boolean banPlayer(String targetUUID, String reason, Instant unbanTime, int adminId, String server, String source) {
        boolean result = executeUpdate(
                "INSERT INTO Bans (Player_id, Reason, unban_timestamp, Admin_id, server, source) VALUES ((SELECT Id FROM Players WHERE Uuid = ?), ?, ?, ?, ?, ?)",
                stmt -> {
                    stmt.setString(1, targetUUID);
                    stmt.setString(2, reason);
                    stmt.setTimestamp(3, Timestamp.from(unbanTime));
                    stmt.setInt(4, adminId);
                    stmt.setString(5, server);
                    stmt.setString(6, source);
                }
        );
        Player player = Groups.player.find(p -> p.uuid().equals(targetUUID));
        if (player != null) {
            getBan(player).ifPresent(r -> {
                player.kick(r.getFormattedReason(player.locale));
            });
        }
        return result;
    }

    /**
     * Забанить игрока пермой
     */
    public static boolean banPlayerPerm(String targetUUID, String reason, int adminId, String server, String source) {
        boolean result = executeUpdate(
                "INSERT INTO Bans (Player_id, Reason, Admin_id, server, source) VALUES ((SELECT Id FROM Players WHERE Uuid = ?), ?, ?, ?, ?)",
                stmt -> {
                    stmt.setString(1, targetUUID);
                    stmt.setString(2, reason);
                    stmt.setInt(3, adminId);
                    stmt.setString(4, server);
                    stmt.setString(5, source);
                }
        );
        Player player = Groups.player.find(p -> p.uuid().equals(targetUUID));
        if (player != null) {
            getBan(player).ifPresent(r -> {
                player.kick(r.getFormattedReason(player.locale));
            });
        }
        return result;
    }
    
    public static boolean banPlayerWithVotes(int targetId, String reason, Instant unbanTime, int adminId, String server, String source, Map<String, Integer> votes) {
        String sql = """
        WITH ban_insert AS (
            INSERT INTO Bans (Player_id, Reason, unban_timestamp, Admin_id, server, source)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING ban_id
        ),
        vote_data(uuid, vote_value) AS (
            SELECT unnest(?::text[]), unnest(?::int[])
        ),
        kickvotes_insert AS (
            INSERT INTO kickvotes (player, ban, vote)
            SELECT p.id, bi.ban_id, vd.vote_value
            FROM ban_insert bi
            CROSS JOIN vote_data vd
            JOIN players p ON p.uuid = vd.uuid
            RETURNING 1  -- Dummy return to satisfy syntax
        )
        SELECT ban_id FROM ban_insert;
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, targetId);
            stmt.setString(2, reason);
            stmt.setTimestamp(3, Timestamp.from(unbanTime));
            stmt.setInt(4, adminId);
            stmt.setString(5, server);
            stmt.setString(6, source);

            List<String> uuidList = new ArrayList<>(votes.size());
            List<Integer> voteList = new ArrayList<>(votes.size());
            for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                uuidList.add(entry.getKey());
                voteList.add(entry.getValue());
            }
            String[] uuids = uuidList.toArray(new String[0]);
            Integer[] voteValues = voteList.toArray(new Integer[0]);
            
            stmt.setArray(7, conn.createArrayOf("text", uuids));
            stmt.setArray(8, conn.createArrayOf("integer", voteValues));

            try (ResultSet rs = stmt.executeQuery()) {
                boolean success = rs.next();
                return success;
            }
        } catch (SQLException e) {
            Log.err("Failed to process votekick with votes", e);
            return false;
        }
    }


    /**
     * Получить топ игроков
     */
    public static List<PlayerData> getTopPlayers() {
        return executeQueryList(
                "SELECT * FROM players ORDER BY -experience LIMIT 10",
                stmt -> {
                },
                DatabaseConnector::mapResultSetToPlayer
        );
    }

    public static int getPlayerIdByDiscord(String discord) {
        return executeQueryAsync(
                "SELECT * FROM players WHERE discord_id = ?",
                stmt -> stmt.setString(1, discord),
                rs -> rs.getInt("id")
        ).orElse(-1);
    }

    public static long getPlayerDiscord(int playerid) {
        return executeQueryAsync(
                "SELECT discord_id FROM players WHERE id = ?",
                stmt -> stmt.setInt(1, playerid),
                rs -> rs.getLong("discord_id")
        ).orElse(-1L);
    }

    public static long getPlayerDiscord(Player player) {
        return executeQueryAsync(
                "SELECT discord_id FROM players WHERE uuid = ?",
                stmt -> stmt.setString(1, player.uuid()),
                rs -> rs.getLong("discord_id")
        ).orElse(-1L);
    }

    public static void setPlayerDiscord(int playerid, String discordid) {
        executeUpdate("UPDATE players SET discord_id = ?, rank_color = '#ADFF9E' where id = ?",
                stmt -> {
                    stmt.setString(1, discordid);
                    stmt.setInt(2, playerid);
                }
        );
    }

    /**
     * Получить топ игроков
     */
    public static List<PlayerData> getTopPlayers(String ordby) {
        return executeQueryList(
                "SELECT * FROM players ORDER BY -" + ordby + " LIMIT 10",
                stmt -> {
                },
                DatabaseConnector::mapResultSetToPlayer
        );
    }

    /**
     * Выполнить sql код
     */
    private static <T> Optional<T> executeQueryAsync(String sql, ThrowingConsumer<PreparedStatement> parameterSetter, SQLFunction<ResultSet, T> mapper) {
        long start = System.nanoTime();
        Optional<T> returnValue = Optional.empty();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            parameterSetter.accept(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    returnValue = Optional.of(mapper.apply(rs));
                }
            }
        } catch (SQLException e) {
            if (dataSource instanceof HikariDataSource hikari && hikari.isClosed()) {
                Log.err("Database connection pool is closed!", e);
            } else {
                Log.err("Database query failed: " + sql, e);
            }
        }
        long end = System.nanoTime();
        if(timeDebugEnabled)
            timeDebug.compute(sql, (key, old) -> {
                if(old == null)
                    old = 0l;
                return old + (end - start);
            });
        return returnValue;
    }

    /**
     * Обновить данные
     */
    private static boolean executeUpdate(String sql, ThrowingConsumer<PreparedStatement> parameterSetter) {
        int updated = 0;
        long start = System.nanoTime();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            parameterSetter.accept(pstmt);
            updated = pstmt.executeUpdate();

        } catch (SQLException e) {
            if (dataSource instanceof HikariDataSource hikari && hikari.isClosed()) {
                Log.err("Database connection pool is closed!", e);
            } else {
                Log.err("Database query failed: " + sql, e);
            }
        }
        long end = System.nanoTime();
        if(timeDebugEnabled)
            timeDebug.compute(sql, (key, old) -> {
                if(old == null)
                    old = 0l;
                return old + (end - start);
            });
        return updated > 0;
    }

    /**
     * Выполнить sql код как-то
     */
    private static <T> List<T> executeQueryList(String sql, ThrowingConsumer<PreparedStatement> parameterSetter, SQLFunction<ResultSet, T> mapper) {
        long start = System.nanoTime();
        List<T> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            parameterSetter.accept(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.apply(rs));
                }
            }
        } catch (SQLException e) {
            if (dataSource instanceof HikariDataSource hikari && hikari.isClosed()) {
                Log.err("Database connection pool is closed!", e);
            } else {
                Log.err("Database query failed: " + sql, e);
            }
        }
        long end = System.nanoTime();
        if(timeDebugEnabled)
            timeDebug.compute(sql, (key, old) -> {
                if(old == null)
                    old = 0l;
                return old + (end - start);
            });
        return results;
    }

    public static Optional<String> getName(int id) {
        return executeQueryAsync(
                "SELECT * FROM player WHERE id = ?",
                stmt->stmt.setInt(1, id),
                rs->rs.getString("last_name")
        );
    }

    private static String mapResultSetToPlayerData(ResultSet rs) throws SQLException {
        return Strings.format("""
                        Trace info of player ID @
                        last name: @
                        [white]custom name: @
                        [white]UUID: @
                        custom level: @
                        [white]use custom level: @
                        rank color: @
                        experience: @
                        wins: @
                        losses: @
                        blocks placed: @
                        blocks broken: @
                        waves survived: @
                        playtime: @
                        last ip: @
                        first join: @
                        social credit score: @
                        color: @
                        mobile: @
                        locale: @
                        discord id: @
                        IPs: @
                        Names: @
                        """,
                rs.getString("id"),
                rs.getString("last_name"),
                rs.getString("custom_name"),
                rs.getString("uuid"),
                rs.getString("custom_level"),
                rs.getString("use_custom_level"),
                rs.getString("rank_color"),
                rs.getString("experience"),
                rs.getString("wins"),
                rs.getString("loses"),
                rs.getString("blocks_placed"),
                rs.getString("blocks_broken"),
                rs.getString("waves_survived"),
                rs.getString("playtime"),
                rs.getString("last_ip"),
                rs.getString("first_join"),
                rs.getString("social_credit_score"),
                rs.getString("color"),
                rs.getString("mobile"),
                rs.getString("locale"),
                rs.getString("discord_id"),
                rs.getString("ips"),
                rs.getString("names")
        );
    }


    /**
     * Перевести ответ бд в класс данных игрока
     */
    private static PlayerData mapResultSetToPlayer(ResultSet rs) throws SQLException {
        String uuid = rs.getString("uuid");
        if (uuid != null) {
            Player player = Groups.player.find(p -> uuid.equals(p.uuid()));
            if (player != null) {
                if(PlayerStatus.existsFor(player))
                    PlayerStatus.get(player).refresh(rs);
            }
        }
        return new PlayerData(
                rs.getInt("Id"),
                rs.getString("Uuid"),
                rs.getString("Last_ip"),
                rs.getString("Last_name"),
                rs.getString("Custom_name"),
                rs.getString("Custom_level"),
                rs.getString("Rank_color"),
                rs.getLong("Experience"),
                rs.getInt("Wins"),
                rs.getInt("Loses"),
                rs.getInt("Blocks_placed"),
                rs.getInt("Blocks_broken"),
                rs.getInt("Waves_survived"),
                rs.getLong("Playtime"),
                rs.getBoolean("Use_custom_level"),
                rs.getTimestamp("First_join").toInstant(),
                rs.getBoolean("Mobile"),
                rs.getString("locale"),
                Color.valueOf(rs.getString("color")),
                rs.getString("discord_id"),
                rs.getBoolean("protection"),
                rs.getBoolean("elite")
        );
    }

    public static Optional<BanRecord> getBan(int banid) {
        return executeQueryAsync("SELECT * FROM BANS WHERE ban_id = ?",
                stmt -> stmt.setInt(1, banid),
                DatabaseConnector::mapResultSetToBan
        );
    }

    /**
     * Перевести ответ бд в класс карты
     */
    private static MapRecord mapResultSetToMap(ResultSet rs) throws SQLException {
        return new MapRecord(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("file"),
                rs.getString("server")
        );
    }

    /**
     * Перевести ответ бд в класс бана
     */
    private static BanRecord mapResultSetToBan(ResultSet rs) throws SQLException {
        Timestamp unbanTimestamp = rs.getTimestamp("unban_timestamp");
        return new BanRecord(
                rs.getInt("ban_id"),
                rs.getString("subnet"),
                rs.getInt("player_id"),
                rs.getInt("admin_id"),
                rs.getTimestamp("ban_timestamp").toInstant(),
                unbanTimestamp == null ? Instant.now() : unbanTimestamp.toInstant(),
                rs.getString("reason"),
                rs.getBoolean("is_active"),
                rs.getString("server"),
                rs.getString("source"),
                unbanTimestamp == null
        );
    }

    /**
     * Перевести ответ бд в класс админа
     */
    private static AdminRecord mapResultSetToAdminRecord(ResultSet rs) throws SQLException {
        return new AdminRecord(
                rs.getInt("player_id"),
                AccessLevel.parseLevel(rs.getString("access_level")),
                rs.getBoolean("hidden"),
                rs.getBoolean("is_active"),
                rs.getBoolean("v7")
        );
    }

    private static MapStats mapResultSetToMapStats(ResultSet rs) throws SQLException {
        return new MapStats(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("file"),
                rs.getString("server"),
                rs.getInt("skips"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("min_duration"),
                rs.getInt("avg_duration"),
                rs.getInt("max_duration"),
                rs.getInt("min_wave"),
                rs.getInt("avg_wave"),
                rs.getInt("max_wave")
        );
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    private interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    public record PlayerData(int id, String uuid, String lastIP, String lastName, String customName, String customLevel,
                             String rankColor, long experience, int wins, int loses, int blocksPlaced, int blocksBroken,
                             int wavesSurvived, long playtime, boolean useCustomLevel, Instant firstJoinTime,
                             boolean isMobile, String locale, Color color, String discordId, boolean protection, boolean elite) {

        public int getLevel() {
                return (int) Math.floor(0.5 * (Math.sqrt(0.08 * experience + 1) - 1));
            }

            public int getExperienceForLevel(int level) {
                return (int) (((2 * level + 1) * (2 * level + 1) - 1) / 0.08);
            }

            public String getDisplayLevel() {
                return useCustomLevel && customLevel != null ? customLevel : String.valueOf(getLevel());
            }

            public String getName() {
                return customName == null || customName.isEmpty() ? lastName : customName;
            }

            @Override
            public String rankColor() {
                return rankColor == null ? "#ffffffff" : rankColor;
            }

            public String getDisplayName() {
                return String.format("[%s]<[green]%s[%s]> [#%s]%s", rankColor(), getDisplayLevel(), rankColor(), color, getName());
            }

            public boolean redirectToElite() {
                return elite && experience >= 300;
            }

            public boolean redirectToBasic() {
                return !elite || experience < 300;
            }

            public boolean shouldRedirect() {
                Gamemode current = Gamemode.getGamemode();
                if(!current.separated)
                    return false;
                if(redirectToElite() && !current.isElite)
                    return true;
                if(redirectToBasic() && current.isElite)
                    return true;
                return false;
            }
        }

    public record BanRecord(int id, String subnet, int playerId, int adminId, Instant banTime, Instant unbanTime,
                            String reason, boolean isActive, String server, String source, boolean isPerm) {

        public String getFormattedReason(String locale) {
            if (isPerm) {
                return MessageFormat.format(
                        Bundle.get("esco.admins.permBanned", locale),
                        reason(),
                        id(),
                        PVars.discordLink
                );
            } else {
                return MessageFormat.format(
                        Bundle.get("esco.admins.banned", locale),
                        reason(),
                        Utils.formatTime(unbanTime.getEpochSecond() - Instant.now().getEpochSecond()),
                        id(),
                        PVars.discordLink
                );
            }
        }
    }

    public record AdminRecord(int playerId, AccessLevel accessLevel, boolean hidden, boolean isActive, boolean v7) {

        public boolean getIsHidden() {
            return hidden;
        }

        public boolean activeHere() {
            return isActive && (Gamemode.getGamemode().version == 8 || v7);
        }
    }

    public static class StatsChange {
        private final int id;
        private final int conId;
        private int blocksPlaced;
        private int blocksBroken;
        private final long joinTime;
        private int totalBlocksPlaced;
        private int totalBlocksBroken;
        private long lastPlaytime;


        public StatsChange(int id, int conId) {
            this.id = id;
            this.conId = conId;
            this.joinTime = System.currentTimeMillis();
        }

        public long getTotalPlaytime() {
            return (System.currentTimeMillis() - joinTime) / 1000;
        }

        public long getPlaytime() {
            long currentTotal = getTotalPlaytime();
            long playtime = currentTotal - this.lastPlaytime;
            this.lastPlaytime = currentTotal;
            return playtime;
        }

        public void reset() {
            blocksPlaced = 0;
            blocksBroken = 0;
        }

        public void incrementPlace() {
            blocksPlaced++;
            totalBlocksPlaced++;
        }

        public void incrementBreak() {
            blocksBroken++;
            totalBlocksBroken++;
        }

        public int getPlacedBlocks() {
            return blocksPlaced;
        }

        public int getBrokenBlocks() {
            return blocksBroken;
        }

        public int getTotalPlacedBlocks() {
            return totalBlocksPlaced;
        }

        public int getTotalBrokenBlocks() {
            return totalBlocksBroken;
        }

        public int getConId() {
            return conId;
        }

        public int getId() {
            return id;
        }
    }

    @Getter
    @Setter
    public static class MapRecord {
        private final int id;
        private final String name;
        private final String fileName;
        private final String server;

        MapRecord(int id, String name, String fileName, String server) {
            this.id = id;
            this.name = name;
            this.fileName = fileName;
            this.server = server;
        }
    }

    @Getter
    @Setter
    public static class MapStats {
        private final int id;
        private final String name;
        private final String fileName;
        private final String server;
        private final int skips;
        private final int wins;
        private final int losses;
        private final int minDuration;
        private final int avgDuration;
        private final int maxDuration;
        private final int minWave;
        private final int avgWave;
        private final int maxWave;

        MapStats(int id, String name, String fileName, String server, int skips, int wins, int losses, int minDuration, int avgDuration, int maxDuration, int minWave, int avgWave, int maxWave) {
            this.id = id;
            this.name = name;
            this.fileName = fileName;
            this.server = server;
            this.skips = skips;
            this.wins = wins;
            this.losses = losses;
            this.minDuration = minDuration;
            this.avgDuration = avgDuration;
            this.maxDuration = maxDuration;
            this.minWave = minWave;
            this.avgWave = avgWave;
            this.maxWave = maxWave;
        }
    }

    public static class BanListener {
        public static void startListener(DataSource dataSource) {
            Executors.newSingleThreadExecutor().submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    PGConnection pgConn = conn.unwrap(PGConnection.class);
                    Statement stmt = conn.createStatement();
                    stmt.execute("LISTEN player_banned");
                    stmt.close();

                    while (true) {
                        PGNotification[] notifications = pgConn.getNotifications(5000);
                        if (notifications != null) {
                            for (PGNotification notification : notifications) {
                                int playerId = Integer.parseInt(notification.getParameter());
                                Log.debug("Recived new ban for player " + playerId);

                                Player player = DatabaseConnector.getPlayerById(playerId);
                                if (player != null) {
                                    Log.debug("Player found, checking ban.");
                                    DatabaseConnector.getBan(player).ifPresent(ban -> {
                                        Core.app.post(() -> player.kick(ban.getFormattedReason(player.locale), 0));
                                    });
                                }
                            }
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    Log.err(e.getMessage());
                    sendAlertMessage("Exception in ban listener thread on server " + Gamemode.getGamemode().toString() + ".\n```\n" + e.getMessage() + "\n```");
                    BanListener.startListener(dataSource);
                }
            });
        }

        public static void startDetailedListener() {
            Threads.daemon(()->{
                try (Connection conn = dataSource.getConnection()) {
                    PGConnection pgConn = conn.unwrap(PGConnection.class);
                    Statement stmt = conn.createStatement();
                    stmt.execute("LISTEN player_banned_detailed");
                    stmt.close();

                    while(true) {
                        PGNotification[] notifications = pgConn.getNotifications(5000);
                        if (notifications != null) {
                            for (PGNotification notification : notifications) {
                                String param = notification.getParameter();
                                JsonReader json = new JsonReader();

                                var root = json.parse(param);

                                if (!root.getString("TG_OP").equals("INSERT"))
                                    continue;

                                var data = json.parse(root.getString("new_data"));

                                int playerId = data.getInt("player_id");
                                String server = data.getString("server");
                                String reason = data.getString("reason");

                                getName(playerId).ifPresent(name -> {
                                    boolean everywhere = server.equals("*");

                                    String key = everywhere ? "esco.announcement.ban.everywhere" : "esco.announcement.ban";

                                    String messageEn = everywhere
                                            ? MessageFormat.format(Bundle.get(key, "en"), name, reason)
                                            : MessageFormat.format(Bundle.get(key, "en"), name, server, reason);

                                    String messageRu = everywhere
                                            ? MessageFormat.format(Bundle.get(key, "ru"), name, reason)
                                            : MessageFormat.format(Bundle.get(key, "ru"), name, server, reason);

                                    Groups.player.each(p ->
                                            p.sendMessage(p.locale.contains("ru") ? messageRu : messageEn)
                                    );
                                });
                            }
                        }
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    Log.err(e);
                    sendAlertMessage("Exception in ban detailed listener thread on server " + Gamemode.getGamemode().toString() + ".\n```\n" + e.getMessage() + "\n```");
                }
            });
        }
    }
}
