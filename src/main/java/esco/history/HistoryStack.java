package main.java.esco.history;

import arc.struct.Seq;
import mindustry.world.Tile;

public class HistoryStack {
    public final Seq<HistoryEntry> stack = new Seq<>();
    public final Tile tile;

    public HistoryStack(Tile tile) {
        this.tile = tile;
    }

    public HistoryStack addEntry(HistoryEntry entry) {
        stack.add(entry);
        return this;
    }

    public int currentSize() {
        return this.stack.size;
    }

    public void removeLast() {
        this.stack.remove(0);
    }
}
