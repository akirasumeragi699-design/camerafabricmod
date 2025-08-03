import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

import java.util.*;

public class CameraMod implements ModInitializer {

    public static final String MOD_ID = "cameramod";

    public static final Item CAMERA_ITEM = new Item(new FabricItemSettings()) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext context) {
            if (!context.getWorld().isClient) {
                BlockPos pos = context.getBlockPos().offset(context.getSide());
                PlayerEntity player = context.getPlayer();
                if (player != null) {
                    CameraState.get(context.getWorld()).addCamera(pos, player.getUuid());
                    player.sendMessage(Text.of("📷 Đã đặt camera tại: " + pos.toShortString()), false);
                }
            }
            return ActionResult.SUCCESS;
        }
    };

    public static final Item VIEWER_ITEM = new Item(new FabricItemSettings()) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            if (!world.isClient) {
                List<BlockPos> cameras = CameraState.get(world).getCameras(player.getUuid());
                if (cameras.isEmpty()) {
                    player.sendMessage(Text.of("🚫 Không có camera nào!"), false);
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }

                BlockPos pos = cameras.get(0);
                player.sendMessage(Text.of("👁️ Đang xem camera tại " + pos.toShortString()), false);

                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.teleport(serverPlayer.getServerWorld(), pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
            return TypedActionResult.success(player.getStackInHand(hand));
        }
    };

    public static final RegistryKey<ItemGroup> ITEM_GROUP = RegistryKey.of(Registries.ITEM_GROUP.getKey(), id("group"));

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("camera"), CAMERA_ITEM);
        Registry.register(Registries.ITEM, id("viewer"), VIEWER_ITEM);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(CAMERA_ITEM);
            entries.add(VIEWER_ITEM);
        });
    }

    public static class CameraState extends PersistentState {
        private final Map<UUID, List<BlockPos>> cameras = new HashMap<>();

        public void addCamera(BlockPos pos, UUID uuid) {
            cameras.computeIfAbsent(uuid, id -> new ArrayList<>()).add(pos);
            markDirty();
        }

        public List<BlockPos> getCameras(UUID uuid) {
            return cameras.getOrDefault(uuid, Collections.emptyList());
        }

        public static CameraState get(World world) {
            PersistentStateManager manager = world.getServer().getOverworld().getPersistentStateManager();
            return manager.getOrCreate(nbt -> new CameraState(), CameraState::new, MOD_ID);
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            return new NbtCompound();
        }
    }
}
