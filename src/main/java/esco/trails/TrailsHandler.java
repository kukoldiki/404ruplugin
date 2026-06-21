package main.java.esco.trails;

import arc.Core;
import arc.graphics.Color;
import arc.util.Timer;
import main.java.esco.bundle.Bundle;
import mindustry.entities.Effect;
import mindustry.gen.Call;
import mindustry.gen.Player;

import java.util.HashMap;

import static main.java.esco.PVars.chandler;
import static main.java.esco.database.DatabaseConnector.getPlayerData;

// TODO. Сделайте нормальные трейлы.
public class TrailsHandler {
    public static final HashMap<Player, Trail> TrailedPlayers = new HashMap<>();
    private static Timer.Task updateTask;

    public static void init() {
        /*Events.run(EventType.Trigger.update, ()->{
            TrailedPlayers.forEach((p, t)->{
                if(!p.con.isConnected())
                    TrailedPlayers.remove(p);
                else
                    Call.effect(t.effect, p.unit().x, p.unit().y, 1, t.color);
            });
        });*/
        /*
         * Пожалуйста, не ставьте слишком маленькое значение таймера
         * а иначе просядет тпс.
         * */
        updateTask = Timer.schedule(() -> {
            Core.app.post(() -> {
                TrailedPlayers.forEach((p, t) -> {
                    effect(t.effect, p.unit().x, p.unit().y, t.color);
                });
            });
        }, 0, 0.1f);
        chandler.addCommand("trail", "[trail-name] [color]", (args, player) -> {
            if (args.length == 0 && TrailedPlayers.containsKey(player)) {
                player.sendMessage(Bundle.get("esco.commands.trail.disabledcuznoargs", player.locale));
                TrailedPlayers.remove(player);
                return;
            }
            if (args.length < 1) {
                TrailedPlayers.remove(player);
                for (Trails t : Trails.values())
                    player.sendMessage("[tan][" + t.ordinal() + "] [#" + t.trail.color.toString() + "]" + t.trail.name + " [tan]lvl " + t.trail.level);
            } else {
                Trails trails = Trails.parseTrail(args[0]);
                if (trails == null) {
                    player.sendMessage(Bundle.get("esco.commands.trail.unkowntrail", player.locale));
                    return;
                }
                Trail trail = trails.trail;
                if (args.length == 2) {
                    if (args[1].replace("#", "").length() == 6) {
                        trail = trail.copy();
                        trail.color = Color.valueOf(Color.gray, args[1]);
                    }
                }
                final Trail trailf = trail;
                getPlayerData(player).ifPresent(plr -> {
                    if (plr.getLevel() >= trailf.level) {
                        TrailedPlayers.remove(player);
                        TrailedPlayers.put(player, trailf);
                        player.sendMessage(Bundle.get("esco.commands.trail.successful", player.locale));
                    } else {
                        player.sendMessage(Bundle.get("esco.commands.trail.nolevel", player.locale) + trailf.level);
                    }
                });
            }
        });
    }

    public static synchronized void effect(Effect e, float x, float y, Color color) {
        Call.effect(e, x, y, 1, color);
    }
}
