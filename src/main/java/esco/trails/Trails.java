package main.java.esco.trails;

import arc.graphics.Color;
import mindustry.content.Fx;


// TODO. Сделайте нормальные трейлы.
public enum Trails {
    mine(new Trail("mine", Fx.mine, 5, Color.sky)),
    healWave(new Trail("healWave", Fx.healWave, 10, Color.lime)),
    explosion(new Trail("explosion", Fx.plasticExplosionFlak, 18, Color.orange)),
    smoke(new Trail("smoke", Fx.smoke, 15, Color.gray)),
    smokeCloud(new Trail("smokeCloud", Fx.smokeCloud, 22, Color.gray));
    public final Trail trail;

    Trails(Trail trail) {
        this.trail = trail;
    }

    public static Trails parseTrail(String s) {
        for (Trails t : values()) {
            if (t.trail.name.equalsIgnoreCase(s))
                return t;
        }
        return null;
    }
}
