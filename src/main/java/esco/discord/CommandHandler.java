package main.java.esco.discord;

import arc.func.Cons2;
import arc.struct.Seq;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.database.DatabaseConnector;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;

import static main.java.esco.PVars.botPrefix;

public class CommandHandler {
    public static Seq<CommandData> commands = new Seq<>();
    public static Seq<CommandData> broadcastCommands = new Seq<>();
    public static void handleEvent(MessageReceivedEvent event) {
        User author = event.getAuthor();
        MessageChannelUnion channel = event.getChannel();
        Message message = event.getMessage();
        String content = message.getContentDisplay();

        if(content.isEmpty())
            return;

        if(content.startsWith(botPrefix)) {
            String[] args = content.split(" ");
            String name = args[0].replace(botPrefix, "");
            CommandData command = commands.find(c->c.name.equals(name));

            if(command == null) {
                message.reply("Command not found!").queue();
                return;
            }

            if(DatabaseConnector.getAccessLevelByDiscord(author.getId()).hasSufficientLevel(command.level)) {
                command.call.get(event, Arrays.copyOfRange(args, 1, args.length));
            } else {
                message.reply("No access").queue();
            }
        } else if(content.startsWith("bc.")) {
            String[] args = content.split(" ");
            String name = args[0].replace("bc", "");
            CommandData command = broadcastCommands.find(c->c.name.equals(name));

            if(command == null) {
                return;
            }

            if(DatabaseConnector.getAccessLevelByDiscord(author.getId()).hasSufficientLevel(AccessLevel.hadmin)) {
                command.call.get(event, Arrays.copyOfRange(args, 1, args.length));
            } else {
                message.reply("No access").queue();
            }
        }
    }

    public static void registerCommand(String name, String desc, AccessLevel level, Cons2<MessageReceivedEvent, String[]> call) {
        commands.add(new CommandData(name, desc, level, call));
    }

    public static void registerCommand(String name, String desc, Cons2<MessageReceivedEvent, String[]> call) {
        registerCommand(name, desc, AccessLevel.player, call);
    }

    public static void registerCommand(String name, AccessLevel level, Cons2<MessageReceivedEvent, String[]> call) {
        registerCommand(name, "", level, call);
    }

    public static void registerCommandh(String name, Cons2<MessageReceivedEvent, String[]> call) {
        commands.add(new CommandData(name, "", AccessLevel.player, call).h());
    }

    public static void registerCommand(String name, Cons2<MessageReceivedEvent, String[]> call) {
        registerCommand(name, "", AccessLevel.player, call);
    }

    public static void registerBroadcastCommand(String name, String desc, Cons2<MessageReceivedEvent, String[]> call) {
        broadcastCommands.add(new CommandData(name, desc, AccessLevel.hadmin, call));
    }
}
