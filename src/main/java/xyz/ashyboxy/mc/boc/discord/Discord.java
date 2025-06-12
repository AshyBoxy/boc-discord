package xyz.ashyboxy.mc.boc.discord;

import me.hypherionmc.mcdiscordformatter.discord.DiscordSerializer;
import me.hypherionmc.mcdiscordformatter.minecraft.MinecraftSerializer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;

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

            if (msg.getString().startsWith("xaero-waypoint:")) {
                String[] parts = msg.getString().split(":");
                msg = Component.literal(
                        String.format("Shared a waypoint called \"%s\" at %s %s %s!", parts[1], parts[3], parts[4], parts[5])
                ).withStyle(ChatFormatting.ITALIC);
            }

            sendWebhook(serializeComponent(msg), playerName.getString(), avatarURL);
        });
        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
            if (!source.isPlayer()) return;
            Component msg = message.decoratedContent();
            Component playerName = source.getDisplayName();
            URI avatarURL = Skins.Renderer.getHeadURIFromPlayer(source.getPlayer(), source.getServer(), Config.skinHats, 64);
            sendWebhook(serializeComponent(msg), playerName.getString(), avatarURL);
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
        sendMessage(serializeComponent(advancementDisplayInfo.getType().createAnnouncement(advancement, player)) + ": *" + DiscordSerializer.INSTANCE.serialize(advancementDisplayInfo.getDescription().copy()) + "*");
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

            // this is in a jda thread, so we need to get this to the server thread somehow
            // at least i think we do?
            discordMessages.add(decorateDiscordMessage(message));
        }
    }
    
    public static Component decorateDiscordMessage(Message message) {
        MutableComponent msgComponent = Component.empty();
        MutableComponent nameComponent;
        MutableComponent nameHoverComponent = Component.empty();
        
        {
            Member member = message.getMember();
            String nickname = member != null ? member.getNickname() : null;
            User user = message.getAuthor();
            String displayName = user.getGlobalName();
            String username = user.getName();
            String name = nickname != null ? nickname : displayName != null ? displayName : username;

            if (nickname != null && (!nickname.equals(displayName) || !nickname.equals(username))) {
                if (displayName != null) {
                    nameHoverComponent.append(Component.literal(displayName));
                    nameHoverComponent.append(Component.literal(" (@" + username + ")"));
                } else nameHoverComponent.append(Component.literal("@" + username));
            }

            nameComponent = Component.literal(name);
        }
        
        nameComponent.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, nameHoverComponent)));
        

        msgComponent.append(Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
        msgComponent.append(Component.literal("<").append(nameComponent).append("> ").withStyle(ChatFormatting.GOLD));
        msgComponent.append(deserializeComponent(message.getContentDisplay()));

        if (!message.getAttachments().isEmpty() || !message.getEmbeds().isEmpty()) msgComponent.append(
                Component.literal((!message.getContentDisplay().isBlank() ? " " : "")
                                + "(+" + (message.getAttachments().size() + message.getEmbeds().size()) + " " + "attachments)")
                        .withStyle(ChatFormatting.ITALIC));

        if (message.getType() == MessageType.INLINE_REPLY) {
            MutableComponent replyComponent = Component.literal("reply").withStyle(ChatFormatting.ITALIC);
            MessageReference reference = message.getMessageReference();
            if (reference != null && reference.getMessage() instanceof Message referenceMsg) {
                // this logic is probably better than the logic used to fill in the sender
                // TODO: split this into a separate method (probably a separate class)
                
                String effectiveName;
                String userEffectiveName;
                String username;
                
                if (referenceMsg.getMember() instanceof Member referenceMember) {
                    effectiveName = referenceMember.getEffectiveName();
                    userEffectiveName = referenceMember.getUser().getEffectiveName();
                    username = referenceMember.getUser().getEffectiveName();
                } else {
                    User user = referenceMsg.getAuthor();
                    try {
                        Member referenceMember = Objects.requireNonNull(reference.getGuild()).retrieveMember(user).complete();
                        effectiveName = referenceMember.getEffectiveName();
                    } catch (Exception e) {
                        effectiveName = user.getEffectiveName();
                    }
                    userEffectiveName = user.getEffectiveName();
                    username = user.getName();
                }

                MutableComponent hover = Component.empty().append(Component.literal(effectiveName).withStyle(ChatFormatting.GOLD));
                MutableComponent hoverSecondary = Component.empty().withStyle(ChatFormatting.GRAY);
                
                // TODO: should probably use a string builder for things like this
                if (!effectiveName.equals(userEffectiveName)) {
                    hoverSecondary.append(Component.literal(" (" + userEffectiveName));
                    if (!userEffectiveName.equals(username)) hoverSecondary.append(Component.literal(" (@" + username + ")"));
                    hoverSecondary.append(Component.literal(")"));
                } else if (!userEffectiveName.equals(username)) hoverSecondary.append(Component.literal(" @" + username));
                
                if(!hoverSecondary.getString().isBlank()) hover.append(hoverSecondary);
                hover.append("\n");
                hover.append(deserializeComponent(referenceMsg.getContentDisplay()));
                
                replyComponent.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
            }
            msgComponent.append(Component.literal(" (").append(replyComponent).append(")"));
        }


        return msgComponent;
    }
    
    public static String serializeComponent(Component component) {
        return DiscordSerializer.INSTANCE.serialize(component.copy());
    }
    
    public static Component deserializeComponent(String serialized) {
        return MinecraftSerializer.INSTANCE.serialize(serialized);
    }
}
