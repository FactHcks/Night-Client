package me.night.event.events;

import me.night.event.Event;

public class TickEvent extends Event {
    public enum Phase {
        PRE, POST
    }

    private final Phase phase;

    public TickEvent(Phase phase) {
        this.phase = phase;
    }

    public Phase getPhase() {
        return this.phase;
    }
}
