package xyz.ashyboxy.mc.boc.discord;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BocDiscord implements ModInitializer {
	public static final String MOD_ID = "boc-discord";
    public static final Logger LOGGER = LoggerFactory.getLogger("Boc Discord");

	@Override
	public void onInitialize() {
		LOGGER.info("discord degeneracy time :3");
		Config.load();
		if(Config.debug) CommandRegistrationCallback.EVENT.register(Skins::register);
		Discord.initialize();
	}
}
