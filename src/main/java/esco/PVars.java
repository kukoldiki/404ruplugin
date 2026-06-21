package main.java.esco;

import arc.struct.IntMap;
import main.java.esco.commandRegister.CommandRegister;
import main.java.esco.ddos.DdosProtect;
import main.java.esco.ddos.Shiza;
import main.java.esco.history.History;
import mindustry.mod.Mods;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PVars {
    public static final String IPAPI_URL = "http://ip-api.com/json/";
    public static final String IPAPI_POSTFIX = "?fields=17023488";
    public static final ExecutorService executor = Executors.newSingleThreadExecutor();
    public static volatile Thread executorThread;
    public static Shiza shiza;
    public static History history = new History();
    public static Instant startTime;
    public static IntMap<String> SSUsers = new IntMap<>(8);
    //public static GatewayDiscordClient gateway;
    //public static Mono<Void> login;
    public static HashMap<String, String> verifycodes = new HashMap<>();
    public static HashMap<String, Integer> usidverifycodes = new HashMap<>();
    public static CommandRegister chandler;
    public static boolean whitelistEnabled = false;
    public static DdosProtect ddosProtection = new DdosProtect();
    //public static Snowflake channelToSnowflake;
    public static TextChannel serverChannel;
    public static TextChannel alertsChannel;
    public static Guild serverGuild;
    public static Role linkedRole;
    public static String pluginVersion;
    public static Mods.LoadedMod mod;
    public static final Instant serverStart = Instant.now();
    public static boolean useDiscordProxy = true;
    
    public static String botToken;
    public static String botPrefix;
    public static long escoGuild;
    public static String channel;
    public static long linkedid = 0L;
    public static String headNotifyID;
    public static String discordLink;
    public static String vpnapiioToken;
    public static String serverIP;
}
