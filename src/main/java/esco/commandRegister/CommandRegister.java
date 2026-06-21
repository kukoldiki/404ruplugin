package main.java.esco.commandRegister;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.CommandHandler.CommandRunner;
import arc.util.Log;
import arc.util.Strings;
import main.java.esco.database.DatabaseConnector;
import main.java.esco.accessLevel.AccessLevel;
import main.java.esco.bundle.Bundle;
import main.java.esco.menus.ScrollableMenu;
import main.java.esco.utils.PlayerStatus;
import mindustry.gen.Player;

public class CommandRegister {
    private final String noPermsMessage = "[scarlet]You don't have access to this command.";
    private final CommandHandler commandHandler;
    private final Seq<Command> commands = new Seq<>();

    public CommandRegister(CommandHandler handler) {
        commandHandler = handler;
        addCommand("help", "", AccessLevel.player, (args, player) -> {
            int commandsPerPage = 7;

            AccessLevel playerLevel = DatabaseConnector.getAccessLevel(player);
            int counter = 0;

            StringBuilder sb = new StringBuilder();
            ScrollableMenu menu = new ScrollableMenu("Commands");

            for (int i = 0; i < commands.size; i++) {
                Command command = commands.get(i);
                if (!playerLevel.hasSufficientLevel(command.requiredLevel))
                    continue;

                String descriptionLink = "esco.commands." + command.name + ".description";
                String description = Bundle.get(descriptionLink, player.locale);
                if (description.equals(descriptionLink))
                    description = Bundle.get("esco.commands.nodescription", player.locale);

                sb.append(Strings.format("[white]@ [orange]/@[white] @[lightgray] - @\n",
                        command.requiredLevel.getPrefix(),
                        command.name,
                        command.args,
                        description));
                counter++;
                if (counter >= commandsPerPage) {
                    counter = 0;
                    menu.addPage(sb.toString());
                    sb.setLength(0);
                }
            }
            if (!sb.isEmpty())
                menu.addPage(sb.toString());

            menu.show(player);
        });

        addCommand("chelp", "[page]", AccessLevel.player, (args, player) -> {
            if (args.length > 0 && !Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]\"page\" must be a number.");
                return;
            }
            int commandsPerPage = 9;
            int page = args.length > 0 ? Strings.parseInt(args[0]) - 1 : 0;

            AccessLevel playerLevel = DatabaseConnector.getAccessLevel(player);
            int availableCommands = 0;

            StringBuilder result = new StringBuilder();

            for (int i = 0; i < commands.size; i++) {
                Command command = commands.get(i);
                if (!playerLevel.hasSufficientLevel(command.requiredLevel))
                    continue;
                if (availableCommands >= page * commandsPerPage && availableCommands < (page + 1) * commandsPerPage) {
                    String descriptionLink = "esco.commands." + command.name + ".description";
                    String description = Bundle.get(descriptionLink, player.locale);
                    if (description.equals(descriptionLink))
                        description = Bundle.get("esco.commands.nodescription", player.locale);

                    result.append(command.requiredLevel.getPrefix())
                            .append(" [orange]/")
                            .append(command.name)
                            .append("[white] ")
                            .append(command.args)
                            .append("[lightgray] - ")
                            .append(description)
                            .append("\n");
                }
                availableCommands++;
            }

            int availablePages = (int) Math.ceil((float) availableCommands / commandsPerPage);

            if (page * commandsPerPage >= availableCommands || page < 0) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + availablePages + "[scarlet].");
                return;
            }

            result.insert(0, Strings.format("[orange]-- Commands Page[lightgray] @[gray]/[]@[orange] --\n", page + 1, availablePages));

            player.sendMessage(result.toString());
        });
        addVanilaCommands();
    }

    public Seq<Command> getCommands() {
        return commands;
    }

    /**
     * Зарегестрировать команду
     */
    public void addCommand(String name, String args, CommandRunner<Player> runner) {
        addCommand(name, args, AccessLevel.player, runner);
    }

    /**
     * Зарегестрировать команду для людей с определенным уровнем доступа
     */
    public void addCommand(String name, String args, AccessLevel requiredLevel, CommandRunner<Player> runner) {
        commandHandler.<Player>register(name, args, name, (arguments, player) -> {
            if (!DatabaseConnector.getAccessLevel(player).hasSufficientLevel(requiredLevel)) {
                player.sendMessage(noPermsMessage);
                return;
            }
            if(!PlayerStatus.existsFor(player))
                return;
            runner.accept(arguments, player);
        });
        Command command = new Command(name, args, requiredLevel);
        commands.add(command);

        String descriptionLink = "esco.commands." + command.name + ".description";
        String description = Bundle.get(descriptionLink, "en");
        if (description.equals(descriptionLink))
            Log.warn("No description found for command " + command.name);
    }

    /**
     * Зарегестрировать ванильные команды
     */
    private void addVanilaCommands() {
        commands.add(new Command("sync", "", AccessLevel.player));
    }

    public record Command(String name, String args, AccessLevel requiredLevel) {
    }
}
