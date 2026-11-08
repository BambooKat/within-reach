package com.bambookat.withinreach.client;

import com.bambookat.withinreach.WithinReach;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Posizioni del bottone WR spostate a mano (Alt+trascina), relative all'angolo dell'inventario, una per tipo di schermata. */
public class ButtonPositions {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, int[]> POSITIONS = new HashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("withinreach-client.json");
    }

    /** null = nessuna posizione salvata, si usa quella automatica. */
    public static int[] get(String key) {
        return POSITIONS.get(key);
    }

    public static void set(String key, int x, int y) {
        POSITIONS.put(key, new int[]{x, y});
        save();
    }

    public static void reset(String key) {
        if (POSITIONS.remove(key) != null) save();
    }

    public static void load() {
        POSITIONS.clear();
        try {
            if (!Files.exists(file())) return;
            JsonObject json = GSON.fromJson(Files.readString(file()), JsonObject.class);
            if (json == null || !json.has("buttons")) return;
            JsonObject buttons = json.getAsJsonObject("buttons");
            for (String key : buttons.keySet()) {
                JsonArray arr = buttons.getAsJsonArray(key);
                POSITIONS.put(key, new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()});
            }
        } catch (IOException | RuntimeException e) {
            WithinReach.LOGGER.warn("Could not read withinreach-client.json, using automatic button positions", e);
            POSITIONS.clear();
        }
    }

    private static void save() {
        JsonObject buttons = new JsonObject();
        POSITIONS.forEach((key, pos) -> {
            JsonArray arr = new JsonArray();
            arr.add(pos[0]);
            arr.add(pos[1]);
            buttons.add(key, arr);
        });
        JsonObject out = new JsonObject();
        out.add("buttons", buttons);
        try {
            Files.writeString(file(), GSON.toJson(out));
        } catch (IOException e) {
            WithinReach.LOGGER.warn("Could not write withinreach-client.json", e);
        }
    }
}
