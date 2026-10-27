package me.night.module;

import me.night.event.EventTarget;
import me.night.event.events.KeyEvent;
import me.night.module.modules.combat.AntiBot;
import me.night.module.modules.combat.Criticals;
import me.night.module.modules.combat.Killaura;
import me.night.module.modules.combat.Teams;
import me.night.module.modules.combat.Velocity;
import me.night.module.modules.movement.Flight;
import me.night.module.modules.movement.Speed;
import me.night.module.modules.movement.Sprint;
import me.night.module.modules.movement.TargetStrafe;
import me.night.module.modules.player.ChestStealer;
import me.night.module.modules.player.DiscordRPC;
import me.night.module.modules.player.FastPlace;
import me.night.module.modules.player.InvManager;
import me.night.module.modules.player.NoFall;
import me.night.module.modules.player.PingSpoof;
import me.night.module.modules.render.ClickGui;
import me.night.module.modules.render.ESP;
import me.night.module.modules.render.TargetIndicator;
import me.night.module.modules.render.Nametags;
import me.night.module.modules.render.HitEffects;
import me.night.module.modules.render.Keystrokes;
import me.night.module.modules.render.MusicPlayer;
import me.night.module.modules.render.Waypoints;
import me.night.module.modules.render.Minimap;
import me.night.module.modules.render.SessionStats;
import me.night.module.modules.render.Animations;
import me.night.module.modules.render.HUD;
import me.night.module.modules.render.TabGUI;
import me.night.module.modules.render.TargetHUD;
import me.night.module.modules.world.BedNuker;
import me.night.module.modules.world.Scaffold;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        // Combat
        register(new Killaura());
        register(new Velocity());
        register(new AntiBot());
        register(new Teams());
        register(new Criticals());

        // Movement
        register(new Sprint());
        register(new Speed());
        register(new Flight());
        register(new TargetStrafe());

        // Render
        register(new ClickGui());
        register(new HUD());
        register(new TabGUI());
        register(new TargetHUD());
        register(new ESP());
        register(new TargetIndicator());
        register(new Nametags());
        register(new HitEffects());
        register(new Keystrokes());
        register(new MusicPlayer());
        register(new Waypoints());
        register(new Minimap());
        register(new SessionStats());
        register(new Animations());

        // Player
        register(new NoFall());
        register(new FastPlace());
        register(new PingSpoof());
        register(new ChestStealer());
        register(new InvManager());
        register(new DiscordRPC());

        // World
        register(new Scaffold());
        register(new BedNuker());
    }

    public void register(Module module) {
        this.modules.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) this.modules.get(clazz);
    }

    public Module getModule(String name) {
        for (Module m : this.modules.values()) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public Collection<Module> getModules() {
        return this.modules.values();
    }

    public List<Module> getModulesByCategory(Category category) {
        return this.modules.values().stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public List<Module> getEnabledModules() {
        return this.modules.values().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.isPress() && event.getKey() > 0) {
            for (Module module : this.modules.values()) {
                if (module.getKey() == event.getKey()) {
                    module.toggle();
                }
            }
        }
    }
}
