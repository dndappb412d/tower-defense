package dev.vadim.heartguard.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Turns the mouse cursor into a world ray and finds the block under it. Client-only, read-only. */
public final class CommanderPicker {
	/** Longer than the maximum camera height, so a straight-down ray always reaches the ground. */
	private static final double RANGE = 400.0;

	private CommanderPicker() {}

	public record Ray(Vec3 origin, Vec3 direction) {}

	/** Ray from the camera through a cursor position, given in window (screen) coordinates. */
	public static Ray rayFromCursor(Camera camera, double mouseX, double mouseY, int screenWidth, int screenHeight) {
		float ndcX = (float)(2.0 * mouseX / screenWidth - 1.0);
		float ndcY = (float)(1.0 - 2.0 * mouseY / screenHeight);
		// The matrix is view rotation * projection (no translation), so unprojected points are camera-relative.
		// Any two NDC depths lie on the same ray, so this holds for both zero-to-one and -1..1 depth ranges.
		Matrix4f inverse = camera.getViewRotationProjectionMatrix(new Matrix4f()).invert();
		Vector3f near = inverse.transformProject(ndcX, ndcY, 0.0F, new Vector3f());
		Vector3f far = inverse.transformProject(ndcX, ndcY, 1.0F, new Vector3f());
		Vector3f direction = far.sub(near).normalize();
		return new Ray(camera.position(), new Vec3(direction.x, direction.y, direction.z));
	}

	/** The block currently under the cursor, or null when the ray hits nothing. */
	public static @Nullable BlockHitResult pick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return null;
		}
		Window window = minecraft.getWindow();
		Ray ray = rayFromCursor(
			minecraft.gameRenderer.getMainCamera(),
			minecraft.mouseHandler.xpos(),
			minecraft.mouseHandler.ypos(),
			window.getScreenWidth(),
			window.getScreenHeight()
		);
		Vec3 end = ray.origin().add(ray.direction().scale(RANGE));
		HitResult hit = minecraft.level.clip(
			new ClipContext(ray.origin(), end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player)
		);
		return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
	}
}
