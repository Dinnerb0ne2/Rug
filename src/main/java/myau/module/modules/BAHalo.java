package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.lwjgl.opengl.GL11.*;

public class BAHalo extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode;
    public final BooleanProperty firstPerson;
    public final BooleanProperty allPlayers;

    private final Map<Integer, ResourceLocation> textureMap = new HashMap<>();
    private final String TEXTURE_PATH = "assets/myau/textures/aurora/";

    public BAHalo() {
        super("BAHalo", false);
        
        List<String> textureNames = discoverTextures();
        if (textureNames.isEmpty()) {
            textureNames.add("None");
        }

        this.mode = new ModeProperty("Mode", 0, textureNames.toArray(new String[0]));
        this.firstPerson = new BooleanProperty("FirstPerson", true);
        this.allPlayers = new BooleanProperty("AllPlayers", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!firstPerson.getValue() && mc.gameSettings.thirdPersonView == 0) return;
        
        if (mode.getValue() == 0) return;

        ResourceLocation texture = textureMap.get(mode.getValue());
        if (texture == null) return;

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        float partialTicks = ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.color(1, 1, 1, 1);

        mc.getTextureManager().bindTexture(texture);

        if (allPlayers.getValue()) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == null || player.isDead || player.isInvisible()) continue;
                renderHat(player, partialTicks, renderPosX, renderPosY, renderPosZ);
            }
        } else {
            EntityPlayer player = mc.thePlayer;
            if (player != null && !player.isDead && !player.isInvisible()) {
                renderHat(player, partialTicks, renderPosX, renderPosY, renderPosZ);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void renderHat(EntityPlayer player, float partialTicks, double renderPosX, double renderPosY, double renderPosZ) {
        double posX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - renderPosX;
        double posY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - renderPosY;
        double posZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - renderPosZ;

        double hatHeight = player.getEntityBoundingBox().maxY - player.getEntityBoundingBox().minY + 0.2;
        double hatSize = 0.5;

        float time = (float) (System.currentTimeMillis() % 2000) / 1000.0f;
        float floatOffset = (float) Math.sin(time * Math.PI) * 0.1f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY + hatHeight + floatOffset, posZ);

        GlStateManager.rotate(-player.rotationYawHead, 0, 1, 0);
        GlStateManager.rotate(mc.getRenderManager().playerViewY, 0, 1, 0);

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex3d(-hatSize, 0, -hatSize);
        glTexCoord2f(1, 0); glVertex3d(hatSize, 0, -hatSize);
        glTexCoord2f(1, 1); glVertex3d(hatSize, 0, hatSize);
        glTexCoord2f(0, 1); glVertex3d(-hatSize, 0, hatSize);
        glEnd();

        GlStateManager.popMatrix();
    }

    private List<String> discoverTextures() {
        List<String> names = new ArrayList<>();
        textureMap.clear();
        
        names.add("None");
        textureMap.put(0, null);

        try {
            java.net.URL url = BAHalo.class.getClassLoader().getResource(TEXTURE_PATH);
            if (url != null) {
                if (url.getProtocol().equals("jar")) {
                    java.net.JarURLConnection connection = (java.net.JarURLConnection) url.openConnection();
                    JarFile jarFile = connection.getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(TEXTURE_PATH) && name.endsWith(".png") && name.length() > TEXTURE_PATH.length()) {
                            String textureName = name.substring(TEXTURE_PATH.length(), name.length() - 4);
                            try {
                                InputStream stream = BAHalo.class.getClassLoader().getResourceAsStream(name);
                                if (stream != null) {
                                    BufferedImage bufferedImage = ImageIO.read(stream);
                                    textureMap.put(names.size(), mc.renderEngine.getDynamicTextureLocation(textureName, new DynamicTexture(bufferedImage)));
                                    names.add(textureName);
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
                                String textureName = file.getName().substring(0, file.getName().length() - 4);
                                try {
                                    BufferedImage bufferedImage = ImageIO.read(file);
                                    textureMap.put(names.size(), mc.renderEngine.getDynamicTextureLocation(textureName, new DynamicTexture(bufferedImage)));
                                    names.add(textureName);
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

        return names;
    }
}