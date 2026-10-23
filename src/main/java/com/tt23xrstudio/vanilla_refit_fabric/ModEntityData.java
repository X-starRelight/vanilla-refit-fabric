package com.tt23xrstudio.vanilla_refit_fabric;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;

/**
 * 自定义实体同步数据序列化器（牵引绳悬链功能）。
 *
 * <p>26.2 的 {@code EntityDataSerializers} 已删除 {@code COMPOUND_TAG} 与 {@code OPTIONAL_UUID}
 * （也没有任何 UUID 型），而绳结实体的伙伴列表与待连接玩家仍需走 {@code SynchedEntityData}
 * 自动同步——这里注册等价的序列化器补上。
 *
 * <p><b>注册时机敏感</b>：{@code SynchedEntityData.defineId} 在实体类静态初始化时就会查询
 * 序列化器表，所以本类的 {@link #initialize()} 必须在任何使用它们的实体类（如
 * {@code FenceKnotEntity}）被类加载之前执行；分配的是全局网络 id，两端必须注册同一批，
 * 故放在通用初始化（{@code VanillaRefitFabric.onInitialize}）而非客户端入口。
 */
public final class ModEntityData {

    private static final StreamCodec<FriendlyByteBuf, CompoundTag> COMPOUND_TAG_CODEC = StreamCodec.of(
            (buf, tag) -> buf.writeNbt(tag),
            buf -> {
                CompoundTag tag = buf.readNbt();
                return tag == null ? new CompoundTag() : tag;
            });

    private static final StreamCodec<FriendlyByteBuf, Optional<UUID>> OPTIONAL_UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                buf.writeBoolean(uuid.isPresent());
                if (uuid.isPresent()) {
                    buf.writeUUID(uuid.get());
                }
            },
            buf -> buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty());

    /** 绳结伙伴列表：{@code CompoundTag}（L 列表，元素 P=栅栏坐标 long、C=是否消耗过拴绳）。 */
    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG =
            EntityDataSerializer.forValueType(COMPOUND_TAG_CODEC);

    /** 绳结上“另一头在玩家手上”的待连接玩家标记。 */
    public static final EntityDataSerializer<Optional<UUID>> OPTIONAL_UUID =
            EntityDataSerializer.forValueType(OPTIONAL_UUID_CODEC);

    private ModEntityData() {
    }

    /** 必须先于任何使用这些序列化器的实体类加载（在 {@code ModEntities.initialize()} 之前调用）。 */
    public static void initialize() {
        // 26.2 + Fabric API：原版 EntityDataSerializers.registerSerializer 只用整数 id，
        // registry sync 时会错位（API 直接拒绝），必须走带 Identifier 的 Fabric 入口——
        // 两端按 id 注册同一批序列化器，整数 id 由 Fabric 分配。
        FabricEntityDataRegistry.register(
                Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "compound_tag"), COMPOUND_TAG);
        FabricEntityDataRegistry.register(
                Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "optional_uuid"), OPTIONAL_UUID);
    }
}
