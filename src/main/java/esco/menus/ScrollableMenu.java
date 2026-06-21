package main.java.esco.menus;

import arc.struct.Seq;
import arc.util.Strings;
import mindustry.gen.Player;


public class ScrollableMenu {
    private Seq<String> pages = new Seq<>();
    private String title;
    private Player player;

    public ScrollableMenu(String title) {
        this.title = title;
    }

    public ScrollableMenu(String title, Seq<String> pages) {
        this.title = title;
        this.pages = pages;
    }

    public void show(Player target) {
        show(target, 0);
    }

    public void show(Player target, int page) {
        final int pageIndex;
        if (page < 0) {
            pageIndex = pages.size - 1;
        } else if (page >= pages.size) {
            pageIndex = 0;
        } else {
            pageIndex = page;
        }

        MenuBuilder menu = new MenuBuilder(title, pages.get(pageIndex));
        menu.add("<", pl -> {
            show(target, pageIndex - 1);
        });
        menu.add(Strings.format("@/@", pageIndex + 1, pages.size), pl -> {
        });
        menu.add(">", pl -> {
            show(target, pageIndex + 1);
        });
        menu.row();
        menu.add("OK", pl -> {
        });
        menu.show(target);
    }

    // Геттеры и сеттеры
    public String getTitle() {
        return title;
    }

    public ScrollableMenu setTitle(String title) {
        this.title = title;
        return this;
    }

    public Seq<String> getPages() {
        return pages;
    }

    public ScrollableMenu addPage(String page) {
        pages.add(page);
        return this;
    }
}
