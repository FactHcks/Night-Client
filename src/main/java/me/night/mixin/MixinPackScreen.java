package me.night.mixin;

import me.night.packconverter.PackConverterScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackScreen.class)
public abstract class MixinPackScreen extends Screen {
    protected MixinPackScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (this.client == null) return;

        // Adds direct shortcut to 1.8.9 Pack Converter inside Resource Packs menu
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§d1.8.9 Pack Converter"),
                btn -> this.client.setScreen(new PackConverterScreen(this))
        ).dimensions(this.width - 162, 8, 150, 20).build());
    }
}
