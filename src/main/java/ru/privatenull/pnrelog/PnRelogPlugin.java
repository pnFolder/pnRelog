package ru.privatenull.pnrelog;

import ru.privatenull.pnrelog.integration.Log;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;
import ru.privatenull.pnrelog.bootstrap.PnLibraryLifecycleListener;
import ru.privatenull.pnrelog.integration.PnLibraryRuntime;
import ru.privatenull.pnrelog.api.CombatEndReason;
import ru.privatenull.pnrelog.api.PnRelogApi;
import ru.privatenull.pnrelog.audit.AuditService;
import ru.privatenull.pnrelog.command.PnRelogCommand;
import ru.privatenull.pnrelog.combat.CombatGraph;
import ru.privatenull.pnrelog.combat.CombatService;
import ru.privatenull.pnrelog.config.ConfigLoader;
import ru.privatenull.pnrelog.config.PluginSettings;
import ru.privatenull.pnrelog.display.CombatDisplay;
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
import ru.privatenull.pnrelog.api.scheduler.PnScheduler;
import ru.privatenull.pnrelog.text.Colorizer;
import ru.privatenull.pnrelog.text.PlaceholderSupport;
import ru.privatenull.pnrelog.text.MessageService;
import ru.privatenull.pnrelog.util.MonotonicClock;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

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
    private PluginScheduler scheduler;
    private ScheduledHandle tickTask;
    private boolean pnLibraryReady;
    private PnLibraryRuntime pnRuntime;

    @Override
    public void onLoad() {
        try {
            pnLibraryReady = PnLibraryBootstrap.ensureInstalled(this, PnLibraryRuntime.MINIMUM_VERSION);
        } catch (Throwable failure) {
            Log.error("Не удалось автоматически подготовить pnLibrary. pnRelog не будет запущен.", failure);
        }
    }

    @Override
    public void onEnable() {
        if (!pnLibraryReady) {
            Log.error("pnLibrary не подготовлена: проверьте доступ к GitHub и ошибку загрузки выше.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            pnRuntime = PnLibraryRuntime.connect(this);
        } catch (Throwable failure) {
            Log.error("pnLibrary не удалось включить или подключить к pnRelog.", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new PnLibraryLifecycleListener(this), this);
        pnRuntime.register();
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
            Log.error("pnRelog не запущен", exception);
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
        try {
            items.load(config);
            actions.load(config);
        } catch (IllegalArgumentException exception) {
            Log.error("Правила предметов pnRelog не загружены", exception);
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

        PnRelogCommand command = new PnRelogCommand(this, combat, logout, messages, audit, items);
        Objects.requireNonNull(getCommand("pnrelog"), "pnrelog is absent from plugin.yml").setExecutor(command);
        Objects.requireNonNull(getCommand("pnrelog"), "pnrelog is absent from plugin.yml").setTabCompleter(command);

        scheduleRuntimeTasks(true);
        boolean placeholderApi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                && pnRuntime.registerPlaceholders(combat);
        audit.record("PLUGIN_ENABLED", null, "", "version=" + getDescription().getVersion());
        Log.info("pnRelog " + getDescription().getVersion() + " включён. Platform: "
                + (scheduler.isFolia() ? "Folia" : "Paper") + ", bStats: enabled");
        pnRuntime.started(scheduler.isFolia() ? "Folia" : "Paper", settings.locale(),
                settings.audit().enabled(), settings.regions().enabled(),
                placeholderApi);
    }

    @Override
    public void onDisable() {
        if (pnRuntime != null) pnRuntime.stopped();
        if (tickTask != null) tickTask.cancel();
        if (scheduler != null) scheduler.cancelAll();
        if (logout != null) logout.shutdown();
        if (combat != null) combat.clearAll(CombatEndReason.SHUTDOWN);
        if (display != null) display.clear();
        getServer().getServicesManager().unregisterAll(this);
        if (audit != null) {
            audit.record("PLUGIN_DISABLED", null, "", "normal-shutdown");
            audit.close();
        }
        PnLibraryRuntime closing = pnRuntime;
        pnRuntime = null;
        if (closing != null) {
            try {
                closing.close();
            } catch (RuntimeException exception) {
                Log.error("Не удалось корректно закрыть контекст pnLibrary", exception);
            }
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
            scheduleRuntimeTasks(false);
            audit.record("CONFIG_RELOADED", null, "", "success");
            return Optional.empty();
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            Log.error("Конфигурация pnRelog не применена", exception);
            return Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.getClass().getSimpleName()));
        }
    }

    private File messageFile(PluginSettings current) {
        return new File(getDataFolder(), current.locale().equals("en") ? "messages-en.yml" : "messages.yml");
    }

    private void scheduleRuntimeTasks(boolean startup) {
        if (tickTask != null) tickTask.cancel();
        tickTask = scheduler.runGlobalTimer(combat::tick, 1L, settings.combat().tickIntervalTicks());
    }
}
