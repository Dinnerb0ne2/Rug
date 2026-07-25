package myau.mixin;

import myau.Myau;
import myau.module.modules.KeepSprint;
import myau.module.modules.Particles;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayer.class}, priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {

    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(doubleValue = 0.6)}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (Myau.moduleManager == null) {
            return speed;
        } else {
            KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
            return keepSprint.isEnabled() && keepSprint.shouldKeepSprint()
                    ? speed + (1.0 - speed) * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0)
                    : speed;
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V")
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (Myau.moduleManager != null) {
            KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
            if (!keepSprint.isEnabled() || !keepSprint.shouldKeepSprint()) {
                entityPlayer.setSprinting(boolean2);
            }
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;onCriticalHit(Lnet/minecraft/entity/Entity;)V")
    )
    private void redirectCriticalHit(EntityPlayer entityPlayer, Entity targetEntity) {
        if (!Particles.shouldOverrideParticles()) {
            entityPlayer.onCriticalHit(targetEntity);
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;onEnchantmentCritical(Lnet/minecraft/entity/Entity;)V")
    )
    private void redirectEnchantmentCritical(EntityPlayer entityPlayer, Entity targetEntity) {
        if (!Particles.shouldOverrideParticles()) {
            entityPlayer.onEnchantmentCritical(targetEntity);
        }
    }

    @Inject(method = {"attackTargetEntityWithCurrentItem"}, at = @At("RETURN"))
    private void onAttackReturn(Entity targetEntity, CallbackInfo ci) {
        if (!Particles.shouldOverrideParticles()) return;
        EntityPlayer player = (EntityPlayer)(Object)this;
        if (player != Minecraft.getMinecraft().thePlayer) return;
        if (targetEntity == null || !targetEntity.canAttackWithItem() || targetEntity.hitByEntity(player)) return;

        boolean isCrit = player.fallDistance > 0.0F && !player.onGround && !player.isOnLadder() && !player.isInWater() && !player.isPotionActive(Potion.blindness) && player.ridingEntity == null;
        boolean isSharp = false;
        if (targetEntity instanceof EntityLivingBase && player.getHeldItem() != null) {
            int sharp = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, player.getHeldItem());
            int smite = EnchantmentHelper.getEnchantmentLevel(Enchantment.smite.effectId, player.getHeldItem());
            int bane = EnchantmentHelper.getEnchantmentLevel(Enchantment.baneOfArthropods.effectId, player.getHeldItem());
            isSharp = sharp > 0 || smite > 0 || bane > 0;
        }

        if (Particles.alwaysCriticals()) isCrit = true;
        if (Particles.alwaysSharpness()) isSharp = true;

        int critMult = Particles.getCriticalsMultiplier(isCrit);
        int sharpMult = Particles.getSharpnessMultiplier(isSharp);

        for (int i = 0; i < critMult; i++) {
            player.onCriticalHit(targetEntity);
        }

        for (int i = 0; i < sharpMult; i++) {
            player.onEnchantmentCritical(targetEntity);
        }
    }
}