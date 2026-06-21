package main.java.esco.utils;

public enum Gamemode {
    hub("Hub", "hub", 0, 0, 0, 7, 6567),
    attack("Attack", "atk", 377, -101, 0, 7, 6568, false, "eattack"),
    survival("Survival", "srv", 137, 0, 7, 7, 6569),
    test("Test", "tst", 0, 0, 0, 7, 6570),
    pvp("PvP", "pvp", 137, -53, 0, 7, 6571),
    sandbox("Sandbox", "snd", 0, 0, 0, 7, 6572),
    tdf("Tower Defence", "tdf", 137, 0, 2, 7, 6573),

    hub8("Hub", "hub8", 0, 0, 0, 8, 6587),
    attack8("Attack", "atk8", 377, -101, 0, 8, 6588, false, "eattack8"),
    survival8("Survival", "srv8", 137, 0, 7, 8, 6589),
    test8("Test", "tst8", 0, 0, 0, 8, 6590),
    pvp8("PvP", "pvp8", 137, -53, 0, 8, 6591),
    sandbox8("Sandbox", "snd8", 0, 0, 0, 8, 6592),
    tdf8("Tower Defence", "tdf8", 137, 0, 2, 8, 6593),

    eattack("Elite Attack", "etk", 377, -101, 0, 7, 6579, true, "attack"),
    eattack8("Attack", "etk8", 377, -101, 0, 8, 6599, true, "attack8"),

    dev("Development", "dev", 2, 3, 5, 7, 6575),
    unknown("Unknown", "unk", 0, 0, 0, 0, 0);

    public final String name;
    public final String prefix;
    public final int winCost;
    public final int loseCost;
    public final int waveCost;
    public final int version;
    public final int port;
    public final boolean separated;
    public final boolean isElite;
    public final String opposite;

    Gamemode(String name, String prefix, int winCost, int loseCost, int waveCost, int version, int port) {
        this.name = name;
        this.prefix = prefix + ".";
        this.winCost = winCost;
        this.loseCost = loseCost;
        this.waveCost = waveCost;
        this.version = version;
        this.port = port;
        this.isElite = false;
        this.opposite = null;
        this.separated = false;
    }

    Gamemode(String name, String prefix, int winCost, int loseCost, int waveCost, int version, int port, boolean isElite, String opposite) {
        this.name = name;
        this.prefix = prefix + ".";
        this.winCost = winCost;
        this.loseCost = loseCost;
        this.waveCost = waveCost;
        this.version = version;
        this.port = port;
        this.isElite = isElite;
        this.opposite = opposite;
        this.separated = true;
    }

    /**
     * Получить текущий режим сервера
     *
     * @return Режим сервера
     */
    public static Gamemode getGamemode() {
        return getGamemode(System.getenv("GAMEMODE_NAME"));
    }

    /**
     * Получить игровой режим по названию
     *
     * @param name Название режима.
     * @return Режим
     */
    public static Gamemode getGamemode(String name) {
        for (Gamemode mode : Gamemode.values()) {
            if (mode.toString().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return Gamemode.unknown;
    }

    public Gamemode getOpposite() {
        if(!separated)
            return Gamemode.unknown;
        return getGamemode(this.opposite);
    }

    public int getVersion() {
        return this.version;
    }
}
