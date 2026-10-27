package dev.vadim.heartguard.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vadim.heartguard.Heartguard;
import dev.vadim.heartguard.block.ModBlocks;
import dev.vadim.heartguard.client.ShopScreen;
import dev.vadim.heartguard.economy.Coins;
import dev.vadim.heartguard.match.Match;
import dev.vadim.heartguard.match.MatchData;
import dev.vadim.heartguard.match.MatchData.Phase;
import dev.vadim.heartguard.placement.PlaceTowerPayload;
import dev.vadim.heartguard.shop.Shop;
import dev.vadim.heartguard.shop.ShopDef;
import dev.vadim.heartguard.tower.TowerActionPayload;
import dev.vadim.heartguard.tower.TowerBlockEntity;
import dev.vadim.heartguard.tower.TowerDefs;
import dev.vadim.heartguard.wave.WaveSets;
import dev.vadim.heartguard.wave.WaveSpawner;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * The whole milestone 3 loop, played the way a player would, on a real client and server: mine veins by hand, buy a
 * pickaxe, mine faster, build towers, and hold the Heart through every wave of the shipped Survival set, spending the
 * money earned between waves. If the numbers in the data files stop adding up, this is the test that fails.
 */
public class FullLoopClientTest implements FabricClientGameTest {
	private static final int MINE_TIMEOUT_TICKS = 1500;
	private static final int WAVE_TIMEOUT_TICKS = 4000;

	private TestSingleplayerContext world;
	private ClientGameTestContext context;
	private BlockPos origin;
	private final List<BlockPos> veins = new ArrayList<>();
	private final List<BlockPos> towers = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		try (TestSingleplayerContext created = context.worldBuilder().create()) {
			this.world = created;
			world.getClientLevel().waitForChunksRender();
			origin = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos heart = origin.offset(16, 0, 0);
			BlockPos spawn = origin.offset(-16, 0, 0);
			BlockPos quartermaster = origin.offset(0, 0, -6);

			try {
				setUpArena(heart, quartermaster);

				// --- before the match: the first money, the first pickaxe, the first towers ---
				int handTicks = mineWithHand(veins.get(0));
				check(coins() >= price(Items.STONE_PICKAXE), "one rich vein should pay for the first pickaxe: have " + coins());
				buy(Items.STONE_PICKAXE, quartermaster);
				equip(Items.STONE_PICKAXE);
				int pickaxeTicks = mine(veins.get(2));
				log("mining a rich vein: " + handTicks + " ticks by hand, " + pickaxeTicks + " with a stone pickaxe");
				check(pickaxeTicks < handTicks, "the pickaxe investment should make mining faster: " + pickaxeTicks + " vs " + handTicks);
				for (BlockPos vein : veins) {
					if (!isDepleted(vein)) {
						mine(vein);
					}
				}
				log("coins after mining everything once: " + coins());
				build(TowerDefs.ARROW, origin.offset(-4, 0, 6));
				build(TowerDefs.ARROW, origin.offset(4, 0, 6));
				context.takeScreenshot("loop-defences-ready");

				int total = WaveSets.active().waves().size();
				world.getServer().runOnServer(server ->
					Match.update(server, d -> MatchData.EMPTY.withHeart(heart).withSpawn(spawn).started(total).advanced()));

				// --- the waves ---
				for (int wave = 1; wave <= total; wave++) {
					int before = coins();
					int hpBefore = heartHp();
					waitWhile(Phase.WAVE);
					Phase after = phase();
					log("wave " + wave + ": phase " + after + ", Heart " + heartHp() + "/" + MatchData.MAX_HEART_HP
						+ " (was " + hpBefore + "), coins " + before + " -> " + coins());
					context.takeScreenshot("loop-wave-" + wave);
					check(after != Phase.LOST, "wave " + wave + " broke the Heart: the defences bought with this economy are not enough");
					if (after == Phase.WON) {
						break;
					}

					// The veins regrew at the payout. Mine them again and spend everything on the next wave.
					waitWhile(Phase.PAYOUT);
					returnToPost();
					for (BlockPos vein : veins) {
						check(!isDepleted(vein), "every vein should have regrown at the payout, " + vein + " is still empty");
						mine(vein);
					}
					log("coins to spend before wave " + (wave + 1) + ": " + coins());
					spendOnDefences(wave);
					world.getServer().runOnServer(server -> Match.update(server, MatchData::advanced));
				}

				MatchData end = world.getServer().computeOnServer(Match::get);
				check(end.phase() == Phase.WON, "the player should win the shipped Survival set, phase " + end.phase());
				check(end.heartHp() > 0, "and the Heart should still stand");
				log("won with the Heart at " + end.heartHp() + "/" + MatchData.MAX_HEART_HP + " and " + coins() + " coins unspent");
				context.takeScreenshot("loop-won");
			} finally {
				world.getServer().runOnServer(server -> {
					WaveSets.setActive(WaveSets.DEFAULT_ID);
					Match.update(server, d -> MatchData.EMPTY);
					for (Entity entity : List.copyOf(toList(server))) {
						if (entity.entityTags().contains(WaveSpawner.WAVE_MOB_TAG)) {
							entity.discard();
						}
					}
				});
			}
		}
	}

	// --- the arena ---

	/** Eight veins in two rows beside the player (the first, third and last rich), the shop behind, and the Heart down the path. */
	private void setUpArena(BlockPos heart, BlockPos quartermaster) {
		int[] xs = {-3, -1, 1, 3};
		for (int row : new int[] {3, -3}) {
			for (int i = 0; i < xs.length; i++) {
				veins.add(origin.offset(xs[i], 0, row));
			}
		}
		world.getServer().runOnServer(server -> {
			for (int i = 0; i < veins.size(); i++) {
				// even positions rich, odd poor: four of each
				Block kind = i % 2 == 0 ? ModBlocks.RICH_VEIN : ModBlocks.POOR_VEIN;
				server.overworld().setBlock(veins.get(i), kind.defaultBlockState(), Block.UPDATE_ALL);
			}
			server.overworld().setBlock(heart, ModBlocks.HEART.defaultBlockState(), Block.UPDATE_ALL);
			server.overworld().setBlock(quartermaster, ModBlocks.QUARTERMASTER.defaultBlockState(), Block.UPDATE_ALL);
		});
		context.waitTicks(10);
	}

	// --- player actions ---

	/** Walks the player back to the spot beside the veins; the wave mobs shove them around on their way past. */
	private void returnToPost() {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			player.connection.teleport(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0.0F, 0.0F);
		});
		context.waitTicks(5);
	}

	private int mineWithHand(BlockPos vein) {
		return mine(vein);
	}

	/** Looks at a vein and holds the attack button until it is mined out. Returns how many ticks that took. */
	private int mine(BlockPos vein) {
		context.getInput().lookAt(vein);
		context.waitTicks(2);
		context.getInput().holdMouse(0);
		int ticks = 0;
		while (!isDepleted(vein) && ticks < MINE_TIMEOUT_TICKS) {
			context.waitTick();
			ticks++;
			// The victory screen opens when the last wave pays out, and it takes the mouse; dismiss it and carry on.
			if (context.computeOnClient(mc -> mc.screen instanceof dev.vadim.heartguard.client.EndScreen)) {
				context.runOnClient(mc -> mc.setScreen(null));
				context.getInput().releaseMouse(0);
				context.getInput().lookAt(vein);
				context.waitTicks(2);
				context.getInput().holdMouse(0);
			}
		}
		context.getInput().releaseMouse(0);
		check(isDepleted(vein), "the vein at " + vein + " should have been mined out within " + MINE_TIMEOUT_TICKS + " ticks; " + playerState());
		return ticks;
	}

	private void buy(Item item, BlockPos quartermaster) {
		world.getServer().runOnServer(server -> Shop.open(server.getPlayerList().getPlayers().get(0), quartermaster));
		context.waitForScreen(ShopScreen.class);
		context.waitTicks(3);
		List<ShopDef.Entry> items = ShopDef.get(ShopDef.DEFAULT_ID).items();
		int index = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).item() == item) {
				index = i;
			}
		}
		int chosen = index;
		double[] at = context.computeOnClient(mc -> ((ShopScreen) mc.screen).windowCentreOf(chosen));
		int before = coins();
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(0);
		context.waitTicks(5);
		check(before - coins() == items.get(chosen).price(), "buying " + item + " should cost " + items.get(chosen).price() + ", coins went from " + before + " to " + coins());
		context.runOnClient(mc -> mc.setScreen(null));
		context.waitTicks(3);
	}

	private void equip(Item item) {
		int slot = world.getServer().computeOnServer(server -> {
			var inventory = server.getPlayerList().getPlayers().get(0).getInventory();
			for (int i = 0; i < 9; i++) {
				if (inventory.getItem(i).is(item)) {
					return i;
				}
			}
			return -1;
		});
		check(slot >= 0, item + " should be on the hotbar after buying it");
		context.getInput().pressKey(InputConstants.KEY_1 + slot);
		context.waitTicks(3);
	}

	private void build(Identifier tower, BlockPos pos) {
		int before = coins();
		context.runOnClient(mc -> ClientPlayNetworking.send(new PlaceTowerPayload(tower, pos)));
		context.waitTicks(5);
		boolean placed = world.getServer().computeOnServer(server -> server.overworld().getBlockEntity(pos) instanceof TowerBlockEntity);
		check(placed, "the " + tower.getPath() + " tower should have been built at " + pos + " (coins were " + before + ")");
		towers.add(pos);
	}

	private void upgrade(BlockPos pos) {
		context.runOnClient(mc -> ClientPlayNetworking.send(new TowerActionPayload(pos, TowerActionPayload.Action.UPGRADE)));
		context.waitTicks(4);
	}

	/**
	 * What a sensible player does with the money between waves: the first break buys a cannon and a frost tower, later
	 * ones upgrade whatever is cheapest to upgrade.
	 */
	private void spendOnDefences(int afterWave) {
		if (afterWave == 1) {
			if (coins() >= cost(TowerDefs.CANNON)) {
				build(TowerDefs.CANNON, origin.offset(0, 0, 7));
			}
			if (coins() >= cost(TowerDefs.FROST)) {
				build(TowerDefs.FROST, origin.offset(-8, 0, 5));
			}
		}
		for (int round = 0; round < 6; round++) {
			BlockPos best = null;
			int bestCost = Integer.MAX_VALUE;
			for (BlockPos tower : towers) {
				int cost = upgradeCost(tower);
				if (cost > 0 && cost <= coins() && cost < bestCost) {
					best = tower;
					bestCost = cost;
				}
			}
			if (best == null) {
				break;
			}
			upgrade(best);
		}
		log("defences before wave " + (afterWave + 1) + ": " + describeTowers() + ", coins left " + coins());
	}

	// --- reading the world ---

	private int coins() {
		return world.getServer().computeOnServer(server -> Coins.balance(server.getPlayerList().getPlayers().get(0)));
	}

	private int heartHp() {
		return world.getServer().computeOnServer(server -> Match.get(server).heartHp());
	}

	private Phase phase() {
		return world.getServer().computeOnServer(server -> Match.get(server).phase());
	}

	private boolean isDepleted(BlockPos pos) {
		return world.getServer().computeOnServer(server -> server.overworld().getBlockState(pos).is(ModBlocks.DEPLETED_VEIN));
	}

	private String playerState() {
		return context.computeOnClient(mc -> "screen=" + mc.screen + ", health=" + mc.player.getHealth() + ", alive=" + mc.player.isAlive()
			+ ", pos=" + mc.player.position() + ", slot=" + mc.player.getInventory().getSelectedSlot()
			+ ", held=" + mc.player.getMainHandItem() + ", hit=" + mc.hitResult + ", block=" + mc.level.getBlockState(veins.get(0)));
	}

	private static int price(Item item) {
		return ShopDef.get(ShopDef.DEFAULT_ID).items().stream().filter(e -> e.item() == item).findFirst().orElseThrow().price();
	}

	private static int cost(Identifier tower) {
		return TowerDefs.get(tower).cost();
	}

	/** Coins the next upgrade of the tower at {@code pos} costs, or 0 when it is at the top tier. */
	private int upgradeCost(BlockPos pos) {
		return world.getServer().computeOnServer(server -> {
			if (server.overworld().getBlockEntity(pos) instanceof TowerBlockEntity tower && tower.def() != null && tower.tier() < tower.def().maxTier()) {
				return tower.def().tier(tower.tier() + 1).upgradeCost();
			}
			return 0;
		});
	}

	private String describeTowers() {
		return world.getServer().computeOnServer(server -> {
			StringBuilder out = new StringBuilder();
			for (BlockPos pos : towers) {
				if (server.overworld().getBlockEntity(pos) instanceof TowerBlockEntity tower) {
					out.append(tower.getBlockState().getBlock() instanceof dev.vadim.heartguard.tower.TowerBlock b ? b.towerId().getPath() : "?")
						.append(" t").append(tower.tier()).append("; ");
				}
			}
			return out.toString();
		});
	}

	/** Lets the game run until the match leaves the given phase. */
	private void waitWhile(Phase phase) {
		int ticks = 0;
		while (phase() == phase && ticks < WAVE_TIMEOUT_TICKS) {
			context.waitTick();
			ticks++;
		}
		check(phase() != phase, "the " + phase + " phase should have ended within " + WAVE_TIMEOUT_TICKS + " ticks");
	}

	private static List<Entity> toList(net.minecraft.server.MinecraftServer server) {
		List<Entity> all = new ArrayList<>();
		server.overworld().getAllEntities().forEach(all::add);
		return all;
	}

	private static void log(String message) {
		Heartguard.LOGGER.info("[full loop] {}", message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
