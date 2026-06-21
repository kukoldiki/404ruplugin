package main.java.esco.discord;

import arc.func.Cons2;
import main.java.esco.accessLevel.AccessLevel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class CommandData {
    public String name, desc;
    public final AccessLevel level;
    public Cons2<MessageReceivedEvent, String[]> call;
    public boolean hidden = false;

    public CommandData(String name, String desc, AccessLevel level, Cons2<MessageReceivedEvent, String[]> call) {
        this.name = name;
        this.desc = desc;
        this.level = level;
        this.call = call;
    }

    public CommandData h() {
        this.hidden = true;
        return this;
    }
}
