package dev.vadim.heartguard.client.mixin;

import dev.vadim.heartguard.client.CommanderPicker;
import dev.vadim.heartguard.client.CommanderView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/**
	 * Replace the crosshair pick with the cursor pick, so vanilla draws its block outline where the cursor points.
	 * Clicks never reach the world while the commander screen is open, so this is display and intent input only.
	 */
	@Inject(method = "pick", at = @At("TAIL"))
	private void heartguard$cursorPick(float partialTicks, CallbackInfo ci) {
		if (!CommanderView.isActive()) {
			CommanderView.setCursorHit(null);
			return;
		}
		Minecraft minecraft = (Minecraft)(Object)this;
		BlockHitResult hit = CommanderPicker.pick(minecraft);
		CommanderView.setCursorHit(hit);
		minecraft.hitResult = hit;
	}
}
