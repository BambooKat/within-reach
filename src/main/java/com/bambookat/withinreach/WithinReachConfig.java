package com.bambookat.withinreach;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WithinReachConfig {
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 16;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int radius = DEFAULT_RADIUS;

    public static int radius() {
        return radius;
    }

    public static void setRadius(int value) {
        radius = Math.max(1, Math.min(MAX_RADIUS, value));
        save();
        WithinReach.RADIUS = radius; // in singleplayer vale subito
    }

    private static void save() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("withinreach.json");
        try {
            JsonObject out = new JsonObject();
            out.addProperty("radius", radius);
            Files.writeString(file, GSON.toJson(out));
        } catch (IOException e) {
            WithinReach.LOGGER.warn("Could not write withinreach.json", e);
        }
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("withinreach.json");
        try {
            if (Files.exists(file)) {
                JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (json != null && json.has("radius")) {
                    radius = json.get("radius").getAsInt();
                }
            }
            radius = Math.max(1, Math.min(MAX_RADIUS, radius));
            JsonObject out = new JsonObject();
            out.addProperty("radius", radius);
            Files.writeString(file, GSON.toJson(out));
        } catch (IOException | RuntimeException e) {
            WithinReach.LOGGER.warn("Could not read withinreach.json, using default radius", e);
            radius = DEFAULT_RADIUS;
        }
    }
}