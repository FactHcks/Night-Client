package me.night.waypoint;

/** A saved spot in a world. */
public record Waypoint(String name, int x, int y, int z, String dimension, int color) {
}
