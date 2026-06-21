package main.java.esco.AI;

import mindustry.content.Blocks;
import mindustry.entities.units.AIController;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class loveAI extends AIController {
    private final Player player;
    private Building heal;
    private boolean ignoreHealing = false;
    private long lastSearchTime = 0;
    private int cantFindHealer = 0;

    public loveAI(Player player) {
        this.player = player;
    }

    @Override
    public void updateMovement() {
        if (this.player.con.isConnected()) {
            float currentHealth = this.unit.health();
            float maxHealth = this.unit.maxHealth();

            if (!ignoreHealing && currentHealth > maxHealth / 2f) {
                this.heal = null;
                cantFindHealer = 0;
                lastSearchTime = 0;
                this.circle(this.player, 100);
                this.unit.plans = this.player.unit().plans();
                this.unit.rotation(this.player.unit().rotation());
                return;
            }

            if (!ignoreHealing) {
                if (this.heal != null) {
                    this.circle(this.heal, 30);
                } else {
                    this.heal = this.getHealTurret();
                    if (this.heal == null) {
                        cantFindHealer++;
                        if (cantFindHealer > 30) {
                            ignoreHealing = true;
                            lastSearchTime = System.currentTimeMillis();
                        }
                    }
                }
            } else {
                this.heal = null;
                this.circle(this.player, 100);
                this.unit.plans = this.player.unit().plans();
                this.unit.rotation(this.player.unit().rotation());
                if (System.currentTimeMillis() - lastSearchTime > 30_000) {
                    ignoreHealing = false;
                    cantFindHealer = 0;
                }
            }
        } else {
            this.unit().kill();
        }
    }

    public Building getHealTurret() {
        return Groups.build.find(b -> {
            return b.team() == this.unit.team() && (b.block == Blocks.repairPoint || b.block == Blocks.repairTurret);
        });
    }
}