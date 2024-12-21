package xyz.ashyboxy.mc.boc.discord;

import me.hypherionmc.mcdiscordformatter.discord.DiscordSerializer;
import me.hypherionmc.mcdiscordformatter.minecraft.MinecraftSerializer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Discord {
    public static JDA jda;
    public static IncomingWebhookClient webhook;
    private static MessageChannel channel;

    public static final List<Component> discordMessages = Collections.synchronizedList(new ArrayList<>());

    // TODO: server messages as the webhook
    // TODO: split the event handlers out
    public static void initialize() {
        if(Config.token.isEmpty() || Config.channel.isEmpty() || Config.webhook.isEmpty()) {
            BocDiscord.LOGGER.error("Discord config needs setting");
            return;
        }

        jda = JDABuilder.createLight(Config.token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new ReadyListener(), new MessageListener())
                .build();
        webhook = WebhookClient.createClient(jda, Config.webhook);

        // fabric events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Component playerName = handler.getPlayer().getDisplayName();
            if (playerName == null) playerName = handler.getPlayer().getName();
            sendMessage(String.format("%s just joined the server", playerName.getString()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Component playerName = handler.getPlayer().getDisplayName();
            if (playerName == null) playerName = handler.getPlayer().getName();
            sendMessage(String.format("%s just left the server", playerName.getString()));
        });

        ServerLivingEntityEvents.AFTER_DEATH.register(((entity, damageSource) -> {
            if (!(entity.hasCustomName() || entity instanceof ServerPlayer)) return;
            sendMessage(damageSource.getLocalizedDeathMessage(entity).getString());
        }));

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            Component msg = message.decoratedContent();
            Component playerName = sender.getDisplayName();
            if (playerName == null) playerName = sender.getName();
            URI avatarURL = Skins.Renderer.getHeadURIFromPlayer(sender, sender.getServer(), Config.skinHats, 64);
            sendWebhook(msg.getString(), playerName.getString(), avatarURL);
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
            if (!source.isPlayer()) return;
            Component msg = message.decoratedContent();
            Component playerName = source.getDisplayName();
            URI avatarURL = Skins.Renderer.getHeadURIFromPlayer(source.getPlayer(), source.getServer(), Config.skinHats, 64);
            sendWebhook(msg.getString(), playerName.getString(), avatarURL);
        });

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            sendMessage("Server started");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
            sendMessage("Server stopped");
        });

        ServerTickEvents.START_SERVER_TICK.register((server) -> {
            synchronized (discordMessages) {
                Iterator<Component> it = discordMessages.iterator();
                while (it.hasNext()) {
                    Component msg = it.next();
                    server.getPlayerList().broadcastSystemMessage(msg, false);
                    it.remove();
                }
            }
        });
    }

    public static void sendMessage(String message) {
        if (channel == null) return;
        channel.sendMessage(message).queue();
    }

    public static void sendWebhook(String message, @Nullable String username, @Nullable URI avatarURL) {
        sendWebhook(message, username, avatarURL != null ? avatarURL.toString() : null);
    }

    public static void sendWebhook(String message, @Nullable String username, @Nullable String avatarURL) {
        var m = webhook.sendMessage(message).setUsername(username).setAvatarUrl(avatarURL);
        if (!Config.thread.isEmpty()) m.setThreadId(Config.thread);
        m.queue();
    }

    public static void triggerAdvancement(AdvancementHolder advancement, DisplayInfo advancementDisplayInfo,
                                          ServerPlayer player) {
        sendMessage(DiscordSerializer.INSTANCE.serialize(advancementDisplayInfo.getType().createAnnouncement(advancement, player)) + ": *" + DiscordSerializer.INSTANCE.serialize(advancementDisplayInfo.getDescription().copy()) + "*");
    }

    public static class ReadyListener implements EventListener {
        @Override
        public void onEvent(@NotNull GenericEvent event) {
            if (event instanceof ReadyEvent) {
                BocDiscord.LOGGER.info("Discord Ready");
                channel = jda.getChannelById(MessageChannel.class, Config.channel);
                if (channel == null) {
                    BocDiscord.LOGGER.error("Discord channel not found");
                }
            }
        }
    }

    public static class MessageListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            User author = event.getAuthor();
            Message message = event.getMessage();
            if (!event.getChannel().getId().equals(channel.getId()) || message.getAuthor().getId().equals(webhook.getId()) || author.getId().equals(jda.getSelfUser().getId()))
                return;
            Member member = message.getMember();
            String username = null;
            if (member != null) username = member.getNickname();
            if (username == null) username = message.getAuthor().getEffectiveName();

            // this is in a jda thread, so we need to get this to the server thread somehow
            // at least i think we do?
            MutableComponent msgComponent =
                    Component.empty().append(Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE,
                            ChatFormatting.BOLD)).append(Component.literal("<" + username + "> ").withStyle(ChatFormatting.GOLD)).append(MinecraftSerializer.INSTANCE.serialize(message.getContentDisplay()));
            if (!message.getAttachments().isEmpty() || !message.getEmbeds().isEmpty()) msgComponent.append(Component.literal((!message.getContentDisplay().isEmpty() ? " " : "") + "(+" + (message.getAttachments().size() + message.getEmbeds().size()) + " attachments)").withStyle(ChatFormatting.ITALIC));
            discordMessages.add(msgComponent);
        }
    }
}
