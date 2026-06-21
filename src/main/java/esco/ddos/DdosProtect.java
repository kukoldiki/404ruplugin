package main.java.esco.ddos;

import arc.struct.Seq;
import arc.util.Timer;
import main.java.esco.accessLevel.AccessLevel;
import mindustry.gen.Call;
import mindustry.gen.Player;
import arc.util.Log;

import static main.java.esco.database.DatabaseConnector.getAccessLevel;
import static main.java.esco.database.DatabaseConnector.getPlayerDiscord;
import static main.java.esco.database.DatabaseConnector.getPlayerId;
import static main.java.esco.discord.Bot.sendServerMessage;
/**Простейшая защита.*/
public class DdosProtect {
    int panicsStart=3; // число с которого начнется паника

    Timer.Task clear; // таск чистит зашедших игроков каждую секунду
    Timer.Task nopanic; // таск выключает паник мод через X минут после его вклю.чения

    final Seq<Player> joinedNow = new Seq<>();

    boolean panicMode = false; // включен ли режим паники

    public DdosProtect() {
        createTask();
    }

    public DdosProtect(int panicStart) {
        this.panicsStart=panicStart;
        createTask();
    }

    /**@return Возвращает true если можно продолжать работу с игроком, в ином случае игрока кикнуло.*/
    public boolean apply(Player player) {
        joinedNow.add(player);
        if(panicMode) {
            Log.debug("Panic mode");
            return kick(player);
        }
        if(joinedNow.size>=panicsStart) {
            Log.debug("joinedNow > start");
            return kick(player);
        }
        return true;
    }

    private void createTask() {
        if(clear!=null) {
            clear.cancel();
        }
        clear=Timer.schedule(()->{
            joinedNow.clear();
        }, 0, 1.5f);
    }

    public Seq<Player> getJoinedNow() {
        return joinedNow.copy();
    }

    public void panic() {
        nopanic=Timer.schedule(this::stopPanic, 5*60);
        this.panicMode=true;
        Call.sendMessage("[scarlet]Panic mode enabled, no new connections.");
        sendServerMessage("Panic mode enabled, no new connections.");
    }

    /*Делаю так ибо лямбда не даст нормально поменять бул.*/
    public void stopPanic() {
        this.panicMode=false;
        Call.sendMessage("Panic mode disabled.");
        sendServerMessage("Panic mode disabled, try to connect now.");
    }

    public void stopPanicNow() {
        this.stopPanic();
        this.nopanic.cancel();
    }

    public boolean isPanic() {
        return this.panicMode;
    }

    private boolean kick(Player player) {
        if (getAccessLevel(player) == AccessLevel.player && getPlayerDiscord(getPlayerId(player)) > 0) {
            player.kick("Server in panic mode, try to connect in few minutes.");
            if(!panicMode)
                panic();
            Log.debug("Panic");
            return false;
        }
        Log.debug("No panic");
        return true;
    }
}
