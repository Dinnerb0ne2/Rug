package myau.mixin;

import myau.Myau;
import myau.module.modules.CustomCape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(priority = 1001, value = LayerCape.class)
public abstract class MixinLayerCape {
    @Final
    @Shadow
    private RenderPlayer playerRenderer;

    /**
     * @author myau
     * @reason custom cape rendering
     */
    @Overwrite
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        CustomCape customCape = Myau.moduleManager != null ? (CustomCape) Myau.moduleManager.modules.get(CustomCape.class) : null;
        if (player.hasPlayerInfo() && !player.isInvisible() && (player.isWearing(EnumPlayerModelParts.CAPE) && player.getLocationCape() != null || player.equals(Minecraft.getMinecraft().thePlayer) && customCape != null && customCape.isEnabled() && customCape.getCapeLocation() != null)) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (player.equals(Minecraft.getMinecraft().thePlayer) && customCape != null && customCape.isEnabled() && customCape.getCapeLocation() != null) {
                this.playerRenderer.bindTexture(customCape.getCapeLocation());
            } else {
                this.playerRenderer.bindTexture(player.getLocationCape());
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 0.0F, 0.125F);
            double d0 = player.prevChasingPosX + (player.chasingPosX - player.prevChasingPosX) * (double)partialTicks - (player.prevPosX + (player.posX - player.prevPosX) * (double)partialTicks);
            double d1 = player.prevChasingPosY + (player.chasingPosY - player.prevChasingPosY) * (double)partialTicks - (player.prevPosY + (player.posY - player.prevPosY) * (double)partialTicks);
            double d2 = player.prevChasingPosZ + (player.chasingPosZ - player.prevChasingPosZ) * (double)partialTicks - (player.prevPosZ + (player.posZ - player.prevPosZ) * (double)partialTicks);
            float f = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
            double d3 = (double)MathHelper.sin(f * 3.1415927F / 180.0F);
            double d4 = (double)(-MathHelper.cos(f * 3.1415927F / 180.0F));
            float f1 = (float)d1 * 10.0F;
            f1 = MathHelper.clamp_float(f1, -6.0F, 32.0F);
            float f2 = (float)(d0 * d3 + d2 * d4) * 100.0F;
            float f3 = (float)(d0 * d4 - d2 * d3) * 100.0F;
            if (f2 < 0.0F) {
                f2 = 0.0F;
            }

            float f4 = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks;
            f1 += MathHelper.sin((player.prevDistanceWalkedModified + (player.distanceWalkedModified - player.prevDistanceWalkedModified) * partialTicks) * 6.0F) * 32.0F * f4;
            if (player.isSneaking()) {
                f1 += 25.0F;
            }

            GlStateManager.rotate(6.0F + f2 / 2.0F + f1, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(f3 / 2.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(-f3 / 2.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            this.playerRenderer.getMainModel().renderCape(0.0625F);
            GlStateManager.popMatrix();
        }
    }
}