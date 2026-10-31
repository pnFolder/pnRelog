package ru.privatenull.pnrelog.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;

import java.util.Map;
import java.util.logging.Level;

/** Managed pnLibrary integration loaded only after the dependency is enabled. */
public final class PnLibraryRuntime {

    public static final String MINIMUM_VERSION = "0.0.1-dev.6";
    private static final String MODULE_ID = "pnrelog";
    private static final int BSTATS_PROJECT_ID = 33313;
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnRelogPolicy.java";
    private static final String UPDATE_OWNER = "pnFolder";
    private static final String UPDATE_REPOSITORY = "pnRelog";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnLibraryRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnRelog требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    public void register() {
        if (registration != null) throw new IllegalStateException("pnRelog уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pnrelog")
                            .snapshot(() -> Map.of("version", owner.getDescription().getVersion()))
                            .configuration(DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build())
                            .build())
                    // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnRelog;
                    // only an explicit denial from the policy does.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .metrics(metrics -> metrics.bStats(BSTATS_PROJECT_ID))
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY, updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .configurationBackup(owner.getDataFolder().toPath(),
                                    "config.yml", "messages.yml", "messages-en.yml", "examples.yml")
                            .artifact("(?i)^pnRelog-[0-9].*\\.jar$", 17)));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
    }

    public void enabled() {
        if (module == null) return;
        try {
            module.getLifecycle().enabled().show();
        } catch (RuntimeException failure) {
            owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение о запуске pnRelog", failure);
        }
    }

    public void close() {
        ModuleContext closing = module;
        module = null;
        if (closing != null && !closing.isClosed()) {
            try {
                closing.getLifecycle().disabled().show();
            } catch (RuntimeException failure) {
                owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnRelog", failure);
            }
        }
        closeRegistration();
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
