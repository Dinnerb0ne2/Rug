package myau.mixin;

import myau.module.modules.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    @Shadow @Final public List<ChatLine> drawnChatLines;
    @Shadow @Final private List<ChatLine> chatLines;
    @Shadow protected Minecraft mc;
    @Shadow private int scrollPos;
    @Shadow private boolean isScrolled;

    @Shadow public int getLineCount() { return 0; }
    @Shadow public boolean getChatOpen() { return false; }
    @Shadow public float getChatScale() { return 0; }
    @Shadow public int getChatWidth() { return 0; }

    @Inject(method = "drawChat", at = @At("HEAD"), cancellable = true)
    private void onDrawChat(int updateCounter, CallbackInfo ci) {
        if (Chat.INSTANCE != null && Chat.INSTANCE.isEnabled()) {
            ci.cancel();
            drawChatCustom(updateCounter);
        }
    }

    private void drawChatCustom(int updateCounter) {
        int lineCount = this.getLineCount();
        int drawnSize = this.drawnChatLines.size();

        if (drawnSize <= 0) return;

        boolean chatOpen = this.getChatOpen();
        float scale = this.getChatScale();
        int maxLines = chatOpen ? lineCount : Math.min(lineCount, 10);
        boolean flag = this.isScrolled;

        GlStateManager.pushMatrix();
        GlStateManager.translate(2.0F, 8.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);

        for (int i = 0; i + this.scrollPos < drawnSize && i < maxLines; i++) {
            ChatLine line = this.drawnChatLines.get(i + this.scrollPos);

            if (line == null) continue;

            int age = updateCounter - line.getUpdatedCounter();

            if (age >= 200 && !flag && !chatOpen) continue;

            double d0 = (double) age / 200.0D;
            double d1 = (flag || chatOpen) ? 1.0D : 1.0D - d0;
            double d2 = d1 * 10.0D;
            if (d2 < 0.0D) d2 = 0.0D;
            if (d2 > 1.0D) d2 = 1.0D;
            d2 = d2 * d2;
            int alpha = (int) (255.0D * d2);

            if (alpha <= 3) continue;

            int y = -i * 9;
            String text = line.getChatComponent().getFormattedText();

            GlStateManager.enableBlend();
            this.mc.fontRendererObj.drawStringWithShadow(text, 0.0F, (float) (y - 8), 16777215 + (alpha << 24));
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }
}