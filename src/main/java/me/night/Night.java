package me.night;

import me.night.command.CommandManager;
import me.night.config.ConfigManager;
import me.night.event.EventManager;
import me.night.event.events.TickEvent;
import me.night.module.ModuleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Night implements ModInitializer {
    public static final String MOD_ID = "night";
    public static final String CLIENT_NAME = "Night Client";
    /** Version from fabric.mod.json (set by the build / release tag). */
    public static final String CLIENT_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("night").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Night INSTANCE;

    private EventManager eventManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        me.night.render.UiPipeline.init();
        if (System.getProperty("night.devshot") != null || System.getenv("NIGHT_DEVSHOT") != null) me.night.dev.DevShots.init();
        LOGGER.info("Initializing {} v{}", CLIENT_NAME, CLIENT_VERSION);

        this.eventManager = new EventManager();
        this.moduleManager = new ModuleManager();
        this.commandManager = new CommandManager();

        this.eventManager.register(this.moduleManager);

        ConfigManager.loadConfig("default");

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            this.eventManager.post(new TickEvent(TickEvent.Phase.PRE));
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.autoSave());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            this.eventManager.post(new TickEvent(TickEvent.Phase.POST));
        });

        LOGGER.info("{} initialized successfully with {} modules!", CLIENT_NAME, this.moduleManager.getModules().size());
    }

    public EventManager getEventManager() {
        return this.eventManager;
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
