package main.java.esco.events;

import arc.Events;
import arc.util.Strings;
import main.java.esco.utils.PlayerStatus;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;

import static main.java.esco.PVars.*;

public class ArcEvents {
    public static void load() {
        Events.run(EventType.Trigger.update, () -> PlayerStatus.getHistoryUsers().forEach(player -> {
            if (player.con == null) {
                return;
            }
            var tile = Vars.world.tileWorld(player.mouseX, player.mouseY);
            if (tile == null || !history.history.containsKey(tile)) return;
            var stack = history.history.get(tile);
            StringBuilder output = new StringBuilder();
            if (stack != null) {
                for (int i = 0; i < stack.stack.size; i++) {
                    var entry = stack.stack.get(i);
                    if (entry != null) {
                        output.append("[accent][").append(i + 1).append("][] ").append(entry.getMessage()).append("\n");
                    } else break;
                }
            }
            if (output.length() < 2) {
                Call.hideHudText(player.con);
            } else {
                output.append(Strings.format("\n[lightgray][@, @]", tile.x, tile.y));
                Call.setHudText(player.con, output.toString());
            }
        }));
        Events.on(EventType.TapEvent.class, e -> {
            PlayerStatus.get(e.player).handleAction();
        });
    }
}
