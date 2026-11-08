package com.mossbot_addon.addon.modules;

import com.mossbot_addon.addon.mossbot_addon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 __        __  _    _  __    __       __      _____   ______     __    __   ____    _    _
 \ \      / / | |  | | \ \  / /      /  \    |  __ \ |  ____|    \ \  / /  / __ \  | |  | |
  \ \ /\ / /  | |__| |  \ \/ /      / /\ \   | |__) || |__        \ \/ /  | |  | | | |  | |
   \ V  V /   |  __  |   |  |      / ____ \  |  _  / |  __|        |  |   | |__| | | |__| |
    \_/\_/    |_|  |_|   |__|     /_/    \_\ |_| \_\ |______|      |__|    \____/   \____/

   _____    ____    __  __   _____   ______   _______   _____   _   _    _____
  / ____|  / __ \  |  \/  | |  __ \ |  ____| |__   __| |_   _| | \ | |  / ____|
 | |      | |  | | | \  / | | |__) || |__       | |      | |   |  \| | | |  __
 | |____  | |__| | | |\/| | |  ___/ |  __|      | |     _| |_  | |\  | | | |_ |
  \_____|  \____/  |_|  |_| |_|     |______|    |_|    |_____| |_| \_|  \_____|
*/

@SuppressWarnings("unused")
public class MossPilot extends Module {
    public enum PilotMode {
        PlateauSurfer,
        LawnmowerGrid,
        ExpandingSpiral,
        ManualControl
    }

    public enum RestockStage {
        NONE,
        APPROACH_PAD,
        LANDING,
        PREP_SPOT,
        PLACE_SHULKER,
        OPEN_SHULKER,
        LOOT_SHULKER,
        BREAK_SHULKER,
        COLLECT_SHULKER,
        PLACE_ECHEST_1,
        PLACE_ECHEST_2,
        OPEN_ECHEST,
        LOOT_ECHEST,
        BREAK_ECHEST_1,
        BREAK_ECHEST_2,
        TAKEOFF
    }

    public enum FleeState {
        IDLE,
        FLEEING_BURST,
        DOGLEG_EVASION,
        DISCONNECTED
    }

    private static final Set<Item> JUNK = Set.of(
        Items.WHEAT_SEEDS, Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN,
        Items.DEAD_BUSH, Items.ROTTEN_FLESH, Items.STRING, Items.ARROW, Items.SPIDER_EYE,
        Items.GRAVEL, Items.FLINT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE,
        Items.DIORITE, Items.ANDESITE, Items.GRANITE, Items.TUFF, Items.NETHERRACK, Items.DIRT
    );

    private static final Set<Block> IGNORED_STRUCTURES = Set.of(
        Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.BEDROCK, Blocks.COBWEB, Blocks.BARRIER, Blocks.NETHER_PORTAL
    );

    private static final Set<Block> PASSABLE_PLANTS = Set.of(
        Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.MOSS_CARPET
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Anti-Slam & Auto-Eat");
    private final SettingGroup sgScanner = settings.createGroup("Pathing & Scanner");
    private final SettingGroup sgFlight = settings.createGroup("Flight & Speed");
    private final SettingGroup sgRestock = settings.createGroup("Restock");
    private final SettingGroup sgFlee = settings.createGroup("Auto Flee");

    private final Setting<PilotMode> pilotMode = sgGeneral.add(new EnumSetting.Builder<PilotMode>()
        .name("mode")
        .description("Pathing mode.")
        .defaultValue(PilotMode.PlateauSurfer)
        .build()
    );

    private final Setting<Boolean> syncMossBot = sgGeneral.add(new BoolSetting.Builder()
        .name("sync-moss-bot")
        .description("Turn on Moss Bot automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Hover in place while eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnManualInput = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-override")
        .description("Pause pathing while pressing movement keys.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Show status messages in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiSlam = sgSafety.add(new BoolSetting.Builder()
        .name("anti-slam")
        .description("Brake horizontal speed before hitting walls or steep terrain to prevent kinetic damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> antiSlamDistance = sgSafety.add(new DoubleSetting.Builder()
        .name("slam-distance")
        .description("Look-ahead distance in blocks to start braking.")
        .defaultValue(2.5)
        .min(1.0)
        .max(6.0)
        .sliderMin(1.2)
        .sliderMax(4.5)
        .visible(antiSlam::get)
        .build()
    );

    private final Setting<Double> antiSlamMinSpeed = sgSafety.add(new DoubleSetting.Builder()
        .name("slam-min-speed")
        .description("Safe speed near obstacles (0.18 prevents elytra kinetic damage).")
        .defaultValue(0.18)
        .min(0.05)
        .max(0.35)
        .sliderMin(0.08)
        .sliderMax(0.30)
        .visible(antiSlam::get)
        .build()
    );

    private final Setting<Boolean> autoEat = sgSafety.add(new BoolSetting.Builder()
        .name("auto-eat")
        .description("Automatically eat gapples or food when health or hunger is low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> eatHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("eat-health")
        .description("Eat a gapple when health drops at or below this value.")
        .defaultValue(14.0)
        .min(4.0)
        .max(20.0)
        .sliderMin(6.0)
        .sliderMax(18.0)
        .visible(autoEat::get)
        .build()
    );

    private final Setting<Integer> eatHunger = sgSafety.add(new IntSetting.Builder()
        .name("eat-hunger")
        .description("Eat when hunger drops at or below this level.")
        .defaultValue(14)
        .min(4)
        .max(19)
        .sliderMin(6)
        .sliderMax(18)
        .visible(autoEat::get)
        .build()
    );

    private final Setting<Boolean> matchRenderDistance = sgScanner.add(new BoolSetting.Builder()
        .name("match-render-distance")
        .description("Scan out to your client render distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanRange = sgScanner.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Max scan radius in blocks.")
        .defaultValue(160)
        .min(32)
        .max(384)
        .sliderMin(48)
        .sliderMax(256)
        .visible(() -> !matchRenderDistance.get())
        .build()
    );

    private final Setting<Boolean> avoidWater = sgScanner.add(new BoolSetting.Builder()
        .name("avoid-water")
        .description("Skip water and lava areas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> neverReturn = sgScanner.add(new BoolSetting.Builder()
        .name("never-return")
        .description("Never fly back to finished coordinates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> laneWidth = sgScanner.add(new DoubleSetting.Builder()
        .name("lane-width")
        .description("Distance between lanes.")
        .defaultValue(8.0)
        .min(4.0)
        .max(14.0)
        .sliderMin(5.0)
        .sliderMax(12.0)
        .visible(() -> pilotMode.get() != PilotMode.PlateauSurfer)
        .build()
    );

    private final Setting<Integer> passLength = sgScanner.add(new IntSetting.Builder()
        .name("pass-length")
        .description("Pass length for grid mode.")
        .defaultValue(56)
        .min(16)
        .max(256)
        .sliderMin(24)
        .sliderMax(160)
        .visible(() -> pilotMode.get() == PilotMode.LawnmowerGrid)
        .build()
    );

    private final Setting<Double> workSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("work-speed")
        .description("Speed while spreading moss.")
        .defaultValue(0.68)
        .min(0.15)
        .max(2.5)
        .sliderMin(0.25)
        .sliderMax(1.6)
        .build()
    );

    private final Setting<Double> cruiseSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("cruise-speed")
        .description("Speed when commuting across non-mossable terrain.")
        .defaultValue(1.15)
        .min(0.2)
        .max(3.5)
        .sliderMin(0.4)
        .sliderMax(2.5)
        .build()
    );

    private final Setting<Boolean> autoPace = sgFlight.add(new BoolSetting.Builder()
        .name("auto-pace-seed")
        .description("Briefly dip speed when planting a new seed on bare stone so the patch catches.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("v-speed")
        .description("Up and down speed.")
        .defaultValue(0.55)
        .min(0.15)
        .max(1.5)
        .sliderMin(0.2)
        .sliderMax(1.2)
        .build()
    );

    private final Setting<Double> hoverClearance = sgFlight.add(new DoubleSetting.Builder()
        .name("height")
        .description("Height above ground.")
        .defaultValue(2.0)
        .min(1.2)
        .max(4.5)
        .sliderMin(1.4)
        .sliderMax(3.5)
        .build()
    );

    private final Setting<Boolean> autoTakeoff = sgFlight.add(new BoolSetting.Builder()
        .name("auto-takeoff")
        .description("Jump and open elytra automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useRocketBoost = sgFlight.add(new BoolSetting.Builder()
        .name("use-rockets")
        .description("Use fireworks on takeoff if needed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoRestock = sgRestock.add(new BoolSetting.Builder()
        .name("auto-restock")
        .description("Pull moss and bone blocks from shulkers and echests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> targetMossStacks = sgRestock.add(new IntSetting.Builder()
        .name("moss-stacks")
        .description("Stacks of moss to keep in inventory.")
        .defaultValue(2)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Integer> bonemealFillPercent = sgRestock.add(new IntSetting.Builder()
        .name("bonemeal-percent")
        .description("How much of inventory to fill with bone meal/blocks.")
        .defaultValue(50)
        .min(10)
        .max(90)
        .build()
    );

    private final Setting<Integer> echestMossShulkers = sgRestock.add(new IntSetting.Builder()
        .name("echest-moss-shulkers")
        .description("Moss shulkers to grab from echest.")
        .defaultValue(3)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Integer> echestBoneShulkers = sgRestock.add(new IntSetting.Builder()
        .name("echest-bone-shulkers")
        .description("Bone shulkers to grab from echest.")
        .defaultValue(3)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Boolean> autoFleeEnabled = sgFlee.add(new BoolSetting.Builder()
        .name("auto-flee")
        .description("Fly away from players, log out once clear, and reconnect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fleeTriggerRange = sgFlee.add(new DoubleSetting.Builder()
        .name("range")
        .description("Trigger distance for players.")
        .defaultValue(80.0)
        .min(20.0)
        .max(128.0)
        .visible(autoFleeEnabled::get)
        .build()
    );

    private final Setting<Double> fleeSpeed = sgFlee.add(new DoubleSetting.Builder()
        .name("flee-speed")
        .description("Flight speed while running.")
        .defaultValue(1.15)
        .min(0.5)
        .max(3.0)
        .visible(autoFleeEnabled::get)
        .build()
    );

    private final Setting<Integer> clearWaitSeconds = sgFlee.add(new IntSetting.Builder()
        .name("clear-wait")
        .description("Seconds out of render before logging.")
        .defaultValue(5)
        .min(3)
        .max(15)
        .visible(autoFleeEnabled::get)
        .build()
    );

    private final Setting<Integer> reconnectDelaySeconds = sgFlee.add(new IntSetting.Builder()
        .name("reconnect-time")
        .description("Seconds to wait before reconnecting.")
        .defaultValue(120)
        .min(15)
        .max(600)
        .visible(autoFleeEnabled::get)
        .build()
    );

    private final Map<Long, SectorInfo> sectorMap = new HashMap<>();
    private final Set<Long> deadSectors = new HashSet<>();
    private final Set<Long> visitedCoords = new HashSet<>();
    private int scanRingRadius = 1;
    private int scanRingStep = 0;

    private Vec3d startPos = null;
    private double plateauY = Double.NaN;
    private Vec3d forwardVec = new Vec3d(0, 0, 1);
    private Vec3d rightVec = new Vec3d(-1, 0, 0);
    private Vec3d moveHeading = new Vec3d(0, 0, 1);
    private Vec3d targetPos = null;

    private int gridStep = 0;
    private int spiralStep = 0;
    private Vec3d spiralPos = null;
    private int targetAgeTicks = 0;
    private int antiStuckTicks = 0;
    private int pitEscapeTicks = 0;
    private double pitEscapeTargetY = Double.NaN;
    private int bypassTicks = 0;
    private Vec3d bypassDir = null;
    private Vec3d prevPos = null;

    private double curSpeed = 0.5;
    private int lagbackTicks = 0;
    private int takeoffTicks = 0;
    private int manualTicks = 0;

    private boolean isAutoEating = false;
    private int autoEatTicks = 0;
    private boolean swappedForEat = false;

    private RestockStage restockStage = RestockStage.NONE;
    private RestockPad restockPad = null;
    private int stageTicks = 0;
    private int totalRestockTicks = 0;
    private int restockCooldown = 0;
    private int clickDelay = 0;
    private boolean grabMossNext = true;
    private boolean echestEmpty = false;
    private int prevShulkers = 0;
    private BlockPos shulkerPos = null;
    private BlockPos echestPos1 = null;
    private BlockPos echestPos2 = null;

    private FleeState fleeState = FleeState.IDLE;
    private Vec3d fleeVec = new Vec3d(1, 0, 0);
    private int clearTicks = 0;
    private long logTime = 0;
    private ServerInfo lastServer = null;
    private int joinWait = 0;
    private ScheduledExecutorService reconnectTimer = null;

    public MossPilot() {
        super(
            mossbot_addon.CATEGORY,
            "moss-pilot",
            "Auto elytra fly, plateau corridor surfer, anti-slam, and restock for Moss Bot."
        );
    }

    @Override
    public void onActivate() {
        startReconnectThread();

        if (mc.player == null || mc.world == null) return;

        curSpeed = workSpeed.get();
        restockStage = RestockStage.NONE;
        fleeState = FleeState.IDLE;
        stageTicks = 0;
        totalRestockTicks = 0;
        restockCooldown = 0;
        echestEmpty = false;
        lagbackTicks = 0;
        takeoffTicks = 0;
        manualTicks = 0;
        antiStuckTicks = 0;
        pitEscapeTicks = 0;
        bypassTicks = 0;
        bypassDir = null;
        prevPos = null;
        stopAutoEating();

        sectorMap.clear();
        deadSectors.clear();
        visitedCoords.clear();
        scanRingRadius = 1;
        scanRingStep = 0;

        if (syncMossBot.get()) {
            MossBot bot = Modules.get().get(MossBot.class);
            if (bot != null && !bot.isActive()) {
                bot.toggle();
            }
        }

        resetGridHere();
    }

    @Override
    public void onDeactivate() {
        stopReconnectThread();
        stopAutoEating();

        if (mc.player != null) {
            mc.player.setNoGravity(false);
            mc.player.setVelocity(0, 0, 0);
        }

        restockStage = RestockStage.NONE;
        fleeState = FleeState.IDLE;
        shulkerPos = null;
        echestPos1 = null;
        echestPos2 = null;
        restockPad = null;
    }

    public boolean isBusyRestockingOrFleeing() {
        return restockStage != RestockStage.NONE || fleeState != FleeState.IDLE || isAutoEating;
    }

    public void resetGridHere() {
        if (mc.player == null) return;
        startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        plateauY = estimateInitialPlateauY(mc.player.getBlockPos());

        Direction dir = mc.player.getHorizontalFacing();
        forwardVec = new Vec3d(dir.getOffsetX(), 0, dir.getOffsetZ());
        Direction right = dir.rotateYClockwise();
        rightVec = new Vec3d(right.getOffsetX(), 0, right.getOffsetZ());
        moveHeading = forwardVec;

        gridStep = 0;
        spiralStep = 0;
        spiralPos = startPos;
        targetAgeTicks = 0;
        scanRingRadius = 1;
        scanRingStep = 0;

        targetPos = pickNextTarget();
        if (chatFeedback.get()) {
            info("Set start at %d, %d, %d (%s)",
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dir.asString());
        }
    }

    public void setPilotModeFromCommand(PilotMode mode) {
        pilotMode.set(mode);
        resetGridHere();
    }

    public void triggerManualRestock() {
        if (restockStage == RestockStage.NONE && fleeState == FleeState.IDLE) {
            prevShulkers = countAllShulkers();
            echestEmpty = false;
            restockCooldown = 0;
            restockPad = findSafeRestockPad();
            restockStage = restockPad != null ? RestockStage.APPROACH_PAD : RestockStage.LANDING;
            stageTicks = 0;
            totalRestockTicks = 0;
            if (chatFeedback.get()) info("Restocking...");
        }
    }

    public String getStatusSummary() {
        if (!isActive()) return "OFF";
        if (fleeState != FleeState.IDLE) return "FLEE";
        if (isAutoEating) return "EATING";
        if (restockStage != RestockStage.NONE) return "RESTOCK";
        if (manualTicks > 0) return "MANUAL";
        return pilotMode.get().name();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (fleeState == FleeState.DISCONNECTED) {
            fleeState = FleeState.IDLE;
            joinWait = 60;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            curSpeed = workSpeed.get() * 0.7;
            lagbackTicks = 4;
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.getCurrentServerEntry() != null) {
            lastServer = mc.getCurrentServerEntry();
        }

        if (joinWait > 0) {
            joinWait--;
            if (joinWait == 0) {
                resetGridHere();
                if (chatFeedback.get()) info("Back in, resuming.");
            }
            return;
        }

        if (autoFleeEnabled.get()) {
            tickFlee();
            if (fleeState != FleeState.IDLE) return;
        }

        if (lagbackTicks > 0) lagbackTicks--;
        if (clickDelay > 0) clickDelay--;
        if (restockCooldown > 0) restockCooldown--;

        if (autoEat.get() && tickAutoEat()) {
            if (mc.player.isGliding()) {
                mc.player.setNoGravity(true);
                mc.player.setVelocity(0, 0, 0);
            }
            return;
        }

        if (pauseOnEat.get() && MossBot.isEating(mc)) {
            if (mc.player.isGliding()) {
                mc.player.setNoGravity(true);
                mc.player.setVelocity(0, 0, 0);
            }
            return;
        }

        if (pauseOnManualInput.get() && pilotMode.get() != PilotMode.ManualControl) {
            if (isMovingManually()) {
                manualTicks = 10;
                if (restockStage != RestockStage.NONE) {
                    restockStage = RestockStage.NONE;
                    restockCooldown = 160;
                }
                if (checkGliding()) {
                    tickManualFly();
                }
                return;
            } else if (manualTicks > 0) {
                manualTicks--;
                if (mc.player.isGliding()) {
                    mc.player.setNoGravity(true);
                    mc.player.setVelocity(0, 0, 0);
                }
                if (manualTicks == 0) {
                    plateauY = estimateInitialPlateauY(mc.player.getBlockPos());
                    targetPos = pickNextTarget();
                }
                return;
            }
        }

        if (autoRestock.get() && restockStage == RestockStage.NONE && restockCooldown == 0 && pilotMode.get() != PilotMode.ManualControl) {
            boolean lowMoss = needsMoss();
            boolean lowBone = needsBone();
            if ((lowMoss || lowBone) && hasStockToPull(lowMoss, lowBone)) {
                prevShulkers = countAllShulkers();
                restockPad = findSafeRestockPad();
                restockStage = restockPad != null ? RestockStage.APPROACH_PAD : RestockStage.LANDING;
                stageTicks = 0;
                totalRestockTicks = 0;
                if (chatFeedback.get()) info("Low on supplies, landing to restock.");
            }
        }

        if (restockStage != RestockStage.NONE) {
            tickRestock();
            return;
        }

        if (!checkGliding()) return;

        if (pilotMode.get() == PilotMode.ManualControl) {
            tickManualFly();
        } else {
            tickPlateauSurfer();
        }
    }

    private boolean tickAutoEat() {
        if (mc.player == null) return false;

        boolean lowHealth = mc.player.getHealth() <= eatHealth.get();
        boolean lowHunger = mc.player.getHungerManager().getFoodLevel() <= eatHunger.get();

        if (!isAutoEating) {
            if (!lowHealth && !lowHunger) return false;

            if (isEdibleFood(mc.player.getOffHandStack())) {
                if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
                    swapPickaxe();
                }
                swappedForEat = false;
                isAutoEating = true;
                autoEatTicks = 0;
                mc.options.useKey.setPressed(true);
                return true;
            }

            FindItemResult food = findBestFood();
            if (!food.found()) return false;

            if (food.slot() >= 9) {
                InvUtils.move().from(food.slot()).toHotbar(3);
                InvUtils.swap(3, true);
            } else {
                InvUtils.swap(food.slot(), true);
            }

            swappedForEat = true;
            isAutoEating = true;
            autoEatTicks = 0;
            mc.options.useKey.setPressed(true);
            return true;
        }

        autoEatTicks++;
        boolean healedEnough = mc.player.getHealth() > eatHealth.get() + 2.0 || (lowHealth && mc.player.getAbsorptionAmount() >= 4.0);
        boolean hungerFull = mc.player.getHungerManager().getFoodLevel() >= 19;

        if ((healedEnough && (!lowHunger || hungerFull)) || autoEatTicks > 42) {
            stopAutoEating();
            return false;
        }

        mc.options.useKey.setPressed(true);
        return true;
    }

    private void stopAutoEating() {
        if (isAutoEating) {
            if (mc.options != null) {
                mc.options.useKey.setPressed(false);
            }
            if (swappedForEat && mc.player != null) {
                InvUtils.swapBack();
                swappedForEat = false;
            }
            isAutoEating = false;
            autoEatTicks = 0;
        }
    }

    private FindItemResult findBestFood() {
        FindItemResult ega = InvUtils.find(Items.ENCHANTED_GOLDEN_APPLE);
        if (ega.found()) return ega;
        FindItemResult ga = InvUtils.find(Items.GOLDEN_APPLE);
        if (ga.found()) return ga;
        return InvUtils.find(this::isEdibleFood);
    }

    private boolean isEdibleFood(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.PUFFERFISH) || s.isOf(Items.CHORUS_FRUIT)) {
            return false;
        }
        return s.get(DataComponentTypes.FOOD) != null;
    }

    private boolean isMovingManually() {
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed()
            || mc.options.jumpKey.isPressed()
            || mc.options.sneakKey.isPressed();
    }

    private boolean checkGliding() {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || (chest.getMaxDamage() - chest.getDamage() <= 2)) {
            FindItemResult elytra = InvUtils.find(s ->
                s.isOf(Items.ELYTRA) && (s.getMaxDamage() - s.getDamage() > 5));
            if (elytra.found()) {
                InvUtils.move().from(elytra.slot()).toArmor(2);
                if (chatFeedback.get()) info("Swapped elytra.");
            }
            return false;
        }

        if (mc.player.isGliding()) {
            mc.player.setNoGravity(true);
            mc.player.fallDistance = 0f;
            takeoffTicks = 0;
            return true;
        }

        if (!autoTakeoff.get()) return false;

        takeoffTicks++;
        if (mc.player.isOnGround()) {
            mc.player.setNoGravity(false);
            mc.player.jump();
            mc.player.setVelocity(0, 0.42, 0);
            takeoffTicks = 1;
            return false;
        }

        mc.player.setNoGravity(true);
        if (takeoffTicks >= 2 && takeoffTicks % 2 == 0) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startGliding();
            if (useRocketBoost.get() && takeoffTicks == 6) {
                useRocket();
            }
        }

        mc.player.setVelocity(0, 0.02, 0);
        return mc.player.isGliding();
    }

    private void tickManualFly() {
        if (mc.player == null) return;
        mc.player.setNoGravity(true);
        mc.player.fallDistance = 0f;

        if (lagbackTicks > 0) {
            mc.player.setVelocity(0, 0, 0);
            return;
        }

        float fwd = 0f;
        float side = 0f;
        if (mc.options.forwardKey.isPressed()) fwd += 1f;
        if (mc.options.backKey.isPressed()) fwd -= 1f;
        if (mc.options.leftKey.isPressed()) side += 1f;
        if (mc.options.rightKey.isPressed()) side -= 1f;

        double vx = 0.0, vz = 0.0, vy = 0.0;

        if (fwd != 0f || side != 0f) {
            curSpeed = Math.min(cruiseSpeed.get(), curSpeed + 0.08);
            float yaw = mc.player.getYaw();
            if (fwd != 0f) {
                if (side > 0f) yaw += (fwd > 0f ? -45f : 45f);
                else if (side < 0f) yaw += (fwd > 0f ? 45f : -45f);
                if (fwd < 0f) yaw += 180f;
            } else {
                yaw += (side > 0f ? -90f : 90f);
            }
            double rad = Math.toRadians(yaw);
            vx = -Math.sin(rad) * curSpeed;
            vz = Math.cos(rad) * curSpeed;
        } else {
            curSpeed = workSpeed.get();
        }

        if (mc.options.jumpKey.isPressed()) vy = verticalSpeed.get();
        else if (mc.options.sneakKey.isPressed()) vy = -verticalSpeed.get();

        Vec3d vel = antiSlam.get() ? applyAntiSlam(new Vec3d(vx, vy, vz)) : new Vec3d(vx, vy, vz);
        mc.player.setVelocity(vel);
    }

    /*
     _______      __      _  __   ______     __        __  _    _       __      _______
    |__   __|    /  \    | |/ /  |  ____|    \ \      / / | |  | |     /  \    |__   __|
       | |      / /\ \   | ' /   | |__        \ \ /\ / /  | |__| |    / /\ \      | |
       | |     / ____ \  |  <    |  __|        \ V  V /   |  __  |   / ____ \     | |
       |_|    /_/    \_\ |_|\_\  |______|       \_/\_/    |_|  |_|  /_/    \_\    |_|

    __    __   ____    _    _      _   _   ______   ______   _____            _______
    \ \  / /  / __ \  | |  | |    | \ | | |  ____| |  ____| |  __ \         .'       '.
     \ \/ /  | |  | | | |  | |    |  \| | | |__    | |__    | |  | |       /   >   <   \
      |  |   | |__| | | |__| |    | |\  | |  __|   |  __|   | |__| |      |    _____    |
      |__|    \____/   \____/     |_| \_| |______| |______| |_____/        \   \___/   /
                                                                            '._______.'
    */

    private void tickPlateauSurfer() {
        if (mc.player == null || mc.world == null) return;
        if (startPos == null) resetGridHere();

        mc.player.setNoGravity(true);
        mc.player.fallDistance = 0f;
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        BlockPos bp = BlockPos.ofFloored(pos.x, pos.y, pos.z);
        if (!mc.world.getChunkManager().isChunkLoaded(bp.getX() >> 4, bp.getZ() >> 4)) {
            mc.player.setVelocity(0, 0, 0);
            return;
        }

        if (lagbackTicks > 0) {
            mc.player.setVelocity(0, 0, 0);
            return;
        }

        if (pitEscapeTicks > 0) {
            pitEscapeTicks--;
            if (pos.y < pitEscapeTargetY && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0, 0.8, 0))) {
                mc.player.setVelocity(0, verticalSpeed.get(), 0);
                return;
            } else {
                plateauY = pos.y - hoverClearance.get();
                pitEscapeTicks = 0;
                targetPos = pickNextTarget();
            }
        }

        if (pilotMode.get() == PilotMode.PlateauSurfer) {
            updateBackgroundSectorScanner(bp);
        }

        LocalWorkInfo workInfo = inspectLocalWork(bp);
        if (neverReturn.get() && workInfo.convertible == 0 && workInfo.frontier == 0) {
            markCoordVisited(pos.x, pos.z);
        }

        if (prevPos != null) {
            double movedHorizSq = (pos.x - prevPos.x) * (pos.x - prevPos.x) + (pos.z - prevPos.z) * (pos.z - prevPos.z);
            if (movedHorizSq < 0.003 || mc.player.horizontalCollision) {
                antiStuckTicks++;
            } else {
                antiStuckTicks = 0;
            }
        }
        prevPos = pos;

        if (antiStuckTicks >= 4) {
            if (targetPos != null) markCoordVisited(targetPos.x, targetPos.z);
            double rimY = findPitRimHeight(bp);
            if (rimY > pos.y + 2.5 && mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0, 1.5, 0))) {
                pitEscapeTargetY = rimY + hoverClearance.get();
                pitEscapeTicks = 28;
                antiStuckTicks = 0;
                return;
            }
            bypassDir = findOpenEscapeHeading(moveHeading);
            bypassTicks = 8;
            moveHeading = bypassDir;
            targetPos = pickNextTarget();
            antiStuckTicks = 0;
        }

        targetAgeTicks++;
        if (targetPos == null || targetAgeTicks > 50) {
            targetPos = pickNextTarget();
            targetAgeTicks = 0;
        }

        double dx = targetPos.x - pos.x;
        double dz = targetPos.z - pos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        if (horizDist < 2.2 || (pilotMode.get() == PilotMode.PlateauSurfer && targetAgeTicks >= 12 && workInfo.convertible == 0 && workInfo.frontier == 0 && horizDist < 7.0)) {
            if (horizDist < 2.5 && workInfo.convertible == 0) {
                markCoordVisited(targetPos.x, targetPos.z);
            }
            targetPos = pickNextTarget();
            dx = targetPos.x - pos.x;
            dz = targetPos.z - pos.z;
            horizDist = Math.sqrt(dx * dx + dz * dz);
            targetAgeTicks = 0;
        }

        double dirX;
        double dirZ;
        if (bypassTicks > 0 && bypassDir != null) {
            bypassTicks--;
            dirX = bypassDir.x;
            dirZ = bypassDir.z;
        } else {
            dirX = horizDist > 0.001 ? dx / horizDist : moveHeading.x;
            dirZ = horizDist > 0.001 ? dz / horizDist : moveHeading.z;
        }

        double rawGroundY = getPlateauSafeGroundY(pos, dirX, dirZ);
        if (!Double.isNaN(rawGroundY)) {
            if (Double.isNaN(plateauY)) {
                plateauY = rawGroundY;
            } else if (rawGroundY >= plateauY - 4.0 && rawGroundY <= plateauY + 4.5) {
                plateauY = MathHelper.lerp(0.18, plateauY, rawGroundY);
            }
        }

        double effectiveGroundY = Double.isNaN(rawGroundY) ? plateauY : Math.max(rawGroundY, plateauY - 3.5);
        double desiredY = effectiveGroundY + hoverClearance.get();
        double dy = desiredY - pos.y;
        double vSpd = verticalSpeed.get();

        double vy = 0.0;
        if (Math.abs(dy) > 0.12) {
            vy = MathHelper.clamp(dy * 0.6, -vSpd, vSpd);
        }

        if (isStepAhead(dirX, dirZ)) {
            vy = vSpd;
        }

        double goalSpeed;
        if (workInfo.convertible > 0 && workInfo.frontier == 0 && autoPace.get() && countItem(Items.MOSS_BLOCK) > 0) {
            goalSpeed = Math.min(workSpeed.get(), 0.38);
        } else if (workInfo.convertible > 0 || workInfo.frontier > 0) {
            goalSpeed = workSpeed.get();
        } else {
            goalSpeed = cruiseSpeed.get();
        }

        curSpeed = MathHelper.lerp(0.3, curSpeed, goalSpeed);
        double speed = horizDist < curSpeed ? Math.max(0.28, horizDist) : curSpeed;

        Vec3d finalVel = steerCleanPath(dirX, vy, dirZ, speed);
        if (antiSlam.get()) {
            finalVel = applyAntiSlam(finalVel);
        }

        if (finalVel.horizontalLengthSquared() > 0.002) {
            moveHeading = new Vec3d(finalVel.x, 0, finalVel.z).normalize();
        }

        mc.player.setVelocity(finalVel);
    }

    private Vec3d applyAntiSlam(Vec3d vel) {
        if (mc.world == null || mc.player == null) return vel;

        double hSpd = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        double minSafe = antiSlamMinSpeed.get();
        if (hSpd <= minSafe) return vel;

        double dirX = vel.x / hSpd;
        double dirZ = vel.z / hSpd;
        double maxDist = antiSlamDistance.get();
        Box paddedBox = mc.player.getBoundingBox().expand(0.15, 0.05, 0.15);

        double obstacleDist = maxDist + 1.0;
        for (double d = 0.4; d <= maxDist; d += 0.35) {
            Box check = paddedBox.offset(dirX * d, vel.y * (d / hSpd) * 0.5, dirZ * d);
            if (!mc.world.isSpaceEmpty(mc.player, check)) {
                obstacleDist = d;
                break;
            }
        }

        if (obstacleDist <= maxDist) {
            if (obstacleDist <= 0.85) {
                double lift = mc.world.isSpaceEmpty(mc.player, paddedBox.offset(0, 0.8, 0)) ? verticalSpeed.get() : 0.0;
                return new Vec3d(dirX * minSafe * 0.5, lift, dirZ * minSafe * 0.5);
            }
            double ratio = MathHelper.clamp((obstacleDist - 0.85) / Math.max(0.2, maxDist - 0.85), 0.0, 1.0);
            double newSpd = MathHelper.lerp(ratio, minSafe, hSpd);
            curSpeed = Math.min(curSpeed, newSpd);
            return new Vec3d(dirX * newSpd, Math.max(vel.y, 0.15), dirZ * newSpd);
        }

        return vel;
    }

    private double estimateInitialPlateauY(BlockPos center) {
        if (mc.world == null) return center.getY() - hoverClearance.get();
        List<Integer> heights = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                BlockPos surf = findGroundColumn(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (surf != null) heights.add(surf.getY() + 1);
            }
        }
        if (heights.isEmpty()) return center.getY() - hoverClearance.get();
        Collections.sort(heights);
        return heights.get((int) Math.floor((heights.size() - 1) * 0.65));
    }

    private double findPitRimHeight(BlockPos center) {
        if (mc.world == null) return center.getY();
        int maxRim = center.getY();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int dist = 2; dist <= 6; dist += 2) {
                BlockPos check = center.offset(dir, dist);
                for (int y = center.getY() + 14; y > center.getY(); y--) {
                    BlockPos p = new BlockPos(check.getX(), y, check.getZ());
                    BlockState s = mc.world.getBlockState(p);
                    if (!s.isAir() && !IGNORED_STRUCTURES.contains(s.getBlock()) && isPassableAir(mc.world.getBlockState(p.up()))) {
                        if (y + 1 > maxRim) maxRim = y + 1;
                        break;
                    }
                }
            }
        }
        return maxRim;
    }

    private Vec3d findOpenEscapeHeading(Vec3d currentHeading) {
        double baseAngle = Math.atan2(currentHeading.z, currentHeading.x);
        double[] testAngles = new double[]{55, -55, 105, -105, 150, -150, 180};
        for (double deg : testAngles) {
            double rad = baseAngle + Math.toRadians(deg);
            double cx = Math.cos(rad);
            double cz = Math.sin(rad);
            if (isBoxPathClear(cx * 0.45, 0.25, cz * 0.45, 3.0)) {
                return new Vec3d(cx, 0, cz);
            }
        }
        return new Vec3d(-currentHeading.x, 0, -currentHeading.z);
    }

    private boolean isStepAhead(double dirX, double dirZ) {
        if (mc.world == null || mc.player == null) return false;
        Box feetBox = mc.player.getBoundingBox().expand(0.12, 0.0, 0.12).offset(dirX * 1.2, 0, dirZ * 1.2);
        Box steppedBox = mc.player.getBoundingBox().expand(0.12, 0.0, 0.12).offset(dirX * 1.2, 1.3, dirZ * 1.2);
        return !mc.world.isSpaceEmpty(mc.player, feetBox) && mc.world.isSpaceEmpty(mc.player, steppedBox);
    }

    private double getPlateauSafeGroundY(Vec3d pos, double dirX, double dirZ) {
        if (mc.world == null) return Double.NaN;
        int feetY = MathHelper.floor(pos.y);
        double minAcceptableFloor = Double.isNaN(plateauY) ? (feetY - 8) : (plateauY - 4.5);

        double underMaxY = Double.NaN;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                BlockPos surf = findGroundColumn(MathHelper.floor(pos.x) + ox, feetY, MathHelper.floor(pos.z) + oz);
                if (surf != null && surf.getY() + 1 >= minAcceptableFloor) {
                    double y = surf.getY() + 1.0;
                    if (Double.isNaN(underMaxY) || y > underMaxY) underMaxY = y;
                }
            }
        }

        double aheadMaxY = Double.NaN;
        for (int step = 1; step <= 4; step++) {
            int fx = MathHelper.floor(pos.x + dirX * step);
            int fz = MathHelper.floor(pos.z + dirZ * step);
            BlockPos surf = findGroundColumn(fx, feetY, fz);
            if (surf != null && surf.getY() + 1 >= minAcceptableFloor) {
                double y = surf.getY() + 1.0;
                if (Double.isNaN(aheadMaxY) || y > aheadMaxY) aheadMaxY = y;
            }
        }

        if (Double.isNaN(underMaxY)) return aheadMaxY;
        if (Double.isNaN(aheadMaxY)) return underMaxY;

        if (aheadMaxY > underMaxY) {
            return aheadMaxY;
        }
        return (underMaxY * 0.45) + (aheadMaxY * 0.55);
    }

    private BlockPos findGroundColumn(int x, int feetY, int z) {
        if (mc.world == null) return null;

        int maxStart = feetY + 2;
        if (!isPassableAir(mc.world.getBlockState(new BlockPos(x, maxStart + 1, z)))) {
            maxStart = feetY;
        }

        int minY = Math.max(mc.world.getBottomY(), feetY - 10);
        BlockPos.Mutable m = new BlockPos.Mutable(x, maxStart, z);

        for (int y = maxStart; y >= minY; y--) {
            m.setY(y);
            BlockState s = mc.world.getBlockState(m);
            if (!s.getFluidState().isEmpty()) {
                return m.toImmutable();
            }
            if (!s.isAir() && !s.getCollisionShape(mc.world, m).isEmpty()) {
                if (IGNORED_STRUCTURES.contains(s.getBlock())) {
                    return null;
                }
                if (isPassableAir(mc.world.getBlockState(m.up())) && isPassableAir(mc.world.getBlockState(m.up(2)))) {
                    return m.toImmutable();
                }
                return null;
            }
        }
        return null;
    }

    private PatchStats sampleGridPatch(int startX, int startZ, int step, int refY) {
        if (mc.world == null) return new PatchStats(0, 0, 0, refY);
        int frontier = 0;
        int convertible = 0;
        int fluid = 0;
        int sumY = 0;
        int cols = 0;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                BlockPos top = findGroundColumn(startX + i * step, refY, startZ + j * step);
                if (top == null) continue;

                BlockState s = mc.world.getBlockState(top);
                if (!s.getFluidState().isEmpty() || !mc.world.getBlockState(top.up()).getFluidState().isEmpty()) {
                    fluid++;
                    continue;
                }

                if (!Double.isNaN(plateauY) && top.getY() + 1 < plateauY - 5.0) {
                    continue;
                }

                Block b = s.getBlock();
                if (b == Blocks.MOSS_BLOCK) {
                    if (hasAdjacentConvertible(top)) {
                        frontier++;
                        sumY += top.getY() + 1;
                        cols++;
                    }
                } else if (MossBot.CONVERTIBLE_BLOCKS.contains(b)) {
                    convertible++;
                    sumY += top.getY() + 1;
                    cols++;
                }
            }
        }

        int avgY = cols > 0 ? (sumY / cols) : refY;
        return new PatchStats(frontier, convertible, fluid, avgY);
    }

    private Vec3d pickNextTarget() {
        if (pilotMode.get() == PilotMode.PlateauSurfer && mc.player != null && mc.world != null) {
            Vec3d corridorTarget = findBestPlateauCorridor();
            if (corridorTarget != null) return corridorTarget;

            Vec3d sectorTarget = pickBestScannedSector();
            if (sectorTarget != null) return sectorTarget;
        }
        return getNextGeometricWaypoint();
    }

    private Vec3d findBestPlateauCorridor() {
        if (mc.player == null || mc.world == null) return null;

        int refY = mc.player.getBlockY();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        Vec3d bestWaypoint = null;
        double bestScore = -1e9;

        for (int i = 0; i < 24; i++) {
            double rad = Math.toRadians(i * 15.0);
            double dirX = Math.cos(rad);
            double dirZ = Math.sin(rad);

            int totalFrontier = 0;
            int totalConvertible = 0;
            int fluidHits = 0;
            int pitDrops = 0;
            double bestDistAlongRay = 14.0;
            int bestYAlongRay = refY;

            for (int dist = 5; dist <= 20; dist += 5) {
                double cx = px + dirX * dist;
                double cz = pz + dirZ * dist;
                if (isCoordVisited(cx, cz)) continue;

                PatchStats stats = sampleGridPatch(MathHelper.floor(cx) - 2, MathHelper.floor(cz) - 2, 2, refY);
                fluidHits += stats.fluid;

                if (stats.frontier == 0 && stats.convertible == 0) {
                    pitDrops++;
                } else {
                    totalFrontier += stats.frontier;
                    totalConvertible += stats.convertible;
                    bestDistAlongRay = dist;
                    bestYAlongRay = stats.avgY;
                }
            }

            if (avoidWater.get() && fluidHits >= 2) continue;
            if (totalFrontier == 0 && totalConvertible < 3) continue;

            double alignment = (dirX * moveHeading.x) + (dirZ * moveHeading.z);
            double elevationDiff = Math.abs(bestYAlongRay - plateauY);

            double score = (totalFrontier * 26.0)
                + (totalConvertible * 6.5)
                + (alignment * 22.0)
                - (pitDrops * 18.0)
                - (elevationDiff * 3.5);

            if (score > bestScore) {
                bestScore = score;
                bestWaypoint = new Vec3d(px + dirX * bestDistAlongRay, bestYAlongRay, pz + dirZ * bestDistAlongRay);
            }
        }

        return bestWaypoint;
    }

    private boolean hasAdjacentConvertible(BlockPos mossPos) {
        if (mc.world == null) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = mossPos.add(dx, dy, dz);
                    if (MossBot.CONVERTIBLE_BLOCKS.contains(mc.world.getBlockState(p).getBlock())
                        && isPassableAir(mc.world.getBlockState(p.up()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private LocalWorkInfo inspectLocalWork(BlockPos playerPos) {
        if (mc.world == null) return new LocalWorkInfo(0, 0);
        int convertible = 0;
        int frontier = 0;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos surf = findGroundColumn(playerPos.getX() + dx, playerPos.getY(), playerPos.getZ() + dz);
                if (surf == null) continue;
                Block b = mc.world.getBlockState(surf).getBlock();
                if (MossBot.CONVERTIBLE_BLOCKS.contains(b)) {
                    convertible++;
                } else if (b == Blocks.MOSS_BLOCK && hasAdjacentConvertible(surf)) {
                    frontier++;
                }
            }
        }
        return new LocalWorkInfo(convertible, frontier);
    }

    private boolean isPassableAir(BlockState s) {
        return s.isAir() || PASSABLE_PLANTS.contains(s.getBlock());
    }

    private Vec3d steerCleanPath(double dirX, double desiredVy, double dirZ, double speed) {
        double baseAngle = Math.atan2(dirZ, dirX);
        double vSpd = verticalSpeed.get();
        double minSafe = antiSlam.get() ? antiSlamMinSpeed.get() : speed * 0.5;

        double[] vyOptions = new double[]{desiredVy, vSpd, -vSpd * 0.5, 0.0};
        double[] anglesDeg = new double[]{
            0, 22, -22, 45, -45, 70, -70, 95, -95, 130, -130, 180
        };

        for (double vy : vyOptions) {
            for (double deg : anglesDeg) {
                double rad = baseAngle + Math.toRadians(deg);
                double cx = Math.cos(rad);
                double cz = Math.sin(rad);
                double spd = Math.abs(deg) > 45 ? Math.min(speed * 0.65, minSafe * 1.4) : speed;

                if (isBoxPathClear(cx * spd, vy, cz * spd, 2.5)) {
                    return new Vec3d(cx * spd, vy, cz * spd);
                }
            }
        }

        if (isBoxPathClear(0, vSpd, 0, 1.5)) {
            return new Vec3d(0, vSpd, 0);
        }
        return new Vec3d(0, 0.25, 0);
    }

    private boolean isBoxPathClear(double vx, double vy, double vz, double ticksAhead) {
        if (mc.world == null || mc.player == null) return false;
        Box box = mc.player.getBoundingBox().expand(0.14, 0.06, 0.14);

        for (double t = 0.5; t <= ticksAhead; t += 0.5) {
            Box next = box.offset(vx * t, vy * t, vz * t);
            if (!mc.world.isSpaceEmpty(mc.player, next)) return false;

            BlockPos center = BlockPos.ofFloored(next.getCenter());
            BlockState s = mc.world.getBlockState(center);
            if (!s.getFluidState().isEmpty() || s.isOf(Blocks.COBWEB) || s.isOf(Blocks.FIRE)) {
                return false;
            }
        }
        return true;
    }

    private int getEffectiveScanRadiusBlocks() {
        if (matchRenderDistance.get() && mc.options != null) {
            return MathHelper.clamp(mc.options.getViewDistance().getValue() * 16, 48, 384);
        }
        return scanRange.get();
    }

    private void updateBackgroundSectorScanner(BlockPos playerPos) {
        int psx = playerPos.getX() >> 3;
        int psz = playerPos.getZ() >> 3;
        int refY = playerPos.getY();

        int maxSectorRadius = Math.max(4, getEffectiveScanRadiusBlocks() >> 3);
        int budget = 36;

        while (budget > 0) {
            if (scanRingRadius > maxSectorRadius) {
                scanRingRadius = 2;
                scanRingStep = 0;
            }

            int perimeter = scanRingRadius * 8;
            if (scanRingStep >= perimeter) {
                scanRingRadius++;
                scanRingStep = 0;
                continue;
            }

            int side = scanRingStep / (scanRingRadius * 2);
            int offset = (scanRingStep % (scanRingRadius * 2)) - scanRingRadius;
            int sx = psx;
            int sz = psz;

            switch (side) {
                case 0 -> { sx += offset; sz -= scanRingRadius; }
                case 1 -> { sx += scanRingRadius; sz += offset; }
                case 2 -> { sx -= offset; sz += scanRingRadius; }
                default -> { sx -= scanRingRadius; sz -= offset; }
            }

            evaluateSector(sx, sz, refY);
            scanRingStep++;
            budget--;
        }
    }

    private void evaluateSector(int sx, int sz, int refY) {
        if (mc.world == null) return;
        long key = pack(sx, sz);
        if (neverReturn.get() && deadSectors.contains(key)) return;

        if (!mc.world.getChunkManager().isChunkLoaded(sx >> 1, sz >> 1)) return;

        int baseX = sx << 3;
        int baseZ = sz << 3;

        PatchStats stats = sampleGridPatch(baseX + 1, baseZ + 1, 3, refY);
        if ((avoidWater.get() && stats.fluid >= 2) || (stats.convertible + stats.frontier < 3)) {
            sectorMap.remove(key);
            if (stats.fluid >= 2 || (stats.convertible == 0 && stats.frontier == 0)) {
                deadSectors.add(key);
            }
            return;
        }

        sectorMap.put(key, new SectorInfo(baseX + 4.0, stats.avgY, baseZ + 4.0, stats.convertible, stats.frontier));
    }

    private Vec3d pickBestScannedSector() {
        if (mc.player == null || sectorMap.isEmpty()) return null;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        SectorInfo best = null;
        double bestScore = -1e9;

        var iter = sectorMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Long, SectorInfo> entry = iter.next();
            long key = entry.getKey();
            SectorInfo s = entry.getValue();

            if (neverReturn.get() && (deadSectors.contains(key) || isCoordVisited(s.x, s.z))) {
                iter.remove();
                continue;
            }

            double dx = s.x - px;
            double dz = s.z - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 4.0) continue;

            double alignment = ((dx / dist) * moveHeading.x) + ((dz / dist) * moveHeading.z);
            double score = (s.frontier * 14.0) + (s.convertible * 6.0) + (alignment * 14.0) - (dist * 1.8) - Math.abs(s.y - plateauY) * 2.5;

            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }

        return best != null ? new Vec3d(best.x, best.y, best.z) : null;
    }

    private void markCoordVisited(double x, double z) {
        int cx = MathHelper.floor(x) >> 2;
        int cz = MathHelper.floor(z) >> 2;
        visitedCoords.add(pack(cx, cz));
    }

    private boolean isCoordVisited(double x, double z) {
        if (!neverReturn.get()) return false;
        int cx = MathHelper.floor(x) >> 2;
        int cz = MathHelper.floor(z) >> 2;
        return visitedCoords.contains(pack(cx, cz));
    }

    private long pack(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private Vec3d getNextGeometricWaypoint() {
        Vec3d next = startPos;
        for (int i = 0; i < 64; i++) {
            next = stepGrid();
            if (!isCoordVisited(next.x, next.z)) return next;
        }
        return next;
    }

    private Vec3d stepGrid() {
        double w = laneWidth.get();
        double len = passLength.get();
        double y = mc.player != null ? mc.player.getY() : startPos.y;

        if (pilotMode.get() == PilotMode.LawnmowerGrid) {
            int pair = gridStep / 4;
            int phase = gridStep % 4;
            gridStep++;

            double fwd = (phase == 0 || phase == 1) ? len : 0.0;
            int lane = switch (phase) {
                case 0 -> pair * 2;
                case 1, 2 -> pair * 2 + 1;
                default -> pair * 2 + 2;
            };

            Vec3d p = startPos.add(forwardVec.multiply(fwd)).add(rightVec.multiply(lane * w));
            return new Vec3d(p.x, y, p.z);
        } else {
            if (spiralPos == null) spiralPos = startPos;

            int dir = spiralStep % 4;
            int mult = (spiralStep / 2) + 1;
            double d = Math.max(12.0, mult * w);
            spiralStep++;

            Vec3d offset = switch (dir) {
                case 0 -> forwardVec.multiply(d);
                case 1 -> rightVec.multiply(d);
                case 2 -> forwardVec.multiply(-d);
                default -> rightVec.multiply(-d);
            };

            spiralPos = spiralPos.add(offset);
            return new Vec3d(spiralPos.x, y, spiralPos.z);
        }
    }

    private void useRocket() {
        FindItemResult r = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!r.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (inv.found()) {
                InvUtils.move().from(inv.slot()).toHotbar(4);
                r = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            }
        }
        if (r.found() && mc.interactionManager != null) {
            InvUtils.swap(r.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
    }

    private void tickFlee() {
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = null;
        double minDist = fleeTriggerRange.get();

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isDead() || p.getHealth() <= 0) continue;
            if (Friends.get().isFriend(p)) continue;

            double d = mc.player.distanceTo(p);
            if (d < minDist) {
                minDist = d;
                target = p;
            }
        }

        boolean hitByPlayer = mc.player.hurtTime > 0 && mc.player.getAttacker() instanceof PlayerEntity;

        switch (fleeState) {
            case IDLE -> {
                if (target != null || hitByPlayer) {
                    stopAutoEating();
                    Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    if (target != null) {
                        Vec3d away = new Vec3d(pos.x - target.getX(), 0, pos.z - target.getZ());
                        fleeVec = away.lengthSquared() > 0.001 ? away.normalize() : forwardVec;
                        if (chatFeedback.get()) {
                            warning("Player spotted (%s), dipping.", target.getName().getString());
                        }
                    } else {
                        fleeVec = forwardVec;
                    }

                    if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                        mc.player.closeHandledScreen();
                    }
                    restockStage = RestockStage.NONE;
                    fleeState = FleeState.FLEEING_BURST;
                    clearTicks = 0;
                }
            }

            case FLEEING_BURST -> {
                if (target != null) {
                    Vec3d away = new Vec3d(mc.player.getX() - target.getX(), 0, mc.player.getZ() - target.getZ());
                    if (away.lengthSquared() > 0.001) {
                        fleeVec = away.normalize();
                    }
                    clearTicks = 0;
                } else {
                    clearTicks++;
                    if (clearTicks >= 30) {
                        fleeVec = new Vec3d(-fleeVec.z, 0, fleeVec.x).normalize();
                        fleeState = FleeState.DOGLEG_EVASION;
                        clearTicks = 0;
                        if (chatFeedback.get()) info("Out of render, turning before log.");
                    }
                }
                flyEscape(fleeVec);
            }

            case DOGLEG_EVASION -> {
                if (target != null) {
                    fleeState = FleeState.FLEEING_BURST;
                    clearTicks = 0;
                    return;
                }

                flyEscape(fleeVec);
                clearTicks++;

                if (clearTicks >= clearWaitSeconds.get() * 20) {
                    fleeState = FleeState.DISCONNECTED;
                    logTime = System.currentTimeMillis();

                    if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(
                            Text.literal("AutoFlee: Logged out. Reconnecting in " + reconnectDelaySeconds.get() + "s.")
                        );
                    }
                }
            }

            case DISCONNECTED -> {}
        }
    }

    private void flyEscape(Vec3d dir) {
        if (!checkGliding() || mc.player == null) return;
        if (lagbackTicks > 0) {
            mc.player.setVelocity(0, 0, 0);
            return;
        }

        curSpeed = Math.min(fleeSpeed.get(), curSpeed + 0.12);
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double envY = getPlateauSafeGroundY(pos, dir.x, dir.z);
        double desiredVy = (!Double.isNaN(envY) && pos.y < envY + 5.0) ? verticalSpeed.get() : 0.0;

        Vec3d vel = steerCleanPath(dir.x, desiredVy, dir.z, curSpeed);
        if (antiSlam.get()) {
            vel = applyAntiSlam(vel);
        }
        mc.player.setVelocity(vel);
    }

    private void startReconnectThread() {
        stopReconnectThread();
        reconnectTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MossPilot-Timer");
            t.setDaemon(true);
            return t;
        });

        reconnectTimer.scheduleAtFixedRate(() -> {
            if (!isActive() || fleeState != FleeState.DISCONNECTED || lastServer == null) return;
            long sec = (System.currentTimeMillis() - logTime) / 1000L;
            if (sec >= reconnectDelaySeconds.get()) {
                fleeState = FleeState.IDLE;
                mc.execute(() -> {
                    if (mc.world == null && lastServer != null) {
                        ConnectScreen.connect(
                            new TitleScreen(),
                            mc,
                            ServerAddress.parse(lastServer.address),
                            lastServer,
                            false,
                            null
                        );
                    }
                });
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopReconnectThread() {
        if (reconnectTimer != null) {
            reconnectTimer.shutdownNow();
            reconnectTimer = null;
        }
    }

    private boolean needsMoss() {
        return countItem(Items.MOSS_BLOCK) < 16;
    }

    private boolean needsBone() {
        int meal = countItem(Items.BONE_MEAL);
        int blocks = countItem(Items.BONE_BLOCK);
        return (meal + (blocks * 3)) < 24;
    }

    private boolean hasStockToPull(boolean needMoss, boolean needBone) {
        if (needMoss && findShulkerInInv(Items.MOSS_BLOCK) != -1) return true;
        if (needBone && findShulkerInInv(Items.BONE_BLOCK, Items.BONE_MEAL) != -1) return true;
        return !echestEmpty && countItem(Items.ENDER_CHEST) >= 1;
    }

    private RestockPad findSafeRestockPad() {
        if (mc.player == null || mc.world == null) return null;
        BlockPos center = mc.player.getBlockPos();
        RestockPad best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = 2; dy >= -8; dy--) {
                    BlockPos floor = center.add(dx, dy, dz);
                    if (!isSolidDryFloor(floor)) continue;

                    BlockPos standPos = floor.up();
                    if (!isPassableAir(mc.world.getBlockState(standPos)) || !mc.world.getBlockState(standPos.up()).isAir()) break;

                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockPos boxFloor = floor.offset(dir);
                        BlockPos boxPos = boxFloor.up();
                        if (!isSolidDryFloor(boxFloor)) continue;
                        if (!isPassableAir(mc.world.getBlockState(boxPos)) || !mc.world.getBlockState(boxPos.up()).isAir()) continue;

                        boolean flatPad = true;
                        for (Direction nDir : Direction.Type.HORIZONTAL) {
                            BlockPos neighborFloor = boxFloor.offset(nDir);
                            if (!isSolidDryFloor(neighborFloor) || !isPassableAir(mc.world.getBlockState(neighborFloor.up()))) {
                                flatPad = false;
                                break;
                            }
                        }
                        if (!flatPad) continue;

                        double distSq = center.getSquaredDistance(standPos);
                        if (distSq < bestScore) {
                            bestScore = distSq;
                            best = new RestockPad(standPos.toImmutable(), boxPos.toImmutable());
                        }
                    }
                    break;
                }
            }
        }
        return best;
    }

    private boolean isSolidDryFloor(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState s = mc.world.getBlockState(pos);
        return !s.isAir()
            && s.getFluidState().isEmpty()
            && s.isSideSolidFullSquare(mc.world, pos, Direction.UP);
    }

    private boolean breakEChest(BlockPos pos) {
        if (pos == null || mc.world == null || mc.world.getBlockState(pos).isAir()) return true;
        swapPickaxe();
        BlockUtils.breakBlock(pos, true);
        return stageTicks > 120;
    }

    private void abortRestock(int cooldown) {
        restockCooldown = cooldown;
        restockStage = RestockStage.TAKEOFF;
        stageTicks = 0;
    }

    private boolean quickMoveSlot(ScreenHandler h, int slot) {
        if (slot == -1 || mc.interactionManager == null || mc.player == null) return false;
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        clickDelay = 3;
        return true;
    }

    private void selectNextShulkerOrTakeoff() {
        if (needsMoss() && findShulkerInInv(Items.MOSS_BLOCK) != -1) {
            grabMossNext = true;
            restockStage = RestockStage.PREP_SPOT;
            stageTicks = 0;
        } else if (needsBone() && findShulkerInInv(Items.BONE_BLOCK, Items.BONE_MEAL) != -1) {
            grabMossNext = false;
            restockStage = RestockStage.PREP_SPOT;
            stageTicks = 0;
        } else {
            abortRestock((needsMoss() || needsBone()) ? 300 : 0);
        }
    }

    private void interactContainerOrFallback(BlockPos pos, RestockStage fallback) {
        if (stageTicks % 6 == 0 && pos != null) {
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            BlockUtils.interact(hit, Hand.MAIN_HAND, true);
        }
        if (stageTicks > 40) {
            restockStage = fallback;
            stageTicks = 0;
        }
    }

    private void tickRestock() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        stageTicks++;
        totalRestockTicks++;

        if (totalRestockTicks > 450) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                mc.player.closeHandledScreen();
            }
            totalRestockTicks = 0;
            abortRestock(300);
            return;
        }

        switch (restockStage) {
            case APPROACH_PAD -> {
                if (restockPad == null || stageTicks > 55) {
                    restockStage = RestockStage.LANDING;
                    stageTicks = 0;
                    return;
                }
                Vec3d goal = Vec3d.ofBottomCenter(restockPad.standPos).add(0, 0.2, 0);
                double dx = goal.x - mc.player.getX();
                double dy = goal.y - mc.player.getY();
                double dz = goal.z - mc.player.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);

                if (horiz < 0.35 && Math.abs(dy) < 1.0) {
                    mc.player.setVelocity(0, -0.25, 0);
                    mc.player.setNoGravity(false);
                    restockStage = RestockStage.LANDING;
                    stageTicks = 0;
                    return;
                }

                mc.player.setNoGravity(true);
                double spd = Math.min(0.45, horiz);
                double vx = horiz > 0.01 ? (dx / horiz) * spd : 0;
                double vz = horiz > 0.01 ? (dz / horiz) * spd : 0;
                double vy = MathHelper.clamp(dy * 0.45, -0.4, 0.45);
                mc.player.setVelocity(vx, vy, vz);
            }

            case LANDING -> {
                mc.player.setNoGravity(false);
                if (mc.player.isOnGround()) {
                    mc.player.setVelocity(0, 0, 0);
                    dropJunkIfFull();
                    if (!echestEmpty && findShulkerInInv(Items.MOSS_BLOCK) == -1
                        && findShulkerInInv(Items.BONE_BLOCK, Items.BONE_MEAL) == -1
                        && countItem(Items.ENDER_CHEST) >= 1) {
                        restockStage = RestockStage.PLACE_ECHEST_1;
                        stageTicks = 0;
                    } else {
                        selectNextShulkerOrTakeoff();
                    }
                    return;
                }

                mc.player.setVelocity(0, -0.35, 0);
                if (stageTicks > 60) {
                    abortRestock(160);
                }
            }

            case PREP_SPOT -> {
                BlockPos targetBoxPos = (restockPad != null) ? restockPad.boxPos : findPlaceSpotSameLevel(mc.player.getBlockPos());
                if (targetBoxPos == null) {
                    abortRestock(140);
                    return;
                }
                shulkerPos = targetBoxPos;

                if (!mc.world.getBlockState(shulkerPos).isAir()) {
                    BlockUtils.breakBlock(shulkerPos, true);
                    if (stageTicks < 10) return;
                }
                if (!mc.world.getBlockState(shulkerPos.up()).isAir()) {
                    BlockUtils.breakBlock(shulkerPos.up(), true);
                    if (stageTicks < 15) return;
                }

                Vec3d boxCenter = Vec3d.ofBottomCenter(shulkerPos);
                double dx = mc.player.getX() - boxCenter.x;
                double dz = mc.player.getZ() - boxCenter.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 1.15 && dist > 0.01 && stageTicks < 12) {
                    mc.player.setVelocity((dx / dist) * 0.18, mc.player.getVelocity().y, (dz / dist) * 0.18);
                    return;
                }

                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
                restockStage = RestockStage.PLACE_SHULKER;
                stageTicks = 0;
            }

            case PLACE_SHULKER -> {
                int slot = grabMossNext
                    ? findShulkerInInv(Items.MOSS_BLOCK)
                    : findShulkerInInv(Items.BONE_BLOCK, Items.BONE_MEAL);

                if (slot == -1) {
                    if (!echestEmpty && countItem(Items.ENDER_CHEST) >= 1) {
                        restockStage = RestockStage.PLACE_ECHEST_1;
                        stageTicks = 0;
                    } else {
                        abortRestock(500);
                    }
                    return;
                }

                if (slot >= 9) {
                    InvUtils.move().from(slot).toHotbar(5);
                    return;
                }

                if (!BlockUtils.canPlace(shulkerPos, true)) {
                    shulkerPos = findPlaceSpotSameLevel(mc.player.getBlockPos());
                }
                if (shulkerPos == null) {
                    abortRestock(140);
                    return;
                }

                prevShulkers = countAllShulkers();
                FindItemResult item = new FindItemResult(slot, mc.player.getInventory().getStack(slot).getCount());
                if (BlockUtils.place(shulkerPos, item, true, 50, true)) {
                    restockStage = RestockStage.OPEN_SHULKER;
                    stageTicks = 0;
                } else if (stageTicks > 25) {
                    abortRestock(140);
                }
            }

            case OPEN_SHULKER -> {
                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
                if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
                    restockStage = RestockStage.LOOT_SHULKER;
                    stageTicks = 0;
                    return;
                }
                interactContainerOrFallback(shulkerPos, RestockStage.BREAK_SHULKER);
            }

            case LOOT_SHULKER -> {
                mc.player.setVelocity(0, 0, 0);
                if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler h)) {
                    restockStage = RestockStage.BREAK_SHULKER;
                    stageTicks = 0;
                    return;
                }

                if (stageTicks < 8 || clickDelay > 0) return;

                if (getEmptySlots() > 1) {
                    if (grabMossNext) {
                        if (countItem(Items.MOSS_BLOCK) < targetMossStacks.get() * 64
                            && quickMoveSlot(h, findItemInShulker(h, Items.MOSS_BLOCK))) {
                            return;
                        }
                    } else if (countBoneSlots() < getMaxBoneSlots()
                        && quickMoveSlot(h, findItemInShulker(h, Items.BONE_BLOCK, Items.BONE_MEAL))) {
                        return;
                    }
                }

                mc.player.closeHandledScreen();
                restockStage = RestockStage.BREAK_SHULKER;
                stageTicks = 0;
            }

            case BREAK_SHULKER -> {
                if (shulkerPos == null || mc.world.getBlockState(shulkerPos).isAir()) {
                    restockStage = RestockStage.COLLECT_SHULKER;
                    stageTicks = 0;
                    return;
                }

                Vec3d boxCenter = Vec3d.ofBottomCenter(shulkerPos);
                double dx = boxCenter.x - mc.player.getX();
                double dz = boxCenter.z - mc.player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.7) {
                    mc.player.setVelocity((dx / dist) * 0.16, mc.player.getVelocity().y, (dz / dist) * 0.16);
                }

                swapPickaxe();
                BlockUtils.breakBlock(shulkerPos, true);
                if (stageTicks > 70) {
                    restockStage = RestockStage.COLLECT_SHULKER;
                    stageTicks = 0;
                }
            }

            case COLLECT_SHULKER -> {
                ItemEntity droppedShulker = findDroppedShulkerEntity();
                if ((countAllShulkers() >= prevShulkers && droppedShulker == null) || stageTicks > 160) {
                    mc.player.setNoGravity(false);
                    shulkerPos = null;
                    selectNextShulkerOrTakeoff();
                    return;
                }

                dropJunkIfFull();

                Vec3d dropPos = droppedShulker != null
                    ? new Vec3d(droppedShulker.getX(), droppedShulker.getY(), droppedShulker.getZ())
                    : (shulkerPos != null ? Vec3d.ofBottomCenter(shulkerPos) : null);

                if (dropPos != null) {
                    double dx = dropPos.x - mc.player.getX();
                    double dy = dropPos.y - mc.player.getY();
                    double dz = dropPos.z - mc.player.getZ();
                    double horiz = Math.sqrt(dx * dx + dz * dz);

                    if (dy > 0.35 || mc.player.horizontalCollision || stageTicks > 25) {
                        if (!mc.player.isGliding() && mc.player.isOnGround()) {
                            mc.player.jump();
                        }
                        mc.player.setNoGravity(true);
                        double vx = horiz > 0.05 ? (dx / horiz) * 0.25 : 0;
                        double vz = horiz > 0.05 ? (dz / horiz) * 0.25 : 0;
                        double vy = MathHelper.clamp((dy + 0.2) * 0.45, -0.3, 0.35);
                        mc.player.setVelocity(vx, vy, vz);
                    } else if (horiz > 0.08) {
                        mc.player.setVelocity((dx / horiz) * 0.24, mc.player.getVelocity().y, (dz / horiz) * 0.24);
                    }
                }
            }

            case PLACE_ECHEST_1 -> {
                FindItemResult ec = findEChest();
                if (!ec.found()) {
                    echestEmpty = true;
                    abortRestock(500);
                    return;
                }

                BlockPos[] pair = findDoubleSpot(mc.player.getBlockPos());
                if (pair != null && ec.count() >= 2) {
                    echestPos1 = pair[0];
                    echestPos2 = pair[1];
                } else {
                    BlockPos single = findPlaceSpotSameLevel(mc.player.getBlockPos());
                    if (single == null) {
                        abortRestock(140);
                        return;
                    }
                    echestPos1 = single;
                    echestPos2 = null;
                }

                if (!mc.world.getBlockState(echestPos1.up()).isAir()) {
                    BlockUtils.breakBlock(echestPos1.up(), true);
                }

                if (BlockUtils.place(echestPos1, ec, true, 50, true)) {
                    restockStage = echestPos2 != null ? RestockStage.PLACE_ECHEST_2 : RestockStage.OPEN_ECHEST;
                    stageTicks = 0;
                } else if (stageTicks > 25) {
                    abortRestock(140);
                }
            }

            case PLACE_ECHEST_2 -> {
                FindItemResult ec = findEChest();
                if (!ec.found() || echestPos2 == null) {
                    restockStage = RestockStage.OPEN_ECHEST;
                    stageTicks = 0;
                    return;
                }
                if (BlockUtils.place(echestPos2, ec, true, 50, true) || stageTicks > 20) {
                    restockStage = RestockStage.OPEN_ECHEST;
                    stageTicks = 0;
                }
            }

            case OPEN_ECHEST -> {
                mc.player.setVelocity(0, 0, 0);
                if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
                    restockStage = RestockStage.LOOT_ECHEST;
                    stageTicks = 0;
                    return;
                }
                interactContainerOrFallback(echestPos1, RestockStage.BREAK_ECHEST_1);
            }

            case LOOT_ECHEST -> {
                mc.player.setVelocity(0, 0, 0);
                if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h)) {
                    restockStage = RestockStage.BREAK_ECHEST_1;
                    stageTicks = 0;
                    return;
                }

                if (stageTicks < 8 || clickDelay > 0) return;

                if (getEmptySlots() > 1) {
                    if (countShulkersWith(Items.MOSS_BLOCK) < echestMossShulkers.get()
                        && quickMoveSlot(h, findShulkerInEChest(h, Items.MOSS_BLOCK))) {
                        return;
                    }

                    if (countShulkersWith(Items.BONE_BLOCK, Items.BONE_MEAL) < echestBoneShulkers.get()
                        && quickMoveSlot(h, findShulkerInEChest(h, Items.BONE_BLOCK, Items.BONE_MEAL))) {
                        return;
                    }
                }

                if (findShulkerInEChest(h, Items.MOSS_BLOCK) == -1
                    && findShulkerInEChest(h, Items.BONE_BLOCK, Items.BONE_MEAL) == -1) {
                    echestEmpty = true;
                }

                mc.player.closeHandledScreen();
                restockStage = RestockStage.BREAK_ECHEST_1;
                stageTicks = 0;
            }

            case BREAK_ECHEST_1 -> {
                if (breakEChest(echestPos1)) {
                    echestPos1 = null;
                    restockStage = RestockStage.BREAK_ECHEST_2;
                    stageTicks = 0;
                }
            }

            case BREAK_ECHEST_2 -> {
                if (breakEChest(echestPos2)) {
                    echestPos2 = null;
                    selectNextShulkerOrTakeoff();
                }
            }

            case TAKEOFF -> {
                if (checkGliding() || stageTicks > 40) {
                    restockStage = RestockStage.NONE;
                    restockPad = null;
                    stageTicks = 0;
                    totalRestockTicks = 0;
                    if (chatFeedback.get()) info("Done restocking, back to work.");
                }
            }

            default -> restockStage = RestockStage.NONE;
        }
    }

    private ItemEntity findDroppedShulkerEntity() {
        if (mc.world == null || mc.player == null) return null;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ItemEntity ie && isShulker(ie.getStack()) && mc.player.distanceTo(e) < 8.0) {
                return ie;
            }
        }
        return null;
    }

    private int getEmptySlots() {
        if (mc.player == null) return 0;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) empty++;
        }
        return empty;
    }

    private void dropJunkIfFull() {
        if (mc.player == null || getEmptySlots() >= 3) return;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && JUNK.contains(s.getItem())) {
                InvUtils.drop().slot(i);
                if (getEmptySlots() >= 3) return;
            }
        }
    }

    private boolean isShulker(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private int countAllShulkers() {
        if (mc.player == null) return 0;
        int c = 0;
        for (int i = 0; i < 36; i++) {
            if (isShulker(mc.player.getInventory().getStack(i))) c++;
        }
        return c;
    }

    private boolean shulkerHas(ItemStack s, Item... items) {
        if (!isShulker(s)) return false;

        ContainerComponent cc = s.get(DataComponentTypes.CONTAINER);
        if (cc == null) return false;

        for (ItemStack in : cc.iterateNonEmpty()) {
            for (Item item : items) {
                if (in.isOf(item)) return true;
            }
        }
        return false;
    }

    private int findShulkerInInv(Item... items) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (shulkerHas(mc.player.getInventory().getStack(i), items)) return i;
        }
        return -1;
    }

    private int countShulkersWith(Item... items) {
        if (mc.player == null) return 0;
        int c = 0;
        for (int i = 0; i < 36; i++) {
            if (shulkerHas(mc.player.getInventory().getStack(i), items)) c++;
        }
        return c;
    }

    private int findShulkerInEChest(GenericContainerScreenHandler h, Item... items) {
        for (int i = 0; i < 27; i++) {
            if (shulkerHas(h.getSlot(i).getStack(), items)) return i;
        }
        return -1;
    }

    private int findItemInShulker(ShulkerBoxScreenHandler h, Item... items) {
        for (int i = 0; i < 27; i++) {
            ItemStack s = h.getSlot(i).getStack();
            for (Item item : items) {
                if (s.isOf(item)) return i;
            }
        }
        return -1;
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item)) count += s.getCount();
        }
        return count;
    }

    private int countBoneSlots() {
        if (mc.player == null) return 0;
        int slots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.BONE_MEAL) || s.isOf(Items.BONE_BLOCK)) {
                slots++;
            }
        }
        return slots;
    }

    private int getMaxBoneSlots() {
        if (mc.player == null) return 9;
        int taken = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && !s.isOf(Items.BONE_MEAL) && !s.isOf(Items.BONE_BLOCK)) taken++;
        }
        int free = Math.max(2, 36 - taken);
        return Math.max(1, (int) Math.floor(free * (bonemealFillPercent.get() / 100.0)));
    }

    private FindItemResult findEChest() {
        FindItemResult hb = InvUtils.findInHotbar(Items.ENDER_CHEST);
        if (hb.found()) return hb;
        FindItemResult inv = InvUtils.find(Items.ENDER_CHEST);
        if (inv.found()) {
            InvUtils.move().from(inv.slot()).toHotbar(5);
            return InvUtils.findInHotbar(Items.ENDER_CHEST);
        }
        return inv;
    }

    private void swapPickaxe() {
        FindItemResult pick = InvUtils.findInHotbar(s ->
            s.isOf(Items.NETHERITE_PICKAXE) || s.isOf(Items.DIAMOND_PICKAXE) || s.isOf(Items.IRON_PICKAXE));
        if (pick.found()) InvUtils.swap(pick.slot(), false);
    }

    private BlockPos findPlaceSpotSameLevel(BlockPos center) {
        if (mc.world == null) return null;
        for (int dist = 1; dist <= 2; dist++) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos p = center.offset(dir, dist);
                if (isSolidDryFloor(p.down())
                    && isPassableAir(mc.world.getBlockState(p))
                    && isPassableAir(mc.world.getBlockState(p.up()))) {
                    return p.toImmutable();
                }
            }
        }
        return null;
    }

    private BlockPos[] findDoubleSpot(BlockPos center) {
        if (mc.world == null) return null;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos p1 = center.offset(dir, 1);
            BlockPos p2 = p1.offset(dir.rotateYClockwise());
            if (BlockUtils.canPlace(p1, true) && BlockUtils.canPlace(p2, true)
                && isSolidDryFloor(p1.down())
                && isSolidDryFloor(p2.down())
                && mc.world.getBlockState(p1.up()).isAir()
                && mc.world.getBlockState(p2.up()).isAir()) {
                return new BlockPos[]{p1.toImmutable(), p2.toImmutable()};
            }
        }
        return null;
    }

    private record LocalWorkInfo(int convertible, int frontier) {}
    private record PatchStats(int frontier, int convertible, int fluid, int avgY) {}
    private record SectorInfo(double x, int y, double z, int convertible, int frontier) {}
    private record RestockPad(BlockPos standPos, BlockPos boxPos) {}
}
