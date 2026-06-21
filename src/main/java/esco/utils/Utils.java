package main.java.esco.utils;

import arc.files.Fi;
import arc.func.Prov;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.net.Host;
import mindustry.net.NetworkIO;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Random;

import static arc.util.Log.warn;
import static main.java.esco.PVars.*;

public class Utils {
    static Prov<DatagramPacket> packetSupplier = () -> new DatagramPacket(new byte[512], 512);

    public static String getUDPAddress(Player player) {
        try {
            return ((arc.net.Connection) Reflect.get(player.con, "connection")).getRemoteAddressUDP().getAddress().toString().substring(1);
        } catch(Throwable e) {
            return null;
        }
    }

    /**
     * Узнать является ли айпи анонимизатором.
     *
     * @param ipAddress ip Адрес игрока.
     * @return Вернет true/false в зависимости от того, является ли ip анонимизатором.
     */
    public static boolean isProxyOrVPN(String ipAddress) {
        boolean anon;
        try (InputStream stream = new URI(
                "http://vpncache:3000/ip/" + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8))
                .toURL().openStream()) {
            JsonReader JSON = new JsonReader();
            var value = JSON.parse(stream);

            if (!value.getString("status").equals("success")) {
                warn("Fetch failed: " + value.getString("message"));
                return false;
            }

            anon = value.getBoolean("anon");
        } catch (URISyntaxException | IOException e) {
	    warn(e.getMessage());
            return false;
        }
        return anon;
    }


    /**
     * Legacy!
     */
    public static boolean isError(String output) {
        try {
            String errorName = output.substring(0, output.indexOf(' ') - 1);
            Class.forName("org.mozilla.javascript." + errorName);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Убирает пометку фус клиента с конца сообщения
     *
     * @param string сообщение
     * @return сообщение без метки
     */
    public static String stripFoo(String string) {
        StringBuilder var1 = new StringBuilder(string);
        for (int i = string.length() - 1; i >= 0; i--) {
            if (var1.charAt(i) >= 0xf80 && var1.charAt(i) <= 0x107f) var1.deleteCharAt(i);
        }
        return var1.toString();
    }

    /**
     * Выдает набор букв и чисел для разных целей
     *
     * @return Набор букв и чисел в String.
     */
    public static String generateAuthCode(int ch) {
        Random random = new Random();
        StringBuilder authCode = new StringBuilder();
        for (int i = 0; i < ch; i++) {
            int choice = random.nextInt(3);
            if (choice == 0) {
                authCode.append(random.nextInt(10));
            } else if (choice == 1) {
                authCode.append((char) ('A' + random.nextInt(26)));
            } else {
                authCode.append((char) ('a' + random.nextInt(26)));
            }
        }
        Log.debug("generated auth code " + authCode);
        return authCode.toString();
    }

    /**
     * Выдает набор букв и чисел для разных целей
     *
     * @return Набор букв и чисел в String.
     */
    public static String generateAuthCode() {
        Random random = new Random();
        StringBuilder authCode = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int choice = random.nextInt(3);
            if (choice == 0) {
                authCode.append(random.nextInt(10));
            } else if (choice == 1) {
                authCode.append((char) ('A' + random.nextInt(26)));
            } else {
                authCode.append((char) ('a' + random.nextInt(26)));
            }
        }
        Log.debug("generated auth code " + authCode);
        return authCode.toString();
    }

    /**
     * Форматрирование времени в 0d 0h 0m 0s
     *
     * @param time Время в секундах в формате long
     * @return время в выше указанном формате
     */
    public static String formatTime(long time) {
        long days = time / 86400;
        long hours = (time % 86400) / 3600;
        long minutes = (time % 3600) / 60;
        long seconds = time % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Парсит время бана.
     */
    public static long parseTime(String time) {
        if (time.isEmpty() || !Character.isDigit(time.charAt(0)))
            return -1;
        char timeMod = Character.toLowerCase(time.charAt(time.length() - 1)); // last char

        if (Character.isDigit(timeMod)) {
            // minutes
            if (!Strings.canParseInt(time))
                return -1;
            return Long.parseLong(time) * 60;
        }

        time = time.substring(0, time.length() - 1);
        if (!Strings.canParseInt(time))
            return -1;

        long parsed = Long.parseLong(time);
        if (timeMod == 'h')
            return parsed * 60 * 60;
        if (timeMod == 'd')
            return parsed * 60 * 60 * 24;
        if (timeMod == 'w')
            return parsed * 60 * 60 * 24 * 7;
        if (timeMod == 'm')
            return parsed * 60 * 60 * 24 * 30;
        if (timeMod == 'y')
            return parsed * 60 * 60 * 24 * 365;
        return parsed;
    }

    public static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(Fi file) {
        return sha256(file.readBytes());
    }

    public static int getOppositePlayer() {
        Gamemode mode = Gamemode.getGamemode().getOpposite();
        if(mode == Gamemode.unknown)
            return 0;
        try {
            return pingHostImpl(serverIP, mode.port).players;
        } catch (IOException ignore) {
            return 0;
        }
    }

    private static Host pingHostImpl(String address, int port) throws IOException{
        try(DatagramSocket socket = new DatagramSocket()){
            long time = Time.millis();

            socket.send(new DatagramPacket(new byte[]{-2, 1}, 2, InetAddress.getByName(address), port));
            socket.setSoTimeout(2000);

            DatagramPacket packet = packetSupplier.get();
            socket.receive(packet);

            ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
            Host host = NetworkIO.readServerData((int)Time.timeSinceMillis(time), packet.getAddress().getHostAddress(), buffer);
            host.port = port;
            return host;
        }
    }
}
