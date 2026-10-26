package dev.vadim.heartguard.client.mixin;

import dev.vadim.heartguard.client.CommanderView;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** The cursor replaces the crosshair in commander view. */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void heartguard$hideCrosshair(CallbackInfo ci) {
		if (CommanderView.isActive()) {
			ci.cancel();
		}
	}
}
