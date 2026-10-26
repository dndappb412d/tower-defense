package dev.vadim.heartguard.mob;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Straight-line flight toward the wanted position, ignoring terrain pathing. Used for fliers whose own movement is
 * unusable here: the phantom's control flies toward a private target point and the stock flying control needs a
 * flying-speed attribute the phantom does not have. The speed is in blocks per tick.
 */
public class HeartFlightControl extends MoveControl {
	private final double blocksPerTick;

	public HeartFlightControl(Mob mob, double blocksPerTick) {
		super(mob);
		this.blocksPerTick = blocksPerTick;
	}

	@Override
	public void tick() {
		if (operation != MoveControl.Operation.MOVE_TO) {
			return;
		}
		mob.setNoGravity(true);
		Vec3 toTarget = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
		double distance = toTarget.length();
		Vec3 velocity = mob.getDeltaMovement();
		if (distance < 0.3) {
			mob.setDeltaMovement(velocity.scale(0.5));
			return;
		}
		Vec3 wanted = toTarget.scale(blocksPerTick * speedModifier / distance);
		mob.setDeltaMovement(velocity.add(wanted.subtract(velocity).scale(0.3)));

		float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * 180.0 / Math.PI) - 90.0F;
		mob.setYRot(rotlerp(mob.getYRot(), yaw, 30.0F));
		mob.yBodyRot = mob.getYRot();
		double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		mob.setXRot(rotlerp(mob.getXRot(), (float) (-(Mth.atan2(toTarget.y, horizontal) * 180.0 / Math.PI)), 20.0F));
	}
}
