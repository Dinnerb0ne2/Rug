package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CustomCape extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static List<ResourceLocation> LOADED_CAPES = new ArrayList<>();
    public static List<String> CAPE_NAMES = new ArrayList<>();
    public ModeProperty capeMode;
    public BooleanProperty loadCapes;

    private static File directory;

    public CustomCape() {
        super("CustomCape", false);
        directory = new File(mc.mcDataDir + File.separator + "Myau", "CustomCapes");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        this.loadCapes = new BooleanProperty("Load-Capes", false);
        loadCapesList();
        this.capeMode = new ModeProperty("Cape", 0, CAPE_NAMES.toArray(new String[0]));
    }

    public static void loadCapesList() {
        LOADED_CAPES.clear();
        CAPE_NAMES.clear();
        CAPE_NAMES.add("None");
        LOADED_CAPES.add(null);

        try {
            java.net.URL url = CustomCape.class.getClassLoader().getResource("assets/myau/cape");
            if (url != null) {
                if (url.getProtocol().equals("jar")) {
                    java.net.JarURLConnection connection = (java.net.JarURLConnection) url.openConnection();
                    JarFile jarFile = connection.getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    String basePath = "assets/myau/cape/";
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(basePath) && name.endsWith(".png") && name.length() > basePath.length()) {
                            String capeName = name.substring(basePath.length(), name.length() - 4);
                            try {
                                InputStream stream = CustomCape.class.getClassLoader().getResourceAsStream(name);
                                if (stream != null) {
                                    BufferedImage bufferedImage = ImageIO.read(stream);
                                    LOADED_CAPES.add(mc.renderEngine.getDynamicTextureLocation(capeName, new DynamicTexture(bufferedImage)));
                                    CAPE_NAMES.add(capeName);
                                    stream.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else if (url.getProtocol().equals("file")) {
                    File dir = new File(url.toURI());
                    if (dir.exists() && dir.isDirectory()) {
                        for (File file : dir.listFiles()) {
                            if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                                String capeName = file.getName().substring(0, file.getName().length() - 4);
                                try {
                                    BufferedImage bufferedImage = ImageIO.read(file);
                                    LOADED_CAPES.add(mc.renderEngine.getDynamicTextureLocation(capeName, new DynamicTexture(bufferedImage)));
                                    CAPE_NAMES.add(capeName);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                        String capeName = file.getName().substring(0, file.getName().length() - 4);
                        try {
                            BufferedImage bufferedImage = ImageIO.read(file);
                            LOADED_CAPES.add(mc.renderEngine.getDynamicTextureLocation(capeName, new DynamicTexture(bufferedImage)));
                            CAPE_NAMES.add(capeName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEnabled() {
        if (this.loadCapes.getValue()) {
            loadCapesList();
            this.loadCapes.setValue(false);
        }
    }

    public ResourceLocation getCapeLocation() {
        if (!this.isEnabled() || this.capeMode.getValue() == 0) return null;
        int index = this.capeMode.getValue();
        if (index > 0 && index < LOADED_CAPES.size()) {
            return LOADED_CAPES.get(index);
        }
        return null;
    }
}