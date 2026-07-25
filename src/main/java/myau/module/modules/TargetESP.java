package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.ModeProperty;
import myau.util.PlayerUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class TargetESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode;
    public final BooleanProperty onlyKillAura;

    public final ColorProperty jelloColor;
    public final ColorProperty vapeNormalColor;
    public final ColorProperty vapeHitColor;
    public final ColorProperty ravenColor;

    private static EntityLivingBase target = null;
    private long lastTargetTime = -1;

    public TargetESP() {
        super("TargetESP", false);
        this.mode = new ModeProperty("Mode", 0, new String[]{"Raven", "Jello", "Vape"});
        this.onlyKillAura = new BooleanProperty("Only-KillAura", true);
        this.jelloColor = new ColorProperty("Jello-Color", 0xFFFFFFFF);
        this.vapeNormalColor = new ColorProperty("Vape-Normal-Color", 0xFF6464BE);
        this.vapeHitColor = new ColorProperty("Vape-Hit-Color", 0xFFFF0000);
        this.ravenColor = new ColorProperty("Raven-Color", 0xFFFFFFFF);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            target = null;
            return;
        }

        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            target = ka.getTarget();
            lastTargetTime = System.currentTimeMillis();
        }

        if (target != null && (target.isDead || System.currentTimeMillis() - lastTargetTime > 5000 || target.getDistanceSqToEntity(mc.thePlayer) > 20)) {
            target = null;
            lastTargetTime = -1;
        }

        if (onlyKillAura.getValue()) return;

        if (PlayerUtil.isAttacking() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            target = (EntityLivingBase) mc.objectMouseOver.entityHit;
            lastTargetTime = System.currentTimeMillis();
        }

        if (target != null && target.getDistanceSqToEntity(mc.thePlayer) > 36) {
            target = null;
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || target == null) return;
        int currentMode = mode.getValue();
        if (currentMode == 0) {
            renderRaven(target);
        } else if (currentMode == 1) {
            renderJello(target, event.getPartialTicks());
        } else if (currentMode == 2) {
            renderVape(target, event.getPartialTicks());
        }
    }

    private void renderRaven(EntityLivingBase target) {
        Color c = new Color(target.hurtTime > 0 ? 0xFFFF0000 : ravenColor.getValue(), true);
        RenderUtil.enableRenderState();
        RenderUtil.drawEntityBox(target, c.getRed(), c.getGreen(), c.getBlue());
        RenderUtil.disableRenderState();
    }

    private void renderJello(EntityLivingBase target, float pt) {
        int drawTime = (int) (System.currentTimeMillis() % 2000);
        boolean drawMode = drawTime > 1000;
        float drawPercent = drawTime / 1000f;

        if (!drawMode) {
            drawPercent = 1 - drawPercent;
        } else {
            drawPercent -= 1;
        }

        drawPercent = drawPercent * 2;

        if (drawPercent < 1) {
            drawPercent = 0.5f * drawPercent * drawPercent * drawPercent;
        } else {
            float f = drawPercent - 2;
            drawPercent = 0.5f * (f * f * f + 2);
        }

        mc.entityRenderer.disableLightmap();
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        mc.entityRenderer.disableLightmap();

        double radius = target.width;
        double height = target.height + 0.1;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * pt - mc.getRenderManager().viewerPosY + height * drawPercent;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
        double eased = (height / 3) * ((drawPercent > 0.5) ? 1 - drawPercent : drawPercent) * ((drawMode) ? -1 : 1);

        Color color = new Color(jelloColor.getValue(), true);

        for (int segments = 0; segments < 360; segments += 5) {
            double x1 = x - Math.sin(segments * Math.PI / 180F) * radius;
            double z1 = z + Math.cos(segments * Math.PI / 180F) * radius;
            double x2 = x - Math.sin((segments - 5) * Math.PI / 180F) * radius;
            double z2 = z + Math.cos((segments - 5) * Math.PI / 180F) * radius;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 0.0f);
            GL11.glVertex3d(x1, y + eased, z1);
            GL11.glVertex3d(x2, y + eased, z2);
            GL11.glColor4f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
            GL11.glVertex3d(x2, y, z2);
            GL11.glVertex3d(x1, y, z1);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(x2, y, z2);
            GL11.glVertex3d(x1, y, z1);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private void renderVape(EntityLivingBase target, float pt) {
        mc.entityRenderer.disableLightmap();
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        mc.entityRenderer.disableLightmap();

        double radius = target.width;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * pt - mc.getRenderManager().viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
        double eased = target.height - 0.2;

        Color normal = new Color(vapeNormalColor.getValue(), true);
        Color hit = new Color(vapeHitColor.getValue(), true);
        Color c = target.hurtTime > 0 ? hit : normal;

        for (int segments = 0; segments < 360; segments += 5) {
            double x1 = x - Math.sin(segments * Math.PI / 180F) * radius;
            double z1 = z + Math.cos(segments * Math.PI / 180F) * radius;
            double x2 = x - Math.sin((segments - 5) * Math.PI / 180F) * radius;
            double z2 = z + Math.cos((segments - 5) * Math.PI / 180F) * radius;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, c.getAlpha() / 255.0f);
            GL11.glVertex3d(x1, y, z1);
            GL11.glVertex3d(x2, y, z2);
            GL11.glColor4f(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 0.0f);
            GL11.glVertex3d(x2, y + eased, z2);
            GL11.glVertex3d(x1, y + eased, z1);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(x2, y + eased, z2);
            GL11.glVertex3d(x1, y + eased, z1);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}