package main.java.esco.discord;

import arc.graphics.Color;
import arc.util.Log;
import mindustry.gen.Call;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.text.MessageFormat;

import static main.java.esco.PVars.botPrefix;

public class MessageListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if(!event.isFromGuild())
            return;
        User author = event.getAuthor();
        Message message = event.getMessage();
        if(author.isBot() || author.isSystem() || message.isWebhookMessage())
            return;
        Member member = event.getMember();
        MessageChannelUnion channel = event.getChannel();
        String content = message.getContentDisplay();

        if(channel.getId().equals(channel) && !content.startsWith(botPrefix)) {
            String username = member.getEffectiveName();
            Log.info("@: @", username, content);
            String colorHex = new Color(member.getColors().getPrimaryRaw()).toString();
            String mindustryMessage = MessageFormat.format(
                    "[blue]\uE80D[tan][[[#{0}]{1}[tan]][white]: {2}",
                    colorHex,
                    username,
                    content
            );
            Call.sendMessage(mindustryMessage);
        }
        CommandHandler.handleEvent(event);
    }
}
