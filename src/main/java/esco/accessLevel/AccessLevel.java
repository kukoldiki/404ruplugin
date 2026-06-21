package main.java.esco.accessLevel;

public enum AccessLevel {
    player("[lightgray][[[blue]P[]]"),
    moderator("[lightgray][[[yellow]M[]]"),
    admin("[lightgray][[[orange]A[]]"),
    hadmin("[lightgray][[[red]H[]]"),
    op("[lightgray][[[purple]O[]]");

    public final String prefix;

    AccessLevel(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Получить лвл по названию
     */
    public static AccessLevel parseLevel(String levelName) {
        for (AccessLevel level : AccessLevel.values()) {
            if (level.toString().equalsIgnoreCase(levelName)) {
                return level;
            }
        }
        return AccessLevel.player;
    }

    /**
     * Проверить имеет ли игрок нужный уровень доступа
     */
    public boolean hasSufficientLevel(AccessLevel requiredLevel) {
        return this.ordinal() >= requiredLevel.ordinal();
    }

    /**
     * Проверить превосходит ли этот уровень другой
     */
    public boolean greatherThan(AccessLevel other) {
        return this.ordinal() > other.ordinal();
    }

    public boolean lessThan(AccessLevel other) {
        return this.ordinal() < other.ordinal();
    }

    /**
     * Получить префикс лвла
     */
    public String getPrefix() {
        return prefix;
    }
}
