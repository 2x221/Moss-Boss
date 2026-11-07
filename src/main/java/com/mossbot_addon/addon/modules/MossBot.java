package com.mossbot_addon.addon.modules;

import com.mossbot_addon.addon.mossbot_addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.*;

public class MossBot extends Module {
    private static final Set<Block> ALL_FULL_LIGHT_BLOCKS = Set.of(
        Blocks.GLOWSTONE,
        Blocks.SEA_LANTERN,
        Blocks.JACK_O_LANTERN,
        Blocks.SHROOMLIGHT,
        Blocks.OCHRE_FROGLIGHT,
        Blocks.VERDANT_FROGLIGHT,
        Blocks.PEARLESCENT_FROGLIGHT,
        Blocks.CRYING_OBSIDIAN,
        Blocks.MAGMA_BLOCK,
        Blocks.BEACON,
        Blocks.COPPER_BULB,
        Blocks.EXPOSED_COPPER_BULB,
        Blocks.WEATHERED_COPPER_BULB,
        Blocks.OXIDIZED_COPPER_BULB,
        Blocks.WAXED_COPPER_BULB,
        Blocks.WAXED_EXPOSED_COPPER_BULB,
        Blocks.WAXED_WEATHERED_COPPER_BULB,
        Blocks.WAXED_OXIDIZED_COPPER_BULB
    );

    public static final Set<Block> CONVERTIBLE_BLOCKS = Set.of(
        Blocks.STONE,
        Blocks.DEEPSLATE,
        Blocks.GRANITE,
        Blocks.DIORITE,
        Blocks.ANDESITE,
        Blocks.TUFF,
        Blocks.DIRT,
        Blocks.COARSE_DIRT,
        Blocks.PODZOL,
        Blocks.MYCELIUM,
        Blocks.ROOTED_DIRT
    );

    public enum AzaleaMode {
        SafeSpread,
        FastSpread
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSeed = settings.createGroup("Seed Starter");
    private final SettingGroup sgAzalea = settings.createGroup("Azalea Tree Spreader");
    private final SettingGroup sgCrafting = settings.createGroup("Auto Crafting");
    private final SettingGroup sgLighting = settings.createGroup("Spawn Proofing");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("moss-range")
        .description("Reach range for moss.")
        .defaultValue(5.0)
        .min(1.0)
        .max(6.0)
        .sliderMin(1.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Tick delay between actions.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .build()
    );

    private final Setting<Integer> actionsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("actions-per-tick")
        .description("Blocks to bonemeal per tick.")
        .defaultValue(4)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> fovAngle = sgGeneral.add(new IntSetting.Builder()
        .name("fov-angle")
        .description("Max angle from crosshair.")
        .defaultValue(180)
        .min(15)
        .max(180)
        .sliderMin(15)
        .sliderMax(180)
        .build()
    );

    private final Setting<Boolean> breakGrass = sgGeneral.add(new BoolSetting.Builder()
        .name("break-grass")
        .description("Break grass on top of moss.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards target block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Pause while eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoPlaceSeed = sgSeed.add(new BoolSetting.Builder()
        .name("auto-place-seed")
        .description("Place a moss block if none are nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bonemealAzalea = sgAzalea.add(new BoolSetting.Builder()
        .name("enable-azalea-spreading")
        .description("Bonemeal azalea bushes into trees.")
        .defaultValue(false)
        .build()
    );

    private final Setting<AzaleaMode> azaleaMode = sgAzalea.add(new EnumSetting.Builder<AzaleaMode>()
        .name("azalea-mode")
        .description("Tree growth mode.")
        .defaultValue(AzaleaMode.SafeSpread)
        .visible(bonemealAzalea::get)
        .build()
    );

    private final Setting<Double> azaleaRange = sgAzalea.add(new DoubleSetting.Builder()
        .name("azalea-range")
        .description("Reach range for azaleas.")
        .defaultValue(6.0)
        .min(1.0)
        .max(6.0)
        .sliderMin(1.0)
        .sliderMax(6.0)
        .visible(bonemealAzalea::get)
        .build()
    );

    private final Setting<Integer> azaleasPerTick = sgAzalea.add(new IntSetting.Builder()
        .name("azaleas-per-tick")
        .description("Azaleas to bonemeal per tick.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .visible(bonemealAzalea::get)
        .build()
    );

    private final Setting<Boolean> autoUnstuck = sgAzalea.add(new BoolSetting.Builder()
        .name("auto-unstuck")
        .description("Break leaves and logs if trapped by a tree.")
        .defaultValue(true)
        .visible(bonemealAzalea::get)
        .build()
    );

    private final Setting<Boolean> craftBoneBlocks = sgCrafting.add(new BoolSetting.Builder()
        .name("craft-bone-blocks")
        .description("Craft bone blocks into bone meal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> craftBlocksPerTick = sgCrafting.add(new IntSetting.Builder()
        .name("craft-blocks-per-tick")
        .description("Bone blocks to craft per cycle.")
        .defaultValue(4)
        .min(1)
        .max(64)
        .sliderMin(1)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> craftLimitPercent = sgCrafting.add(new IntSetting.Builder()
        .name("craft-limit-percent")
        .description("Max inventory space to fill with bone meal.")
        .defaultValue(50)
        .min(1)
        .max(100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> spawnProof = sgLighting.add(new BoolSetting.Builder()
        .name("spawn-proof")
        .description("Place light blocks on dark spots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxBlockLight = sgLighting.add(new IntSetting.Builder()
        .name("max-block-light")
        .description("Place light when block light is at or below this value.")
        .defaultValue(0)
        .min(0)
        .max(14)
        .sliderMin(0)
        .sliderMax(14)
        .visible(spawnProof::get)
        .build()
    );

    private final Setting<Boolean> lightWhitelistMode = sgLighting.add(new BoolSetting.Builder()
        .name("light-whitelist-mode")
        .description("Only use light blocks from the whitelist.")
        .defaultValue(false)
        .visible(spawnProof::get)
        .build()
    );

    private final Setting<List<Block>> lightWhitelist = sgLighting.add(new BlockListSetting.Builder()
        .name("light-whitelist")
        .description("Allowed light blocks.")
        .defaultValue(
            Blocks.GLOWSTONE,
            Blocks.SEA_LANTERN,
            Blocks.JACK_O_LANTERN,
            Blocks.SHROOMLIGHT,
            Blocks.OCHRE_FROGLIGHT,
            Blocks.VERDANT_FROGLIGHT,
            Blocks.PEARLESCENT_FROGLIGHT
        )
        .filter(ALL_FULL_LIGHT_BLOCKS::contains)
        .visible(() -> spawnProof.get() && lightWhitelistMode.get())
        .build()
    );

    private int timer = 0;
    private int craftCooldown = 0;
    private int seedCooldown = 0;
    private boolean pendingCraftCollect = false;
    private int pendingCraftTimeout = 0;
    private final Map<BlockPos, Integer> recentBonemealed = new HashMap<>();

    public MossBot() {
        super(
            mossbot_addon.CATEGORY,
            "moss-bot",
            "Automatically expands moss and safely grows azalea trees"
        );
    }

    @Override
    public void onDeactivate() {
        pendingCraftCollect = false;
        pendingCraftTimeout = 0;
        recentBonemealed.clear();
    }

    public static boolean isEating(MinecraftClient mc) {
        if (mc.player == null) return false;
        if (mc.player.isUsingItem() && isFood(mc.player.getActiveItem())) return true;
        if (mc.options.useKey.isPressed()) {
            return isFood(mc.player.getMainHandStack()) || isFood(mc.player.getOffHandStack());
        }
        return false;
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.get(DataComponentTypes.FOOD) != null || stack.get(DataComponentTypes.CONSUMABLE) != null) return true;
        UseAction action = stack.getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!recentBonemealed.isEmpty()) {
            recentBonemealed.entrySet().removeIf(e -> {
                e.setValue(e.getValue() - 1);
                return e.getValue() <= 0;
            });
        }

        if (pauseOnEat.get() && isEating(mc)) return;

        if (craftCooldown > 0) craftCooldown--;
        if (seedCooldown > 0) seedCooldown--;

        MossPilot pilot = Modules.get().get(MossPilot.class);
        boolean pilotActive = pilot != null && pilot.isActive();

        if (craftBoneBlocks.get() || pilotActive) {
            handleBoneMealCrafting();
        }

        if (bonemealAzalea.get() && autoUnstuck.get()) {
            if (tryUnstuckPlayer()) return;
        }

        if (spawnProof.get()) {
            trySpawnProofArea();
        }

        BlockPos playerPos = mc.player.getBlockPos();
        double effectiveMossRange = pilotActive ? Math.max(range.get(), 5.0) : range.get();
        double maxR = Math.max(effectiveMossRange, bonemealAzalea.get() ? azaleaRange.get() : effectiveMossRange);
        int searchRadius = (int) Math.ceil(maxR);
        double rangeSq = effectiveMossRange * effectiveMossRange;
        double azaleaRangeSq = bonemealAzalea.get() ? azaleaRange.get() * azaleaRange.get() : 0;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();
        double minCosAngle = Math.cos(Math.toRadians(fovAngle.get()));

        List<TargetCandidate> mossCandidates = new ArrayList<>();
        List<TargetCandidate> azaleaCandidates = new ArrayList<>();
        boolean hasExpandableMossNearby = false;

        for (BlockPos pos : BlockPos.iterate(
            playerPos.add(-searchRadius, -searchRadius, -searchRadius),
            playerPos.add(searchRadius, searchRadius, searchRadius)
        )) {
            if (mc.world == null) continue;
            Vec3d center = Vec3d.ofCenter(pos);
            double distSq = eyePos.squaredDistanceTo(center);

            BlockState state = mc.world.getBlockState(pos);
            boolean isMoss = state.isOf(Blocks.MOSS_BLOCK);
            boolean isAzalea = bonemealAzalea.get() && (state.isOf(Blocks.AZALEA) || state.isOf(Blocks.FLOWERING_AZALEA));

            if (!isMoss && !isAzalea) continue;

            BlockPos immutablePos = pos.toImmutable();

            if (isMoss && distSq <= rangeSq) {
                int convScore = countConvertibleNeighbors(immutablePos);
                if (convScore > 0) {
                    hasExpandableMossNearby = true;

                    if (!recentBonemealed.containsKey(immutablePos)) {
                        if (!pilotActive && fovAngle.get() < 180) {
                            Vec3d toBlock = center.subtract(eyePos).normalize();
                            if (lookVec.dotProduct(toBlock) < minCosAngle) continue;
                        }

                        BlockPos abovePos = immutablePos.up();
                        BlockState aboveState = mc.world.getBlockState(abovePos);
                        boolean hasGrassAbove = isBreakableVegetation(aboveState);

                        if (aboveState.isAir() || (breakGrass.get() && hasGrassAbove)) {
                            double score = distSq - (convScore * 2.0);
                            mossCandidates.add(new TargetCandidate(immutablePos, hasGrassAbove, score));
                        }
                    }
                }
                continue;
            }

            if (isAzalea && !recentBonemealed.containsKey(immutablePos)) {
                if (!pilotActive && fovAngle.get() < 180) {
                    Vec3d toBlock = center.subtract(eyePos).normalize();
                    if (lookVec.dotProduct(toBlock) < minCosAngle) continue;
                }

                if (distSq <= azaleaRangeSq && hasAzaleaGrowthSpace(immutablePos)) {
                    Vec3d playerLook2D = new Vec3d(lookVec.x, 0, lookVec.z).normalize();
                    Vec3d toAzalea2D = new Vec3d(center.x - eyePos.x, 0, center.z - eyePos.z).normalize();
                    double forwardDot = playerLook2D.dotProduct(toAzalea2D);

                    if (azaleaMode.get() == AzaleaMode.SafeSpread) {
                        if (forwardDot <= 0.1) {
                            azaleaCandidates.add(new TargetCandidate(immutablePos, false, rayDistSq(eyePos, lookVec, center)));
                        }
                    } else {
                        double distFromPlayerHoriz = Math.sqrt(Math.pow(center.x - eyePos.x, 2) + Math.pow(center.z - eyePos.z, 2));
                        if (distFromPlayerHoriz > 1.35 || forwardDot <= 0.25) {
                            azaleaCandidates.add(new TargetCandidate(immutablePos, false, rayDistSq(eyePos, lookVec, center)));
                        }
                    }
                }
            }
        }

        if (!hasExpandableMossNearby && (autoPlaceSeed.get() || pilotActive) && seedCooldown == 0) {
            BlockPos seeded = tryPlaceSeedMoss(playerPos, rangeSq, eyePos, lookVec, minCosAngle, pilotActive);
            if (seeded != null) {
                mossCandidates.add(new TargetCandidate(seeded, false, -100.0));
                seedCooldown = 3;
            }
        }

        FindItemResult bonemeal = getAvailableBonemeal();
        if (!bonemeal.found()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        int actionsDone = 0;
        int maxActions = pilotActive ? Math.max(actionsPerTick.get(), 4) : actionsPerTick.get();

        if (!mossCandidates.isEmpty()) {
            mossCandidates.sort(Comparator.comparingDouble(c -> c.rayDistSq));

            boolean swapped = false;
            for (TargetCandidate candidate : mossCandidates) {
                if (actionsDone >= maxActions) break;

                if (breakGrass.get() && candidate.hasGrassAbove) {
                    BlockPos abovePos = candidate.pos.up();
                    if (mc.player.squaredDistanceTo(Vec3d.ofCenter(abovePos)) <= rangeSq) {
                        if (swapped) {
                            InvUtils.swapBack();
                            swapped = false;
                        }
                        BlockUtils.breakBlock(abovePos, true);
                        actionsDone++;
                        continue;
                    }
                }

                if (!swapped) {
                    InvUtils.swap(bonemeal.slot(), true);
                    swapped = true;
                }

                BlockHitResult hitResult = new BlockHitResult(
                    Vec3d.ofCenter(candidate.pos),
                    Direction.UP,
                    candidate.pos,
                    false
                );

                BlockUtils.interact(hitResult, Hand.MAIN_HAND, rotate.get() && actionsDone == 0);
                recentBonemealed.put(candidate.pos, 7);
                actionsDone++;
            }

            if (swapped) {
                InvUtils.swapBack();
            }
        }

        int azaleasDone = 0;
        int maxAzaleas = azaleasPerTick.get();
        if (!azaleaCandidates.isEmpty()) {
            azaleaCandidates.sort(Comparator.comparingDouble(c -> c.rayDistSq));

            InvUtils.swap(bonemeal.slot(), true);
            for (TargetCandidate candidate : azaleaCandidates) {
                if (azaleasDone >= maxAzaleas) break;

                BlockHitResult hitResult = new BlockHitResult(
                    Vec3d.ofCenter(candidate.pos),
                    Direction.UP,
                    candidate.pos,
                    false
                );

                BlockUtils.interact(hitResult, Hand.MAIN_HAND, rotate.get() && azaleasDone == 0);
                recentBonemealed.put(candidate.pos, 20);
                azaleasDone++;
            }
            InvUtils.swapBack();
        }

        if (actionsDone > 0 || azaleasDone > 0) {
            timer = delaySetting.get();
        }
    }

    private boolean tryUnstuckPlayer() {
        ClientWorld world = mc.world;
        if (mc.player == null || world == null) return false;

        BlockPos feetPos = mc.player.getBlockPos();
        BlockPos headPos = feetPos.up();

        if (isTrappingTreeBlock(world.getBlockState(headPos))) {
            BlockUtils.breakBlock(headPos, true);
            return true;
        }
        if (isTrappingTreeBlock(world.getBlockState(feetPos))) {
            BlockUtils.breakBlock(feetPos, true);
            return true;
        }

        Direction[] horizDirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        int blockedSides = 0;
        List<BlockPos> candidateObstacles = new ArrayList<>();

        for (Direction dir : horizDirs) {
            BlockPos adjFeet = feetPos.offset(dir);
            BlockPos adjHead = headPos.offset(dir);
            BlockState feetState = world.getBlockState(adjFeet);
            BlockState headState = world.getBlockState(adjHead);

            boolean feetBlocked = isBlockingMovement(feetState, adjFeet);
            boolean headBlocked = isBlockingMovement(headState, adjHead);

            if (feetBlocked || headBlocked) {
                blockedSides++;
                if (headBlocked && isTrappingTreeBlock(headState)) {
                    candidateObstacles.add(adjHead.toImmutable());
                }
                if (feetBlocked && isTrappingTreeBlock(feetState)) {
                    candidateObstacles.add(adjFeet.toImmutable());
                }
            }
        }

        Direction lookDir = mc.player.getHorizontalFacing();
        BlockPos frontHead = headPos.offset(lookDir);
        BlockPos frontFeet = feetPos.offset(lookDir);
        boolean frontTrappedByTree = isTrappingTreeBlock(world.getBlockState(frontHead))
            || isTrappingTreeBlock(world.getBlockState(frontFeet));

        if ((blockedSides >= 3 || (frontTrappedByTree && mc.player.input != null && mc.player.input.hasForwardMovement()))
            && !candidateObstacles.isEmpty()) {

            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();

            candidateObstacles.sort(
                Comparator.comparingInt((BlockPos p) -> getBreakPriority(world.getBlockState(p)))
                    .thenComparingDouble(p -> rayDistSq(eyePos, lookVec, Vec3d.ofCenter(p)))
            );

            BlockUtils.breakBlock(candidateObstacles.getFirst(), true);
            return true;
        }

        return false;
    }

    private boolean isBlockingMovement(BlockState state, BlockPos pos) {
        if (mc.world == null) return false;
        return !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean isTrappingTreeBlock(BlockState state) {
        if (state.isAir()) return false;
        return state.isIn(BlockTags.LEAVES)
            || state.isOf(Blocks.AZALEA_LEAVES)
            || state.isOf(Blocks.FLOWERING_AZALEA_LEAVES)
            || state.isOf(Blocks.MOSS_CARPET)
            || state.isOf(Blocks.AZALEA)
            || state.isOf(Blocks.FLOWERING_AZALEA)
            || state.isIn(BlockTags.LOGS)
            || state.isOf(Blocks.OAK_LOG);
    }

    private int getBreakPriority(BlockState state) {
        if (state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.AZALEA) || state.isOf(Blocks.FLOWERING_AZALEA)) {
            return 0;
        }
        if (state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.AZALEA_LEAVES) || state.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return 1;
        }
        return 2;
    }

    private BlockPos tryPlaceSeedMoss(BlockPos playerPos, double rangeSq, Vec3d eyePos, Vec3d lookVec, double minCosAngle, boolean ignoreFov) {
        FindItemResult mossBlock = InvUtils.findInHotbar(Items.MOSS_BLOCK);
        if (!mossBlock.found()) {
            FindItemResult invMoss = InvUtils.find(Items.MOSS_BLOCK);
            if (invMoss.found()) {
                InvUtils.move().from(invMoss.slot()).toHotbar(2);
                mossBlock = InvUtils.findInHotbar(Items.MOSS_BLOCK);
            }
        }
        if (!mossBlock.found()) return null;

        int searchRadius = (int) Math.ceil(Math.sqrt(rangeSq));
        BlockPos bestPlacePos = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
            playerPos.add(-searchRadius, -searchRadius, -searchRadius),
            playerPos.add(searchRadius, searchRadius, searchRadius)
        )) {
            if (mc.world == null) continue;
            Vec3d center = Vec3d.ofCenter(pos);
            double distSq = eyePos.squaredDistanceTo(center);
            if (distSq > rangeSq) continue;

            if (!ignoreFov && fovAngle.get() < 180) {
                Vec3d toBlock = center.subtract(eyePos).normalize();
                if (lookVec.dotProduct(toBlock) < minCosAngle) continue;
            }

            BlockState state = mc.world.getBlockState(pos);
            BlockPos placePos = pos.up().toImmutable();

            if (CONVERTIBLE_BLOCKS.contains(state.getBlock())
                && mc.world.getBlockState(placePos).isAir()
                && BlockUtils.canPlace(placePos, true)) {

                int neighbors = countConvertibleNeighbors(pos);
                if (neighbors >= 2) {
                    double score = distSq - (neighbors * 2.0);
                    if (score < bestScore) {
                        bestScore = score;
                        bestPlacePos = placePos;
                    }
                }
            }
        }

        if (bestPlacePos != null) {
            if (BlockUtils.place(bestPlacePos, mossBlock, rotate.get(), 50, true)) {
                return bestPlacePos;
            }
        }
        return null;
    }

    private double rayDistSq(Vec3d eyePos, Vec3d lookVec, Vec3d center) {
        double projection = center.subtract(eyePos).dotProduct(lookVec);
        Vec3d closestPointOnRay = eyePos.add(lookVec.multiply(Math.max(0.0, projection)));
        return center.squaredDistanceTo(closestPointOnRay);
    }

    private boolean isBreakableVegetation(BlockState state) {
        return state.isOf(Blocks.SHORT_GRASS)
            || state.isOf(Blocks.FERN)
            || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.LARGE_FERN);
    }

    public int countConvertibleNeighbors(BlockPos mossPos) {
        if (mc.world == null) return 0;
        int convertibleCount = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos checkPos = mossPos.add(dx, dy, dz);
                    if (CONVERTIBLE_BLOCKS.contains(mc.world.getBlockState(checkPos).getBlock())) {
                        BlockState above = mc.world.getBlockState(checkPos.up());
                        if (above.isAir() || isBreakableVegetation(above)) {
                            convertibleCount++;
                            if (convertibleCount >= 6) return convertibleCount;
                        }
                    }
                }
            }
        }
        return convertibleCount;
    }

    private boolean hasAzaleaGrowthSpace(BlockPos azaleaPos) {
        if (mc.world == null) return false;
        for (int dy = 1; dy <= 5; dy++) {
            BlockState state = mc.world.getBlockState(azaleaPos.up(dy));
            if (!state.isAir() && !state.isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    private void trySpawnProofArea() {
        if (mc.player == null || mc.world == null) return;

        FindItemResult lightItem = getAvailableLightBlock();
        if (!lightItem.found()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int searchRadius = (int) Math.ceil(range.get());
        double rangeSq = range.get() * range.get();
        int lightLimit = maxBlockLight.get();

        BlockPos bestPos = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
            playerPos.add(-searchRadius, -searchRadius, -searchRadius),
            playerPos.add(searchRadius, searchRadius, searchRadius)
        )) {
            if (mc.world == null) continue;
            double distSq = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distSq > rangeSq || distSq >= closestDistSq) continue;

            if (!mc.world.getBlockState(pos).isAir()) continue;

            BlockPos floorPos = pos.down();
            BlockState floorState = mc.world.getBlockState(floorPos);
            if (ALL_FULL_LIGHT_BLOCKS.contains(floorState.getBlock())) continue;
            if (!floorState.isSideSolidFullSquare(mc.world, floorPos, Direction.UP)) continue;

            if (mc.world.getLightLevel(LightType.BLOCK, pos) <= lightLimit && BlockUtils.canPlace(pos, true)) {
                bestPos = pos.toImmutable();
                closestDistSq = distSq;
            }
        }

        if (bestPos != null) {
            BlockUtils.place(bestPos, lightItem, rotate.get(), 50, true);
        }
    }

    private FindItemResult getAvailableLightBlock() {
        FindItemResult hotbarLight = InvUtils.findInHotbar(this::isValidLightStack);
        if (hotbarLight.found()) return hotbarLight;

        FindItemResult invLight = InvUtils.find(this::isValidLightStack);
        if (invLight.found()) {
            InvUtils.move().from(invLight.slot()).toHotbar(1);
            return InvUtils.findInHotbar(this::isValidLightStack);
        }

        return invLight;
    }

    private boolean isValidLightStack(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (!ALL_FULL_LIGHT_BLOCKS.contains(block)) return false;
        return !lightWhitelistMode.get() || lightWhitelist.get().contains(block);
    }

    private FindItemResult getAvailableBonemeal() {
        FindItemResult hotbarBonemeal = InvUtils.findInHotbar(Items.BONE_MEAL);
        if (hotbarBonemeal.found()) return hotbarBonemeal;

        FindItemResult invBonemeal = InvUtils.find(Items.BONE_MEAL);
        if (invBonemeal.found()) {
            InvUtils.move().from(invBonemeal.slot()).toHotbar(0);
            return InvUtils.findInHotbar(Items.BONE_MEAL);
        }

        return invBonemeal;
    }

    private void handleBoneMealCrafting() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

        if (pendingCraftCollect) {
            pendingCraftTimeout++;
            if (handler.getSlot(0).hasStack()) {
                mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                if (handler.getSlot(1).hasStack()) {
                    mc.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player);
                }
                pendingCraftCollect = false;
                pendingCraftTimeout = 0;
                craftCooldown = 4;
                return;
            }
            if (pendingCraftTimeout > 8) {
                if (handler.getSlot(1).hasStack()) {
                    mc.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player);
                }
                pendingCraftCollect = false;
                pendingCraftTimeout = 0;
                craftCooldown = 4;
            }
            return;
        }

        if (craftCooldown > 0) return;

        FindItemResult boneBlocks = InvUtils.find(Items.BONE_BLOCK);
        if (!boneBlocks.found()) return;

        int totalMainSlots = 36;
        int otherItemSlots = 0;
        int emptySlots = 0;
        int currentBoneMealItems = 0;
        int partialBoneMealSpace = 0;

        for (int i = 0; i < totalMainSlots; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                emptySlots++;
            } else if (stack.isOf(Items.BONE_MEAL)) {
                currentBoneMealItems += stack.getCount();
                partialBoneMealSpace += (stack.getMaxCount() - stack.getCount());
            } else {
                otherItemSlots++;
            }
        }

        int availableSlotsForBonemeal = totalMainSlots - otherItemSlots;
        if (availableSlotsForBonemeal <= 0) return;

        int targetMaxBoneMeal = (int) Math.floor((availableSlotsForBonemeal * 64) * (craftLimitPercent.get() / 100.0));
        int neededBoneMeal = targetMaxBoneMeal - currentBoneMealItems;

        int physicalSpace = (emptySlots * 64) + partialBoneMealSpace;
        int allowedAdditionalBoneMeal = Math.min(neededBoneMeal, physicalSpace);

        if (allowedAdditionalBoneMeal < 3) return;

        ItemStack sourceStack = mc.player.getInventory().getStack(boneBlocks.slot());
        int blocksInSlot = sourceStack.getCount();

        int maxConvertibleBlocks = Math.min(blocksInSlot, allowedAdditionalBoneMeal / 3);
        int blocksThisTick = Math.min(maxConvertibleBlocks, craftBlocksPerTick.get());

        if (blocksThisTick <= 0) return;

        int screenSlotId = boneBlocks.slot() < 9 ? boneBlocks.slot() + 36 : boneBlocks.slot();

        mc.interactionManager.clickSlot(handler.syncId, screenSlotId, 0, SlotActionType.PICKUP, mc.player);

        if (blocksThisTick == blocksInSlot) {
            mc.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.PICKUP, mc.player);
        } else {
            for (int i = 0; i < blocksThisTick; i++) {
                mc.interactionManager.clickSlot(handler.syncId, 1, 1, SlotActionType.PICKUP, mc.player);
            }
            mc.interactionManager.clickSlot(handler.syncId, screenSlotId, 0, SlotActionType.PICKUP, mc.player);
        }

        pendingCraftCollect = true;
        pendingCraftTimeout = 0;
    }

    private record TargetCandidate(BlockPos pos, boolean hasGrassAbove, double rayDistSq) {}
}
