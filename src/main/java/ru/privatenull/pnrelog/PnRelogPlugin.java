package ru.privatenull.pnrelog;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.PnRelogApi;
import ru.privatenull.pnrelog.audit.AuditService;
import ru.privatenull.pnrelog.command.PnRelogCommand;
import ru.privatenull.pnrelog.combat.CombatGraph;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.config.ConfigLoader;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.display.CombatDisplay;
import ru.privatenull.pnrelog.integration.PnRelogExpansion;
import ru.privatenull.pnrelog.api.item.ItemControlApi;
import ru.privatenull.pnrelog.api.display.CombatDisplayApi;
import ru.privatenull.pnrelog.item.ItemControlService;
import ru.privatenull.pnrelog.listener.CombatListener;
import ru.privatenull.pnrelog.listener.ConnectionListener;
import ru.privatenull.pnrelog.listener.RestrictionListener;
import ru.privatenull.pnrelog.logout.LogoutService;
import ru.privatenull.pnrelog.scheduler.PlatformScheduler;
import ru.privatenull.pnrelog.scheduler.PluginScheduler;
import ru.privatenull.pnrelog.scheduler.ScheduledHandle;
import ru.privatenull.pnrelog.api.powerup.PowerupApi;
import ru.privatenull.pnrelog.powerup.PowerupService;
import ru.privatenull.pnrelog.api.action.ActionApi;
import ru.privatenull.pnrelog.action.ActionService;
import ru.privatenull.pnrelog.api.region.RegionApi;
import ru.privatenull.pnrelog.region.RegionService;
import ru.privatenull.pnrelog.update.UpdateService;
import ru.privatenull.pnrelog.api.scheduler.PnScheduler;
import ru.privatenull.pnrelog.text.Colorizer;
import ru.privatenull.pnrelog.text.PlaceholderSupport;
import ru.privatenull.pnrelog.text.MessageService;
import ru.privatenull.pnrelog.util.MonotonicClock;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class PnRelogPlugin extends JavaPlugin {
    private PluginSettings settings;
    private MessageService messages;
    private AuditService audit;
    private CombatService combat;
    private LogoutService logout;
    private CombatDisplay display;
    private ItemControlService items;
    private PowerupService powerups;
    private ActionService actions;
    private RegionService regions;
    private UpdateService updates;
    private ScheduledHandle updateTask;
    private PluginScheduler scheduler;
    private ScheduledHandle tickTask;
    private Runnable unregisterExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("messages-en.yml", false);
        saveResource("examples.yml", false);
        YamlConfiguration config;
        try {
            config = ConfigLoader.load(new File(getDataFolder(), "config.yml"));
            settings = PluginSettings.load(config);
            Colorizer.configure(settings.textFormat());
            PlaceholderSupport.configure(settings.usePlaceholderApi());
            YamlConfiguration messageConfig = ConfigLoader.load(messageFile(settings));
            messages = new MessageService(messageConfig);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            getLogger().severe("pnRelog не запущен: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        scheduler = new PlatformScheduler(this);
        audit = new AuditService(getLogger(), getDataFolder().toPath(), settings.audit());
        display = new CombatDisplay(settings, getLogger());
        powerups = new PowerupService(scheduler, getLogger());
        powerups.configure(settings.powerups().provider());
        combat = new CombatService(this, new CombatGraph(), new MonotonicClock(), settings,
                messages, audit, display, scheduler, powerups);
        logout = new LogoutService(this, combat, messages, audit, settings, scheduler);
        regions = new RegionService(this, combat, settings.regions(), getLogger());
        items = new ItemControlService(this, combat, messages, scheduler);
        actions = new ActionService(scheduler, getLogger(), combat);
        updates = new UpdateService(this, scheduler, messages, settings.updates());
        try {
            items.load(config);
            actions.load(config);
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Правила предметов pnRelog не загружены: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(PnRelogApi.class, combat, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ItemControlApi.class, items, this, ServicePriority.Normal);
        getServer().getServicesManager().register(CombatDisplayApi.class, display, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PowerupApi.class, powerups, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ActionApi.class, actions, this, ServicePriority.Normal);
        getServer().getServicesManager().register(RegionApi.class, regions, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PnScheduler.class, scheduler, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new CombatListener(combat), this);
        getServer().getPluginManager().registerEvents(new RestrictionListener(combat, messages), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(logout), this);
        getServer().getPluginManager().registerEvents(items, this);
        getServer().getPluginManager().registerEvents(actions, this);
        getServer().getPluginManager().registerEvents(regions, this);

        PnRelogCommand command = new PnRelogCommand(this, combat, logout, messages, audit, items, updates);
        Objects.requireNonNull(getCommand("pnrelog"), "pnrelog is absent from plugin.yml").setExecutor(command);
        Objects.requireNonNull(getCommand("pnrelog"), "pnrelog is absent from plugin.yml").setTabCompleter(command);

        scheduleRuntimeTasks(true);
        new Metrics(this, 33313);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            PnRelogExpansion expansion = new PnRelogExpansion(combat, getDescription().getVersion());
            if (expansion.register()) unregisterExpansion = expansion::unregister;
            else getLogger().warning("PlaceholderAPI expansion pnrelog не зарегистрирована");
        }
        audit.record("PLUGIN_ENABLED", null, "", "version=" + getDescription().getVersion());
        getLogger().info("pnRelog " + getDescription().getVersion() + " включён. Platform: "
                + (scheduler.isFolia() ? "Folia" : "Paper") + ", bStats: enabled");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (updateTask != null) updateTask.cancel();
        if (scheduler != null) scheduler.cancelAll();
        if (unregisterExpansion != null) unregisterExpansion.run();
        if (logout != null) logout.shutdown();
        if (combat != null) combat.clearAll(CombatEndReason.SHUTDOWN);
        if (display != null) display.clear();
        getServer().getServicesManager().unregisterAll(this);
        if (audit != null) {
            audit.record("PLUGIN_DISABLED", null, "", "normal-shutdown");
            audit.close();
        }
    }

    /** Reloads both files only after all values have passed validation. */
    public Optional<String> reloadRuntime() {
        try {
            YamlConfiguration newConfig = ConfigLoader.load(new File(getDataFolder(), "config.yml"));
            PluginSettings newSettings = PluginSettings.load(newConfig);
            YamlConfiguration newMessages = ConfigLoader.load(messageFile(newSettings));
            items.load(newConfig);
            actions.load(newConfig);
            settings = newSettings;
            Colorizer.configure(newSettings.textFormat());
            PlaceholderSupport.configure(newSettings.usePlaceholderApi());
            messages.replace(newMessages);
            audit.update(newSettings.audit());
            combat.updateSettings(newSettings);
            powerups.configure(newSettings.powerups().provider());
            logout.updateSettings(newSettings);
            regions.update(newSettings.regions());
            updates.updateSettings(newSettings.updates());
            scheduleRuntimeTasks(false);
            audit.record("CONFIG_RELOADED", null, "", "success");
            return Optional.empty();
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            getLogger().warning("Конфигурация pnRelog не применена: " + exception.getMessage());
            return Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.getClass().getSimpleName()));
        }
    }

    private File messageFile(PluginSettings current) {
        return new File(getDataFolder(), current.locale().equals("en") ? "messages-en.yml" : "messages.yml");
    }

    private void scheduleRuntimeTasks(boolean startup) {
        if (tickTask != null) tickTask.cancel();
        if (updateTask != null) updateTask.cancel();
        tickTask = scheduler.runGlobalTimer(combat::tick, 1L, settings.combat().tickIntervalTicks());
        updateTask = null;
        if (!settings.updates().enabled()) return;
        if (startup) scheduler.runGlobalLater(updates::startupCheck, 40L);
        updateTask = scheduler.runGlobalTimer(updates::startupCheck,
                settings.updates().checkIntervalTicks(), settings.updates().checkIntervalTicks());
    }
}
