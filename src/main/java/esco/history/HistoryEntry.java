package main.java.esco.history;

import arc.struct.Seq;
import arc.util.Strings;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.utils.Utils;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.world.Tile;

import java.time.Instant;

public class HistoryEntry {
    public int rotation;
    public Object config;
    public final String uuid;
    public final int playerDBId;
    public final Instant actionTimestamp;
    public final Building build;
    public final boolean breaking;
    public final String type;
    public Tile tile;
    public Seq<Building> powerConnections;
    public String name;
    public boolean isLogic;

    public HistoryEntry(Building build, String uuid, boolean is_breaking, String type) {
        if (build != null) {
            this.rotation = build.rotation;
        }
        this.uuid = uuid;
        this.build = build;
        this.breaking = is_breaking;
        this.type = type;
        this.playerDBId = DatabaseConnector.getPlayerId(getPlayer());
        this.actionTimestamp = Instant.now();
    }

    public HistoryEntry setTile(Tile tile) {
        this.tile = tile;
        return this;
    }

    public Player getPlayer() {
        return Groups.player.find(p -> p.uuid().equals(this.uuid));
    }

    public String getPlayerName() {
        return getPlayer() == null ? name : getPlayer().coloredName();
    }

    public String getElapsedTime() {
        return Utils.formatTime(Instant.now().getEpochSecond() - actionTimestamp.getEpochSecond());
    }

    public String getMessage() {
        StringBuilder out = new StringBuilder(Strings.format("[lightgray](@) @ [gray][@][white]>: ", getElapsedTime(), getPlayerName(), playerDBId));
        switch (this.type) {
            case "rotate" -> out.append("rotated ").append(this.build.block.emoji()).append(" to ").append(rotation);
            case "config" -> {
                if (powerConnections != null) {
                    StringBuilder powerConnections = new StringBuilder();
                    this.powerConnections.each(con -> {
                        powerConnections.append("[white]").append(con.block.emoji()).append(" [red](").append((int) (con.x / 8)).append(", ").append((int) (con.y / 8)).append(") ");
                    });
                    if (powerConnections.length() < 2) {
                        out.append("removed all connections");
                        return out.toString();
                    }
                    out.append("added connections: ").append(powerConnections);
                    return out.toString();
                }
                if (isLogic) {
                    out.append("changed code in ").append(build.block.emoji());
                } else {
                    out.append("configured ").append(build.block.emoji()).append(" to ").append(this.config instanceof Item i ? i.emoji() : "[sky] " + (this.config == null ? "None" : this.config.toString()));
                }
                break;
            }
            case "break" -> {
                out.append("[red]breaked []").append(this.build == null ? this.tile.floor().emoji() : this.build.block.emoji());
                break;
            }
            case "build" -> {
                out.append("[lime]builded []").append(this.build == null ? this.tile.floor().emoji() : this.build.block.emoji());
                if (config instanceof Item i) {
                    out.append(" with config: ").append(i.emoji());
                }
                if (this.powerConnections != null) {
                    StringBuilder connections = new StringBuilder();
                    this.powerConnections.each(con -> {
                        connections.append("[white]").append(con.block.emoji()).append(" [red](").append((int) (con.x / 8)).append(", ").append((int) (con.y / 8)).append(") ");
                    });
                    if (connections.length() < 2) {
                        return out.toString();
                    }
                    out.append(" with connections: ").append(connections);
                }
                if (config instanceof String s) {
                    out.append(" with config: ").append(s);
                }
                break;
            }
        }
        return out.toString();
    }
}
