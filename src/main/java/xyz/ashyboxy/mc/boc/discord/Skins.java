package xyz.ashyboxy.mc.boc.discord;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;

// i am proud of this horrible mess
public class Skins {
    public static void register(CommandNode<CommandSourceStack> root) {
        BocDiscord.LOGGER.info("Registering BOC-Discord skin commands");

        var skins = Commands.literal("skins").then(Commands.literal("test").executes((c) -> {
            CommandSourceStack source = c.getSource();
            MinecraftServer server = source.getServer();
            ServerLevel level = source.getLevel();
            ServerPlayer player = source.getPlayer();
            RegistryAccess registryAccess = source.registryAccess();
            if (player == null) return -1;
            URI headURI = Renderer.getHeadURIFromPlayer(player, server);
            if (headURI == null) return -1;

            source.sendSystemMessage(Component.literal(headURI.toString()));
            return 0;
        })).build();
        
        root.addChild(skins);
    }

    @SuppressWarnings({ "ResultOfMethodCallIgnored", "unused" })
    public static class Renderer {
        private static final int faceHorizontalOffset = 8;
        private static final int faceVerticalOffset = 8;
        private static final int hatHorizontalOffset = 40;
        private static final int hatVerticalOffset = 8;

        // vanilla has these duplicated, so this is the easiest way to get the right output
        // the class storing them is only available on the client, so there isn't an
        // easy way to make this work with mods
        // this is only relevant in a dev env (offline accounts) anyway, proper accounts
        // with a default skin still have their skin on mojang's servers
        private static final String[] defaultSkins = new String[]{ "assets/boc-discord/defaultskins/alex.png", "assets/boc-discord/defaultskins/ari.png", "assets/boc-discord/defaultskins/efe.png", "assets/boc-discord/defaultskins/kai.png", "assets/boc-discord/defaultskins/makena.png", "assets/boc-discord/defaultskins/noor.png", "assets/boc-discord/defaultskins/steve.png", "assets/boc-discord/defaultskins/sunny.png", "assets/boc-discord/defaultskins/zuri.png", "assets/boc-discord/defaultskins/alex.png", "assets/boc-discord/defaultskins/ari.png", "assets/boc-discord/defaultskins/efe.png", "assets/boc-discord/defaultskins/kai.png", "assets/boc-discord/defaultskins/makena.png", "assets/boc-discord/defaultskins/noor.png", "assets/boc-discord/defaultskins/steve.png", "assets/boc-discord/defaultskins/sunny.png", "assets/boc-discord/defaultskins/zuri.png" };
        private static final Pattern dotPngPattern = Pattern.compile("\\.png$");

        // this should point to the folder served at Config.skinsUrl
        private static final Path outputDir = FabricLoader.getInstance().getConfigDir().resolve("boc/skins");

        @Nullable
        public static URI getHeadURIFromPlayer(Player player, MinecraftServer server, boolean hat, int scale) {
            MinecraftProfileTexture skin = server.getSessionService().getTextures(player.getGameProfile()).skin();
            if (skin != null) return getHeadURI(skin.getUrl(), hat, scale);
            else {
                try {
                    URL defaultURL = Renderer.class.getClassLoader().getResource(defaultSkins[Math.floorMod(player.getUUID().hashCode(), defaultSkins.length)]);
                    if (defaultURL == null) return null;
                    return getHeadURI(defaultURL.toURI(), hat, scale);
                } catch (URISyntaxException e) {
                    return null;
                }
            }
        }

        @Nullable
        public static URI getHeadURIFromPlayer(Player player, MinecraftServer server) {
            return getHeadURIFromPlayer(player, server, true, 1);
        }

        @Nullable
        public static URI getHeadURIFromPlayer(Player player, MinecraftServer server, boolean hat) {
            return getHeadURIFromPlayer(player, server, hat, 1);
        }

        @Nullable
        public static URI getHeadURIFromPlayer(Player player, MinecraftServer server, int scale) {
            return getHeadURIFromPlayer(player, server, true, scale);
        }

        @Nullable
        public static URI getHeadURI(URI source, boolean hat, int scale) {
            String outputBasePath;
            if (!source.getScheme().equals("file"))
                outputBasePath = source.getHost() + "/" + source.getPath();
            else outputBasePath = "local/" + Arrays.stream(source.getPath().split("/")).toList().getLast();
            outputBasePath = outputBasePath.replaceFirst(dotPngPattern.pattern(), "");
            String outputRelativePath = outputBasePath.concat(".png");
            String faceRelativePath = outputBasePath.concat("-nohat.png");
            String outputRelativeScaledPath = outputBasePath.concat("-" + 8 * scale + ".png");
            String faceRelativeScaledPath = outputBasePath.concat("-" + 8 * scale + "-nohat.png");
            File outputFile = Paths.get(outputDir.toString(), outputRelativePath).toFile();
            File faceFile = Paths.get(outputDir.toString(), faceRelativePath).toFile();
            File outputScaledFile = Paths.get(outputDir.toString(), outputRelativeScaledPath).toFile();
            File faceScaledFile = Paths.get(outputDir.toString(), faceRelativeScaledPath).toFile();

            if (scale > 1) {
                if (!hat && faceScaledFile.exists()) return Config.skinsUrl.resolve(faceRelativeScaledPath);
                if (outputScaledFile.exists()) return Config.skinsUrl.resolve(outputRelativeScaledPath);
            } else {
                if (!hat && faceFile.exists()) return Config.skinsUrl.resolve(faceRelativePath);
                else if (outputFile.exists()) return Config.skinsUrl.resolve(outputRelativePath);
            }

            BufferedImage origImage;
            try {
                origImage = ImageIO.read(source.toURL());
            } catch (IOException e) {
                return null;
            }
            if (origImage.getWidth() != 64) return null;
            BufferedImage image = new BufferedImage(origImage.getWidth() * scale, origImage.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
            image.getGraphics().drawImage(origImage, 0, 0, null);
            BufferedImage outputImage = new BufferedImage(8, 8, image.getType());
            BufferedImage faceImage = new BufferedImage(8, 8, image.getType());
            Raster raster = image.getRaster();
            WritableRaster outputRaster = outputImage.getRaster();
            WritableRaster faceRaster = faceImage.getRaster();
            WritableRaster hatRaster = image.getColorModel().createCompatibleWritableRaster(8, 8);

            for (int y = 0; y < 8; y++) {
                int[] pixels = new int[8 * 4];
                raster.getPixels(faceHorizontalOffset, faceVerticalOffset + y, 8, 1, pixels);
                faceRaster.setPixels(0, y, 8, 1, pixels);
            }
            for (int y = 0; y < 8; y++) {
                int[] pixels = new int[8 * 4];
                raster.getPixels(hatHorizontalOffset, hatVerticalOffset + y, 8, 1, pixels);
                hatRaster.setPixels(0, y, 8, 1, pixels);
            }

            AlphaComposite.getInstance(AlphaComposite.DST_ATOP).createContext(image.getColorModel(), image.getColorModel(), null).compose(faceRaster, hatRaster, outputRaster);

            // outputImage has the face + hat, faceImage has only the face
            try {
                outputFile.mkdirs();

                ImageIO.write(outputImage, "PNG", outputFile);
                ImageIO.write(faceImage, "PNG", faceFile);

                if (scale > 1) {
                    BufferedImage scaledImage = new BufferedImage(outputImage.getWidth() * scale, outputImage.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
                    BufferedImage faceScaledImage = new BufferedImage(faceImage.getWidth() * scale, faceImage.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
                    scaledImage.getGraphics().drawImage(outputImage.getScaledInstance(scaledImage.getWidth(), scaledImage.getHeight(), Image.SCALE_REPLICATE), 0, 0, null);
                    faceScaledImage.getGraphics().drawImage(faceImage.getScaledInstance(faceScaledImage.getWidth(), faceScaledImage.getHeight(), Image.SCALE_REPLICATE), 0, 0, null);

                    ImageIO.write(scaledImage, "PNG", outputScaledFile);
                    ImageIO.write(faceScaledImage, "PNG", faceScaledFile);

                    if (!hat) return Config.skinsUrl.resolve(faceRelativeScaledPath);
                    else return Config.skinsUrl.resolve(outputRelativeScaledPath);
                }

                if (!hat) return Config.skinsUrl.resolve(faceRelativePath);
                else return Config.skinsUrl.resolve(outputRelativePath);
            } catch (IOException e) {
                BocDiscord.LOGGER.error("Failed to write image to {}", outputDir, e);
                return null;
            }
        }

        // spaghet
        @Nullable
        public static URI getHeadURI(URI source) {
            return getHeadURI(source, true, 1);
        }

        @Nullable
        public static URI getHeadURI(URI source, boolean hat) {
            return getHeadURI(source, hat, 1);
        }

        @Nullable
        public static URI getHeadURI(URI source, int scale) {
            return getHeadURI(source, true, scale);
        }

        @Nullable
        public static URI getHeadURI(String source) {
            return getHeadURI(source, true, 1);
        }

        @Nullable
        public static URI getHeadURI(String source, boolean hat) {
            return getHeadURI(source, hat, 1);
        }

        @Nullable
        public static URI getHeadURI(String source, boolean hat, int scale) {
            try {
                return getHeadURI(new URI(source), hat, scale);
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }
}
