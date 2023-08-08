package com.rokucraft.resourcepacker;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ResourcePacker extends JavaPlugin implements Listener {
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private HttpRequest request;
    private PackData packData;
    private String baseUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String dataProvider = getConfig().getString("data-provider");
        this.baseUrl = getConfig().getString("base-url");
        try {
            if (dataProvider == null) throw new IllegalAccessException("You did not configure a data provider.");
            this.request = HttpRequest.newBuilder()
                    .uri(URI.create(dataProvider))
                    .timeout(Duration.ofSeconds(10))
                    .build();
        } catch (IllegalAccessException e) {
            getSLF4JLogger().error("Invalid data provider", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshUrl, 0, 20 * 60 * 5);
    }

    private void refreshUrl() {
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() != 200) {
                        throw new UncheckedIOException(new IOException(
                                "HTTP request failed: status code " + res.statusCode() + ", " + res.body()
                        ));
                    }
                    return res.body();
                })
                .thenApply(body -> this.gson.fromJson(body, PackData.class))
                .thenAccept(data -> {
                    if (data.equals(this.packData)) return;
                    getLogger().info("Setting active resource pack to " + data.key());
                    this.packData = data;
                })
                .exceptionally(e -> {
                    getSLF4JLogger().error("Unable to fetch resource pack data", e.getCause());
                    return null;
                });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (packData == null) return;
        Player player = e.getPlayer();
        player.setResourcePack(
                baseUrl + packData.key(),
                packData.sha1(),
                !player.hasPermission("resourcepacker.bypass")
        );
    }
}
