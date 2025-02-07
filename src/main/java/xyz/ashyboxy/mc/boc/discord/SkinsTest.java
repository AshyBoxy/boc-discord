package xyz.ashyboxy.mc.boc.discord;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.knot.Knot;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;

public class SkinsTest {
    private static final String TEST_URL = "https://textures.minecraft.net/texture/15647c4a323d1ba7af9c6e31658cbb4d036d139e859c6c79c331c1173479f509";
    private static final String TEST_URL2 = "https://textures.minecraft.net/texture/45fbe05a5f9a40a5da9326bd8724ea4601b7c8226ba88e6c2428f0bca690f21";
    private static final Path TEST_PATH = Path.of("./test/");
    
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        Field configDir = FabricLoaderImpl.INSTANCE.getClass().getDeclaredField("configDir");
        configDir.setAccessible(true);
        configDir.set(FabricLoaderImpl.INSTANCE, TEST_PATH);
        new Knot(EnvType.SERVER);
        Config.skinsUrl = URI.create("https://localhost/");
        Skins.Renderer.setOutputDir(TEST_PATH);
        System.out.println(Skins.Renderer.getHeadURI(TEST_URL, true, 64));
    }
}
