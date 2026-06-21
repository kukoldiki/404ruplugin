package main.java.esco.menus;

import arc.Events;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Grely - в душе не чаю как оно работает.
 */
public class MenuBuilder {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
    private static final ObjectMap<Integer, MenuBuilder> ACTIVE_MENUS = new ObjectMap<>();

    static {
        Events.on(EventType.MenuOptionChooseEvent.class, event -> {
            MenuBuilder menu = ACTIVE_MENUS.get(event.menuId);
            if (menu == null || !menu.player.equals(event.player)) return;

            menu.handleEvent(event.option);
            ACTIVE_MENUS.remove(event.menuId);
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            ACTIVE_MENUS.each((id, menu) -> {
                if (menu.player == event.player) {
                    ACTIVE_MENUS.remove(id);
                }
            });
        });
    }

    private final Seq<Seq<String>> options = new Seq<>();
    private final Seq<Cons<Player>> handlers = new Seq<>();
    private String title;
    private String message;
    private Player player;
    private Cons<Player> closeHandler;
    private int menuId;

    public MenuBuilder(String title, String message) {
        this.title = title;
        this.message = message;
    }


    public MenuBuilder add(String text, Cons<Player> handler) {
        if (options.isEmpty()) options.add(new Seq<String>());

        options.peek().add(text);
        handlers.add(handler);
        return this;
    }

    public MenuBuilder row() {
        if (!options.isEmpty() && options.peek().isEmpty()) {
            return this;
        }
        options.add(new Seq<String>());
        return this;
    }

    private void validateHandlers() {
        int totalOptions = options.sum(row -> row.size);
        if (handlers.size != totalOptions) {
            throw new IllegalStateException("Menu options and handlers count mismatch");
        }
    }


    public void show(Player target) {
        this.player = target;
        this.menuId = ID_GENERATOR.incrementAndGet();

        cleanupEmptyRows();
        validateHandlers();

        ACTIVE_MENUS.put(menuId, this);
        Call.menu(target.con, menuId, title, message, buildOptionsArray());
    }

    private void handleEvent(int option) {
        try {
            if (option == -1) {
                if (closeHandler != null) closeHandler.get(player);
            } else if (option >= 0 && option < handlers.size) {
                handlers.get(option).get(player);
            }
        } catch (Exception e) {
            Log.err("Menu handling error", e);
        }
    }

    private String[][] buildOptionsArray() {
        //return options.map(Seq::toArray).toArray(String[][].class);
        String[][] optionsArr = new String[options.size][];
        for (int i = 0; i < options.size; i++) {
            Seq<String> optRow = options.get(i);
            optionsArr[i] = new String[optRow.size];
            for (int j = 0; j < optRow.size; j++) {
                optionsArr[i][j] = optRow.get(j);
            }
        }
        return optionsArr;
    }

    private void cleanupEmptyRows() {
        options.removeAll(Seq::isEmpty);
    }

    public MenuBuilder onClose(Cons<Player> closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    // Геттеры и сеттеры
    public String getTitle() {
        return title;
    }

    public MenuBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public MenuBuilder setMessage(String message) {
        this.message = message;
        return this;
    }
}
