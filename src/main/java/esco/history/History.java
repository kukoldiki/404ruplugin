package main.java.esco.history;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.power.PowerNode;

public class History {
    public static final int limit = 24;
    public final Seq<Tile> tiles = new Seq<>();
    public final ObjectMap<Tile, HistoryStack> history = new ObjectMap<>();

    public History() {
        tiles.clear();
        for (Tile tile : Vars.world.tiles) {
            tiles.add(tile);
            history.put(tile, null);
        }
    }

    public void reset() {
        tiles.clear();
        history.clear();
        for (Tile tile : Vars.world.tiles) {
            tiles.add(tile);
            history.put(tile, null);
        }
        System.gc();
    }

    public void initEvents() {
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.unit == null || event.unit.getPlayer() == null || event.breaking) return;
            var entry = createEntry(event);
            if (history.get(event.tile) != null) {
                var stack = history.get(event.tile);
                if (stack.currentSize() > limit) {
                    stack.removeLast();
                }
                addEntry(entry, event.tile);
            } else {
                history.put(event.tile, new HistoryStack(event.tile));
                addEntry(entry, event.tile);
            }
        });
        Events.on(EventType.BlockBuildBeginEvent.class, event -> {
            if (event.unit == null || event.unit.getPlayer() == null || !event.breaking) return;
            var entry = createEntry(event);
            if (history.get(event.tile) != null) {
                var stack = history.get(event.tile);
                if (stack.currentSize() > limit) {
                    stack.removeLast();
                }
                addEntry(entry, event.tile);
            } else {
                history.put(event.tile, new HistoryStack(event.tile));
                addEntry(entry, event.tile);
            }
        });
        Events.on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null || event.tile.dead || event.player == null) return;
            var entry = createEntry(event);
            if (history.get(event.tile.tile) != null) {
                var stack = history.get(event.tile.tile);
                if (stack.currentSize() > limit) {
                    stack.removeLast();
                }
                addEntry(entry, event.tile.tile);
            } else {
                history.put(event.tile.tile, new HistoryStack(event.tile.tile));
                addEntry(entry, event.tile.tile);
            }
        });
        Events.on(EventType.WorldLoadEvent.class, event -> {
            this.reset();
        });
//        Events.on(EventType.TapEvent.class, event -> {
//            if (!MVars.historyPlayers.contains(event.player)) return;
//            StringBuilder output = new StringBuilder();
//            var stack = history.get(event.tile);
//            if (stack != null) {
//                for (int i = 0; i < stack.stack.size; i++) {
//                    var entry = stack.stack.get(i);
//                    if (entry != null) {
//                        output.append("[accent][").append(i + 1).append("][] ").append(entry.getMessage()).append("\n");
//                    }
//                }
//            }
//            Call.infoPopup(event.player.con, output.toString(), 3f, 1, 200, 0, 0, 0);
//        });
        Events.on(EventType.BuildRotateEvent.class, event -> {
            if (event.build == null || event.unit == null || event.unit.getPlayer() == null) return;
            var entry = createEntry(event);
            if (history.get(event.build.tile) != null) {
                var stack = history.get(event.build.tile);
                if (stack.currentSize() > limit) {
                    stack.removeLast();
                }
                addEntry(entry, event.build.tile);
            } else {
                history.put(event.build.tile, new HistoryStack(event.build.tile));
                addEntry(entry, event.build.tile);
            }
        });
    }


    private HistoryEntry createEntry(Object event) {
        if (event instanceof EventType.BuildRotateEvent e) {
            HistoryEntry entry = new HistoryEntry(e.build, e.unit.getPlayer().uuid(), false, "rotate");
            entry.config = e.build.rotation;
            return entry;
        } else if (event instanceof EventType.BlockBuildEndEvent e) {
            var entry = new HistoryEntry(e.tile.build, e.unit.getPlayer().uuid(), e.breaking, e.breaking ? "break" : "build");
            if (e.tile.build == null) entry.setTile(e.tile);
            if (e.tile.build instanceof PowerNode.PowerNodeBuild n) {
                entry.powerConnections = n.getPowerConnections(new Seq<>());
                if (entry.powerConnections.isEmpty()) entry.powerConnections.add(e.tile.build);
            }
            entry.name = e.unit.getPlayer().coloredName();
            entry.config = e.tile.build == null ? null : e.tile.build.config();
            entry.isLogic = e.tile.build instanceof LogicBlock.LogicBuild;
            return entry;
        } else if (event instanceof EventType.ConfigEvent e) {
            var entry = new HistoryEntry(e.tile, e.player.uuid(), false, "config");
            if (e.tile instanceof PowerNode.PowerNodeBuild n) {
                entry.powerConnections = n.getPowerConnections(new Seq<>());
            }
            entry.name = e.player.coloredName();
            entry.isLogic = e.tile instanceof LogicBlock.LogicBuild;
            entry.config = e.value;
            return entry;
        } else {
            EventType.BlockBuildBeginEvent e = (EventType.BlockBuildBeginEvent) event;
            var entry = new HistoryEntry(e.tile.build, e.unit.getPlayer().uuid(), true, "break");
            entry.isLogic = e.tile.build instanceof LogicBlock.LogicBuild;
            entry.config = e.tile.build.config();
            entry.name = e.unit.getPlayer().coloredName();
            return entry;
        }
    }

    private void addEntry(HistoryEntry entry, Tile tile) {
        tile.getLinkedTiles(t -> {
            if (history.get(t) == null) history.put(t, new HistoryStack(t).addEntry(entry));
            else history.get(t).addEntry(entry);
        });
    }
}
