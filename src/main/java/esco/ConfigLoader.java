package main.java.esco;

import arc.util.Log;
import main.java.esco.utils.Gamemode;

public class ConfigLoader {
    /**
     * Загружает конфиг сервера.
     */
    public static void loadConfig() {
        try {
			PVars.botToken = loadEnvVar("BOT_TOKEN");
			PVars.escoGuild = Long.parseLong(loadEnvVar("GUILD_ID"));
			PVars.channel = loadEnvVar("CHAT_CHANNEL");
			PVars.linkedid = Long.parseLong(loadEnvVar("LINKED_ROLE_ID"));
			PVars.headNotifyID = loadEnvVar("HEAD_NOTIFY_CHANNEL");
			PVars.discordLink = loadEnvVar("DISCORD_INVITE");
			PVars.vpnapiioToken = loadEnvVar("VPNAPIIO_TOKEN");
			PVars.botPrefix = Gamemode.getGamemode(loadEnvVar("GAMEMODE_NAME")).prefix;
			PVars.serverIP = loadEnvVar("SERVER_IP");
            Log.info("[EscoPlugin] Loaded config.");
        } catch (Exception e) {
            Log.warn("[EscoPlugin] Cannont read config!");
			Log.err(e.getMessage());
			System.exit(2);	
        }
    }
    
    private static String loadEnvVar(String varname) {
		String value = System.getenv(varname);
		if(value == null) {
			Log.err("[EscoPlugin] Failed to load enviroment variable " + varname + "!");
			System.exit(2);	
		}
		return value;
	}
}
