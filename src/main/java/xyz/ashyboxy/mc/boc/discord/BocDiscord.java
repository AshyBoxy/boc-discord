package xyz.ashyboxy.mc.boc.discord;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BocDiscord implements ModInitializer {
	public static final String MOD_ID = "boc-discord";
    public static final Logger LOGGER = LoggerFactory.getLogger("BOC Discord");

	@Override
	public void onInitialize() {
		LOGGER.info("discord degeneracy time :3");
		Config.load();
		Skins.Renderer.setOutputDir(FabricLoader.getInstance().getConfigDir().resolve("boc/skins"));
		Discord.initialize();
		CommandRegistrationCallback.EVENT.register(BDCommands::register);
	}
}
