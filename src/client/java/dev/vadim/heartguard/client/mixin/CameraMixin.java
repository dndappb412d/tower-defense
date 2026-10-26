package dev.vadim.heartguard.client.mixin;

import dev.vadim.heartguard.client.CommanderView;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract Vec3 position();

	/** After vanilla aligns the camera with its entity, lift it above the player and look straight down. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void heartguard$topDown(float partialTicks, CallbackInfo ci) {
		if (!CommanderView.isActive()) {
			return;
		}
		Vec3 eye = this.position();
		this.setRotation(CommanderView.YAW, CommanderView.PITCH);
		this.setPosition(eye.add(0.0, CommanderView.HEIGHT, 0.0));
	}

	/** Report the camera as detached so the local player model renders from above. */
	@Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
	private void heartguard$detached(CallbackInfoReturnable<Boolean> cir) {
		if (CommanderView.isActive()) {
			cir.setReturnValue(true);
		}
	}
}
