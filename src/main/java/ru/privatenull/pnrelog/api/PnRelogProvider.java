package ru.privatenull.pnrelog.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class PnRelogProvider {
    private PnRelogProvider() {
    }

    public static PnRelogApi get() {
        RegisteredServiceProvider<PnRelogApi> registration = Bukkit.getServicesManager()
                .getRegistration(PnRelogApi.class);
        if (registration == null) {
            throw new IllegalStateException("pnRelog API is not available");
        }
        return registration.getProvider();
    }
}
