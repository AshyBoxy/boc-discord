package xyz.ashyboxy.mc.boc.discord;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("boc/discord.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static boolean debug = FabricLoader.getInstance().isDevelopmentEnvironment();
    public static boolean enable = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    public static String token = "";
    public static String channel = "";
    public static String thread = "";
    public static String webhook = "";

    public static URI skinsUrl = URI.create("http://localhost/");
    public static boolean skinHats = true;

    public static void load() {
        if (!Files.isRegularFile(configPath)) {
            save();
            return;
        }
        JsonElement j;
        try {
            j = JsonParser.parseString(Files.readString(configPath));
        } catch (IOException e) {
            BocDiscord.LOGGER.error(e.getLocalizedMessage());
            return;
        }
        if (!j.isJsonObject()) return;
        JsonObject config = j.getAsJsonObject();

        debug = GsonHelper.getAsBoolean(config, "debug", debug);
        enable = GsonHelper.getAsBoolean(config, "enable", enable);
        token = GsonHelper.getAsString(config, "token", token);
        channel = GsonHelper.getAsString(config, "channel", channel);
        thread = GsonHelper.getAsString(config, "thread", thread);
        webhook = GsonHelper.getAsString(config, "webhook", webhook);
        skinsUrl = URI.create(config.get("skinUrl").getAsString());
        skinHats = GsonHelper.getAsBoolean(config, "skinHats", skinHats);
    }

    public static void save() {
        JsonObject j = new JsonObject();

        j.addProperty("debug", debug);
        j.addProperty("enable", enable);
        j.addProperty("token", token);
        j.addProperty("channel", channel);
        j.addProperty("thread", thread);
        j.addProperty("webhook", webhook);
        j.addProperty("skinUrl", skinsUrl.toString());
        j.addProperty("skinHats", skinHats);

        try {
            configPath.getParent().toFile().mkdirs();
            Files.writeString(configPath, gson.toJson(j));
        } catch (IOException e) {
            BocDiscord.LOGGER.error(e.getLocalizedMessage());
        }
    }
}
