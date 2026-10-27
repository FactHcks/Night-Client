package me.night.dev;

import me.night.Night;
import me.night.alt.AltManager;
import me.night.alt.AltManagerScreen;
import me.night.module.Module;
import me.night.module.modules.combat.Killaura;
import me.night.module.modules.render.ClickGui;
import me.night.module.modules.render.ESP;
import me.night.module.modules.render.Nametags;
import me.night.property.properties.ModeProperty;
import me.night.ui.clickgui.ModernClickGuiScreen;
import me.night.ui.hud.HudEditorScreen;
import me.night.ui.menu.NightMainMenu;
import me.night.util.RotationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * Development helper: start the game with NIGHT_DEVSHOT=1 (or -Dnight.devshot=true) and it walks
 * through the menus, HUD, combat visuals, ClickGUI pages and HUD editor, saving a screenshot of
 * each, then quits. Screenshots land in run/screenshots.
 */
public final class DevShots {
    private static int step = 0;
    private static int ticks = 0;

    private DevShots() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
    }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "devshot-" + name + ".png", mc.getFramebuffer(), 1,
                msg -> Night.LOGGER.info("[devshot] {}", msg.getString()));
    }

    private static Module module(String name) {
        return Night.INSTANCE.getModuleManager().getModule(name);
    }

    private static void go(int next) {
        step = next;
        ticks = 0;
    }

    private static void setupWorld(MinecraftClient mc) {
        MinecraftServer server = mc.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(mc.player.getUuid());
            if (sp == null) return;
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 95, 1));
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 47, 0));
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 12, 2));
            sp.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            sp.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
            legs.setDamage(140);
            sp.equipStack(EquipmentSlot.LEGS, legs);
            sp.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            sp.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));

            Scoreboard sb = server.getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective("night");
            if (obj == null) {
                obj = sb.addObjective("night", ScoreboardCriterion.DUMMY, Text.literal("§d§lNIGHT §fSMP"),
                        ScoreboardCriterion.RenderType.INTEGER, true, null);
            }
            sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
            String[] lines = {"§7Server: §fplay.night.gg", "§r", "§7Kills: §a12", "§7Deaths: §c3",
                    "§7Coins: §6§l1,240", "§r§r", "§dnight.gg"};
            for (int i = 0; i < lines.length; i++) sb.getOrCreateScore(ScoreHolder.fromName(lines[i]), obj).setScore(lines.length - i);

            ServerWorld world = sp.getEntityWorld();
            PigEntity pig = EntityType.PIG.create(world, SpawnReason.COMMAND);
            if (pig != null) {
                Vec3d look = Vec3d.fromPolar(0, sp.getYaw());
                pig.refreshPositionAndAngles(sp.getX() + look.x * 2.6, sp.getY(), sp.getZ() + look.z * 2.6, sp.getYaw() + 140, 0);
                pig.setAiDisabled(true);
                world.spawnEntity(pig);
            }
        });
        mc.inGameHud.getChatHud().addMessage(Text.literal("§d[Night] §fWelcome back!"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("§7Steve: §fgg that was close"));
        mc.inGameHud.getChatHud().addMessage(Text.literal("§aYou earned §6+25 coins"));
    }

    private static Entity findPig(MinecraftClient mc) {
        Entity best = null;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PigEntity && (best == null || mc.player.distanceTo(e) < mc.player.distanceTo(best))) best = e;
        }
        return best;
    }

    // ---- simple FPS benchmark (vsync off, uncapped)
    private static final String[] BENCH = {"warmup", "everything on", "blur off", "client visuals off", "everything on", "blur off", "client visuals off", "glow esp on"};
    private static int benchPhase = 0;
    private static long benchFps = 0;
    private static int benchSamples = 0;

    private static void setBlur(boolean on) {
        for (String n : new String[]{"HUD", "TargetHUD", "Nametags"}) {
            for (var p : module(n).getProperties()) {
                if (p.getName().equals("Blur") && p instanceof me.night.property.properties.BooleanProperty bp) bp.setValue(on);
            }
        }
    }

    private static void benchSetup(MinecraftClient mc, int phase) {
        String name = BENCH[phase];
        setBlur(!name.equals("blur off"));
        boolean visuals = !name.equals("client visuals off");
        for (String n : new String[]{"HUD", "TabGUI", "TargetHUD", "Keystrokes", "TargetIndicator", "HitEffects"}) {
            module(n).setEnabled(visuals);
        }
        Module esp = module("ESP");
        if (name.equals("glow esp on")) {
            ESP.devIncludeSelf = false;
            for (var p : esp.getProperties()) if (p instanceof ModeProperty mp && mp.getName().equals("Mode")) mp.setValue("Glow");
            esp.setEnabled(true);
        } else {
            esp.setEnabled(false);
        }
    }

    private static void tick(MinecraftClient mc) {
        ticks++;
        switch (step) {
            case 0 -> { // main menu
                if (!(mc.currentScreen instanceof NightMainMenu)) ticks = 0;
                else if (ticks > 70) { shot(mc, "01-menu"); go(1); }
            }
            case 1 -> { // alt manager
                if (ticks == 1) {
                    AltManager.add("NightUser");
                    AltManager.add("ShadowFox417");
                    mc.setScreen(new AltManagerScreen(mc.currentScreen));
                }
                if (ticks > 25) { shot(mc, "02-accounts"); mc.setScreen(new NightMainMenu()); go(2); }
            }
            case 2 -> { // start a world
                if (ticks == 5) {
                    String level = "Night_Dev_World";
                    if (mc.getLevelStorage().levelExists(level)) {
                        mc.createIntegratedServerLoader().start(level, () -> mc.setScreen(new NightMainMenu()));
                    } else {
                        mc.createIntegratedServerLoader().createAndStart(level, MinecraftServer.DEMO_LEVEL_INFO,
                                GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, new NightMainMenu());
                    }
                    go(3);
                }
            }
            case 3 -> { // HUD + combat visuals
                if (mc.player == null || mc.currentScreen != null) { ticks = 0; return; }
                if (ticks == 30) {
                    mc.player.setPitch(18);
                    for (String n : new String[]{"Sprint", "Velocity", "NoFall", "Keystrokes", "TargetIndicator", "HitEffects",
                            "Animations", "Minimap", "Waypoints", "MusicPlayer"}) {
                        Module m = module(n);
                        if (m != null && !m.isEnabled()) m.setEnabled(true);
                    }
                    setupWorld(mc);
                    me.night.waypoint.WaypointManager.add("Base", mc.player.getBlockX() + 12, mc.player.getBlockY(), mc.player.getBlockZ() - 6);
                    me.night.waypoint.WaypointManager.add("Mine", mc.player.getBlockX() - 30, mc.player.getBlockY(), mc.player.getBlockZ() + 40);
                }
                if (ticks == 60) Killaura.currentTarget = (net.minecraft.entity.LivingEntity) findPig(mc);
                if (ticks == 80) {
                    Entity pig = findPig(mc);
                    if (pig != null) mc.interactionManager.attackEntity(mc.player, pig);
                }
                if (ticks == 84) {
                    mc.getToastManager().clear();
                    shot(mc, "03-hud");
                }
                if (ticks == 90) { go(4); }
            }
            case 30 -> { // benchmark: 4 phases x 4 seconds
                if (ticks == 1 && benchPhase == 0) {
                    mc.options.getEnableVsync().setValue(false);
                    mc.getWindow().setVsync(false);
                    mc.options.getMaxFps().setValue(260);
                }
                if (ticks == 1) { benchSetup(mc, benchPhase); benchFps = 0; benchSamples = 0; }
                if (ticks > 30 && ticks % 20 == 0) { benchFps += mc.getCurrentFps(); benchSamples++; }
                if (ticks >= 110) {
                    Night.LOGGER.info("[devshot] bench '{}': {} fps avg", BENCH[benchPhase], benchSamples == 0 ? 0 : benchFps / benchSamples);
                    benchPhase++;
                    if (benchPhase >= BENCH.length) {
                        benchSetup(mc, 0);
                        mc.options.getEnableVsync().setValue(true);
                        mc.getWindow().setVsync(true);
                        go(4);
                    } else {
                        ticks = 0;
                    }
                }
            }
            case 4 -> { // third person: glow, nametag and silent rotations on ourselves
                Module esp = module("ESP");
                Module tags = module("Nametags");
                if (ticks == 1) {
                    ESP.devIncludeSelf = true;
                    Nametags.devIncludeSelf = true;
                    for (var p : esp.getProperties()) if (p instanceof ModeProperty mp && mp.getName().equals("Mode")) mp.setValue("Glow");
                    esp.setEnabled(true);
                    tags.setEnabled(true);
                    mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                }
                RotationManager.setRotations(mc.player.getYaw() + 75, 35);
                if (ticks == 38) mc.getToastManager().clear();
                if (ticks > 40) {
                    shot(mc, "04-third-person");
                    mc.options.setPerspective(Perspective.FIRST_PERSON);
                    esp.setEnabled(false);
                    tags.setEnabled(false);
                    ESP.devIncludeSelf = false;
                    Nametags.devIncludeSelf = false;
                    go(5);
                }
            }
            case 5 -> { // pause menu
                if (ticks == 1) mc.setScreen(new GameMenuScreen(true));
                if (ticks > 25) { shot(mc, "05-pause"); mc.setScreen(null); go(6); }
            }
            case 6 -> { // chat
                if (ticks == 1) mc.setScreen(new ChatScreen("hello there", false));
                if (ticks > 15) { shot(mc, "06-chat"); mc.setScreen(null); go(7); }
            }
            case 7 -> { // ClickGUI + bind test
                if (ticks == 1) {
                    ModernClickGuiScreen.devExpand(module("Killaura"));
                    Night.INSTANCE.getModuleManager().getModule(ClickGui.class).setEnabled(true);
                }
                if (ticks == 30 && mc.currentScreen instanceof ModernClickGuiScreen gui) {
                    Module ka = module("Killaura");
                    gui.devStartBinding(ka);
                    gui.keyPressed(new KeyInput(GLFW.GLFW_KEY_R, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R), 0));
                    gui.charTyped(new CharInput('r', 0));
                    Night.LOGGER.info("[devshot] bind test: key={} search='{}' -> {}", ka.getKey(), gui.devSearchText(),
                            ka.getKey() == GLFW.GLFW_KEY_R && gui.devSearchText().isEmpty() ? "PASS" : "FAIL");
                    ka.setKey(0);
                }
                if (ticks > 40) { shot(mc, "07-modules"); ModernClickGuiScreen.devPage("modules"); mc.setScreen(new HudEditorScreen(false)); go(8); }
            }
            case 8 -> {
                if (ticks > 30) { shot(mc, "08-hud-editor"); mc.setScreen(null); go(9); }
            }
            case 9 -> {
                if (ticks > 20) mc.scheduleStop();
            }
            default -> {
            }
        }
    }
}
