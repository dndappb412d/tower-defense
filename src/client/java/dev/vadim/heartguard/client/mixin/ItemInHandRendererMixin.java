package dev.vadim.heartguard.client.mixin;

import dev.vadim.heartguard.client.CommanderView;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	/** No first-person hand while looking down from the commander camera. */
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void heartguard$hideHands(CallbackInfo ci) {
		if (CommanderView.isActive()) {
			ci.cancel();
		}
	}
}
