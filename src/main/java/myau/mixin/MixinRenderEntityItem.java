package myau.mixin;

import myau.Myau;
import myau.module.modules.ItemPhysics;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.client.renderer.GlStateManager.*;
import static net.minecraft.util.MathHelper.sin;

@Mixin(RenderEntityItem.class)
public abstract class MixinRenderEntityItem extends Render<EntityItem> {

    protected MixinRenderEntityItem(RenderManager renderManager) {
        super(renderManager);
    }

    @Inject(method = "doRender", at = @At("HEAD"))
    private void offsetToGround(EntityItem entity, double x, double y, double z,
                                float entityYaw, float partialTicks, CallbackInfo ci) {
        y -= 0.25F * 0.625F;
    }

    @Inject(method = "func_177077_a", at = @At("HEAD"), cancellable = true)
    private void onRenderItem(EntityItem itemIn, double x, double y, double z, float p_177077_8_,
                              IBakedModel ibakedmodel, CallbackInfoReturnable<Integer> cir) {
        ItemPhysics itemPhysics = (ItemPhysics) Myau.moduleManager.modules.get(ItemPhysics.class);
        if (itemPhysics == null || !itemPhysics.isEnabled()) {
            return;
        }

        ItemStack itemStack = itemIn.getEntityItem();
        Item item = itemStack.getItem();
        if (item == null) {
            cir.setReturnValue(0);
            return;
        }

        enableCull();
        cullFace(GL11.GL_BACK);

        boolean isGui3d = ibakedmodel.isGui3d();
        int count = getItemCount(itemStack);
        float yOffset = 0.05F;

        float age = (float) itemIn.getAge() + p_177077_8_;
        float hoverStart = itemIn.hoverStart;
        boolean isRealistic = itemPhysics.getRealistic();
        float weight = itemPhysics.getWeight();

        float sinValue = sin((age / 10.0F + hoverStart)) * 0.1F + 0.1F;
        sinValue = 0.0F;
        float scaleY = ibakedmodel.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y;

        translate((float) x, (float) y + sinValue + yOffset * scaleY, (float) z);

        if (isGui3d) translate(0, 0, -0.08F);
        else translate(0, 0, -0.04F);

        if (isGui3d || this.renderManager.options != null) {
            float rotationYaw = (age / 20.0F + hoverStart) * (180F / (float) Math.PI);
            rotationYaw *= itemPhysics.getRotationSpeed() * (1.0F + Math.min(age / 360.0F, 1.0F));

            if (itemIn.onGround) {
                GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                if (!isRealistic) GL11.glRotatef(itemIn.rotationYaw, 0.0F, 0.0F, 1.0F);
                else GL11.glRotatef(itemIn.rotationYaw, 0.0F, 1.0F, 0.6F);
            } else {
                for (int a = 0; a < 7; a++)
                    GL11.glRotatef(rotationYaw, weight, weight, 1.35F);
            }
        }

        if (!isGui3d) {
            float ox = -0.0F * (count - 1) * 0.5F;
            float oy = -0.0F * (count - 1) * 0.5F;
            float oz = -0.09375F * (count - 1) * 0.5F;
            translate(ox, oy, oz);
        }

        disableCull();
        color(1.0F, 1.0F, 1.0F, 1.0F);
        cir.setReturnValue(count);
    }

    private int getItemCount(ItemStack stack) {
        int size = stack.stackSize;
        if (size > 48) return 5;
        if (size > 32) return 4;
        if (size > 16) return 3;
        if (size > 1) return 2;
        return 1;
    }
}