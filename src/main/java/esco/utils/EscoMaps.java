package main.java.esco.utils;

import arc.graphics.Pixmap;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.Tiles;

import java.util.concurrent.ConcurrentHashMap;

import static mindustry.io.MapIO.colorFor;

/*
https://github.com/Anuken/Mindustry/blob/master/core/src/mindustry/maps/Maps.java
maps.customMaps(); -> Seq(map)
https://github.com/Anuken/Mindustry/blob/master/server/src/mindustry/server/ServerControl.java#L415
*/
public class EscoMaps {
    static {
        Vars.maps.setMapProvider((mode, prev) -> {
            ObjectIntMap<Map> votesMap = new ObjectIntMap<>();
            PlayerStatus.each(ps -> {
                if(ps.votedMap == null)
                    return;
                votesMap.put(ps.votedMap, votesMap.get(ps.votedMap, 0) + 1);
                ps.votedMap = null;
            });
            if(votesMap.isEmpty()) {
                Seq<Map> maps = Vars.maps.customMaps().copy();
                maps.remove(prev);
                return maps.random();
            }
            Seq<ObjectIntMap.Entry<Map>> votes = votesMap.entries().toArray();
            Map selectedMap = votes.max(e -> e.value).key;
            if(selectedMap == null) {
                Seq<Map> maps = Vars.maps.customMaps().copy();
                maps.remove(prev);
                return maps.random();
            }
            return selectedMap;
        });
    }

    /**
     * Получить все доступные кастомные карты сервера
     *
     * @return Кастомные карты сервера
     */
    public static Seq<Map> getMaps() {
        if (!Vars.maps.all().isEmpty()) {
            Seq<Map> all = new Seq<>();
            all.addAll(Vars.maps.customMaps());
            // all.addAll(maps.defaultMaps());
            return all;
        }
        return null;
    }

    /**
     * Функция получения списка карт в виде StringBuilder для дискорда.
     *
     * @return Карты в StringBuilder в формате mapName: file x*y
     */
    public static StringBuilder getMapsForDiscord() {
        StringBuilder sb = new StringBuilder();
        Seq<Map> all = getMaps();
        if (all == null) {
            sb.append("No maps to show.");
        } else {
            sb.append("Maps:\n");
            for (Map map : all) {
                String mapName = map.plainName().replace(' ', '_');
                if (map.custom) {
                    sb.append(mapName + " (" + map.file.name() + "): Custom / " + map.width + "x" + map.height + "\n");
                } else {
                    sb.append(mapName + ": Default / " + map.width + "x" + map.height + "\n");
                }
            }
        }
        return sb;
    }

    /**
     * Получить рендер карт с цветом предмета в конфиге блока(сортер и т.д.)
     *
     * @param tiles Тайлы карты
     * @return Pixmap для сохранения в файл
     */
    public static Pixmap generatePreview(Tiles tiles) {
        Pixmap pixmap = new Pixmap(tiles.width, tiles.height);
        for (int x = 0; x < pixmap.width; x++) {
            for (int y = 0; y < pixmap.height; y++) {
                Tile tile = tiles.getn(x, y);
                if (tile.build != null) {
                    if (tile.build.config() instanceof Item item) {
                        pixmap.set(x, pixmap.height - 1 - y, item.color.rgba());
                        item = null;
                    } else {
                        pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                    }
                } else {
                    pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                }
            }
        }
        return pixmap;
    }
}
