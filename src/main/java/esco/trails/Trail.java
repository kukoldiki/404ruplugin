package main.java.esco.trails;

import arc.graphics.Color;
import mindustry.entities.Effect;


public class Trail {
    public final String name;
    public final Effect effect;
    public final int level;
    public Color color;

    Trail(String name, Effect effect, int level, Color color) {
        this.name = name;
        this.effect = effect;
        this.level = level;
        this.color = color;
    }

    public Trail copy() {
        return new Trail(name, effect, level, color);
    }
}
