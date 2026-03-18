package me.bintanq.manager;

import me.bintanq.EasterEventVisantara;

public class EventWindowManager {

    private final EasterEventVisantara plugin;

    public EventWindowManager(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    public boolean isEventActive() {
        return true;
    }
}