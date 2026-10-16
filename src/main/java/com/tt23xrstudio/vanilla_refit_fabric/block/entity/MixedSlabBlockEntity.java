package com.tt23xrstudio.vanilla_refit_fabric.block.entity;

import com.tt23xrstudio.vanilla_refit_fabric.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 混合半砖方块实体：一个格子里放两块“不同材质”的半砖。
 *
 * slabA / slabB 各自是原版水平半砖方块(如 stone_slab / oak_slab)。它们的几何朝向由
 * 方块状态里的 MODE / TYPE 决定（水平上下堆叠 或 竖直南北/东西拼合），本实体只负责记住
 * 这两块半砖分别是什么材质。渲染时由客户端模型包装器读取并合并两个材质模型的几何。
 */
public class MixedSlabBlockEntity extends BlockEntity {

    private Block slabA = Blocks.STONE_SLAB;
    private Block slabB = Blocks.STONE_SLAB;

    public MixedSlabBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** BlockEntityType 工厂方法（供 {@code new BlockEntityType<>(...)} 使用）。 */
    public static MixedSlabBlockEntity create(BlockPos pos, BlockState state) {
        return new MixedSlabBlockEntity(ModBlockEntities.MIXED_SLAB, pos, state);
    }

    public Block getSlabA() {
        return slabA;
    }

    public Block getSlabB() {
        return slabB;
    }

    /** 设置两块半砖材质，并用“合并上半/北半”判断哪块是 A。 */
    public boolean setBlocks(Block slabA, Block slabB) {
        if (!isUsableSlab(slabA) || !isUsableSlab(slabB)) {
            return false;
        }
        this.slabA = slabA;
        this.slabB = slabB;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        refreshClientModel();
        return true;
    }

    /**
     * 材质变了以后刷新客户端渲染。
     *
     * <p>阶段 2 备注：原 NeoForge 版这里做两件事——让模型数据缓存失效（requestModelDataUpdate）
     * + 标脏所在区块（MixedSlabClientRefresh）。Fabric 版改用模型装配事件后，
     * 由 {@code setBlocks} 里的 {@code level.sendBlockUpdated} + 收包后 vanilla 自带的区块标脏覆盖；
     * 若阶段 2 实测新材质要等旁边方块更新才显示，在此补 Fabric 侧的重绘触发。
     */
    private void refreshClientModel() {
        // 阶段2：按 Fabric 渲染管线补重绘触发（原 requestModelDataUpdate + MixedSlabClientRefresh）
    }

    public Block getFirstSlab() {
        return slabA;
    }

    public Block getSecondSlab() {
        return slabB;
    }

    private static boolean isUsableSlab(Block block) {
        return block instanceof SlabBlock && block != Blocks.AIR;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.slabA = readSlab(input.getStringOr("slab_a", ""));
        this.slabB = readSlab(input.getStringOr("slab_b", ""));
        refreshClientModel();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        output.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
    }

    /**
     * 客户端从区块包/方块实体数据包里恢复实体时，使用的是 {@code getUpdateTag} 的网络数据
     * （而非磁盘保存的 {@code saveAdditional}）。若不覆写，客户端收到的就是空 tag，
     * 材质会退回默认石头。因此这里也写入两块材质。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        tag.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
        return tag;
    }

    /** 放置/变更时向客户端同步实体数据（配合 {@code getUpdateTag}）。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static Block readSlab(String id) {
        if (id == null || id.isEmpty()) {
            return Blocks.STONE_SLAB;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            return block instanceof SlabBlock ? block : Blocks.STONE_SLAB;
        } catch (RuntimeException exception) {
            // 存档里的 id 可能被写坏、或来自已被移除的方块（解析会抛异常）。
            // 这里必须兜住，否则区块加载直接崩溃；回退成石头半砖，和“不是半砖就回退”保持一致。
            return Blocks.STONE_SLAB;
        }
    }
}
