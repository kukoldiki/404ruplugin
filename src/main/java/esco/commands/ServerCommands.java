package main.java.esco.commands;

import arc.Core;
import arc.util.CommandHandler;
import arc.util.Log;
import main.java.esco.PVars;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import main.java.esco.database.DatabaseConnector;

import static main.java.esco.ConfigLoader.loadConfig;
import static main.java.esco.PVars.executor;
import static main.java.esco.PVars.executorThread;
import static main.java.esco.discord.Bot.sendServerMessage;

public class ServerCommands {
  public ServerCommands(CommandHandler handler) {
    handler.register("restart", "restart server", args -> {
      Core.app.post(() -> {
        PVars.whitelistEnabled = true;
        //Groups.player.each(player -> Call.connect(player.con, "121.127.37.17", 6571));
        //Groups.player.each(player -> Call.connect(player.con, "95.215.56.128", 6571));
        Groups.player.each(player -> Call.connect(player.con, "194.164.245.98", 6567));
        //executor.shutdown();
        Vars.net.dispose();
        Core.app.exit();
      });
    });
    handler.register("reload-config", "Reload server config.", args -> {
      loadConfig();
      Log.info("done!");
    });
    handler.register("say", "<text...>", "Say something.", args -> {
      Call.sendMessage("[scarlet][Server]:[white] " + args[0]);
      sendServerMessage("[Server]: " + args[0]);
      Log.info("[Server]: " + args[0]);
    });
    handler.register("wl", "", "toggle whitelist", args -> {
      PVars.whitelistEnabled = !PVars.whitelistEnabled;
      Log.info(PVars.whitelistEnabled ? "Enabled" : "Disabled");
    });
    handler.register("executor", "<type>", "check executor info", (a)->{
      switch (a[0].toLowerCase()) {
        case "busy":
          Log.info("By terminated @", executor.isTerminated());
          Log.info("By shutdowned @", executor.isShutdown());
          Log.info("By thread @", executorThread.getState());
          break;
        case "thread":
          Log.info("Task sent");
          executor.execute(()->{
            Thread thread = Thread.currentThread();
            Log.info("Name @", thread.getName());
            Log.info("ID @", thread.getId());
            Log.info("Priority @", thread.getPriority());
          });
          break;
        default:
          Log.err("Unkown type @. Expected 'busy' or 'thread'", a[0]);
          break;
      }
    });
    handler.register("dbpool", "", "check db pool info", (a) -> {
      DatabaseConnector.logMetrics();
    });
    handler.register("sqld", "<command>", "Check sql debug info", (a)->{
      switch (a[0].toLowerCase()) {
        case "on":
          DatabaseConnector.timeDebugEnabled = true;
          break;
        case "off":
          DatabaseConnector.timeDebugEnabled = false;
          break;
        case "get":
          DatabaseConnector.timeDebug.forEach((k,v) -> {
            Log.info(k + " -- " + v + "ns --");
          });
          break;
        default:
          Log.err("Wrong command! Expected 'off', 'on', or 'get'");
          break;
      }
    });
  }
}
