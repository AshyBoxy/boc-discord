package xyz.ashyboxy.mc.boc.discord;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;

public class BDCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, CommandSelection environment) {
        BocDiscord.LOGGER.info("Registering BOC-Discord commands");

        var boc = dispatcher.getRoot().getChild("boc");
        if (boc == null) {
            boc = Commands.literal("boc").build();
            dispatcher.getRoot().addChild(boc);
        }

        var discord = Commands.literal("discord")
                .requires(s -> s.hasPermission(2))
                .build();
        boc.addChild(discord);

        Skins.register(discord);


        var send = Commands.literal("send").build();
        var player = Commands.argument("player", GameProfileArgument.gameProfile()).build();
        var message = Commands.argument("message", ComponentArgument.textComponent(context))
                .executes(BDCommands::send)
                .build();
        
        discord.addChild(send);
        send.addChild(player);
        player.addChild(message);


        var system = Commands.literal("system").build();
        var systemMessage = Commands.argument("message", ComponentArgument.textComponent(context))
                .executes(BDCommands::sendSystem)
                .build();

        discord.addChild(system);
        system.addChild(systemMessage);


        var server = Commands.literal("server").build();
        var serverMessage = Commands.argument("message", ComponentArgument.textComponent(context))
                .executes(BDCommands::sendServer)
                .build();

        discord.addChild(server);
        server.addChild(serverMessage);
        
        
        var sendDiscord = Commands.literal("sendDiscord").build();
        var sendDiscordMessage = Commands.argument("message", StringArgumentType.greedyString())
                .executes(BDCommands::sendDiscord)
                .build();

        discord.addChild(sendDiscord);
        sendDiscord.addChild(sendDiscordMessage);
    }

    private static int send(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Component message = ComponentArgument.getComponent(context, "message");
        GameProfile profile = GameProfileArgument.getGameProfiles(context, "player").stream().findFirst().orElseThrow();
        // surely there's a better way to do this?
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(profile.getId());
        Component playerName;
        URI avatarURL = null;

        if (player != null) {
            playerName = player.getDisplayName();
            avatarURL = Skins.Renderer.getHeadURIFromPlayer(player, context.getSource().getServer(), Config.skinHats, 64);
        } else {
            playerName = Component.literal(profile.getName());
            MinecraftSessionService sessionService = context.getSource().getServer().getSessionService();
            MinecraftProfileTexture skin = sessionService.getTextures(profile).skin();
            if (skin == null) {
                ProfileResult fetchedProfile = sessionService.fetchProfile(profile.getId(), false);
                if (fetchedProfile != null) {
                    skin = sessionService.getTextures(fetchedProfile.profile()).skin();
                }
            }
            if (skin != null)
                avatarURL = Skins.Renderer.getHeadURI(skin.getUrl(), Config.skinHats, 64);
        }

        Component decorated = ChatType.bind(ChatType.CHAT, context.getSource().registryAccess(), playerName).decorate(message);

        context.getSource().getServer().getPlayerList().broadcastSystemMessage(decorated, false);

        if (avatarURL == null)
            context.getSource().sendFailure(Component.literal("Could not find player skin, not sending discord message"));
        else Discord.sendWebhook(Discord.serializeComponent(message), playerName.getString(), avatarURL);

        return 0;
    }

    private static int sendSystem(CommandContext<CommandSourceStack> context) {
        Component message = ComponentArgument.getComponent(context, "message");
        context.getSource().getServer().getPlayerList().broadcastSystemMessage(message, false);
        Discord.sendMessage(Discord.serializeComponent(message));
        return 0;
    }

    private static int sendServer(CommandContext<CommandSourceStack> context) {
        Component message = ComponentArgument.getComponent(context, "message");
        Component decorated = ChatType.bind(ChatType.SAY_COMMAND, context.getSource().registryAccess(), Component.literal("Server")).decorate(message);
        context.getSource().getServer().getPlayerList().broadcastSystemMessage(decorated, false);
        Discord.sendMessage(Discord.serializeComponent(message));
        return 0;
    }
    
    private static int sendDiscord(CommandContext<CommandSourceStack> context) {
        String message = StringArgumentType.getString(context, "message");
        Discord.sendMessage(message);
        return 0;
    }
}
