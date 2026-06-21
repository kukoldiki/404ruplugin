package main.java.esco.events;

public class EventManager {
    public EventManager() {
        ConnectEvents.load();
        LeaveEvents.load();
        ChatEvents.load();
        ServerEvents.load();
        MapEvents.load();
        ArcEvents.load();
    }
}
