package carpet.helpers;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static carpet.CarpetSettings.commandLazyChunkBehavior;


public class LazyChunkBehaviorHelper {
    private static final Map<Integer, Set<Long>> lazyProcessingChunks = new HashMap<>();

    public static void listLazyChunks(ICommandSender sender){
        if(!lazyProcessingChunks.isEmpty()) {
            for (Map.Entry<Integer, Set<Long>> dimension : lazyProcessingChunks.entrySet()) {
                for (long packed : dimension.getValue()) {
                    ChunkPos chunk = new ChunkPos((int) packed, (int) (packed >> 32));
                    TextComponentString text = new TextComponentString("Chunk " + chunk.x + ", " + chunk.z + " in dimension " + dimension.getKey());
                    text.getStyle().setColor(TextFormatting.GREEN);
                    sender.sendMessage(text);
                }
            }

        }
        else{
            TextComponentString text = new TextComponentString("No chunks to list." );
            text.getStyle().setColor(TextFormatting.GREEN);
            sender.sendMessage(text);
        }
    }
    public static void removeAll(){
        lazyProcessingChunks.clear();
    }

    public static boolean addLazyChunk(Chunk chunk){
        return lazyProcessingChunks.computeIfAbsent(chunk.getWorld().provider.getDimensionType().getId(), key -> new HashSet<>())
                .add(ChunkPos.asLong(chunk.x, chunk.z));
    }
    public static boolean removeLazyChunk(Chunk chunk){
        int dimension = chunk.getWorld().provider.getDimensionType().getId();
        Set<Long> chunks = lazyProcessingChunks.get(dimension);
        if (chunks == null || !chunks.remove(ChunkPos.asLong(chunk.x, chunk.z))) return false;
        if (chunks.isEmpty()) lazyProcessingChunks.remove(dimension);
        return true;
    }
    public static boolean containsLazyChunk(Chunk chunk){
        Set<Long> chunks = lazyProcessingChunks.get(chunk.getWorld().provider.getDimensionType().getId());
        return chunks != null && chunks.contains(ChunkPos.asLong(chunk.x, chunk.z));
    }

    public static boolean shouldUpdate(Entity entityIn) {
        World world = entityIn.getEntityWorld();
        return shouldUpdate(world, entityIn.getPosition());
    }

    public static boolean shouldUpdate(World worldIn, BlockPos pos) {
        return shouldUpdate(worldIn, new ChunkPos(pos.getX()>> 4,pos.getZ()>> 4));
    }

    public static boolean shouldUpdate(World worldIn, ChunkPos pos) {
        Chunk chunk = worldIn.getChunk(pos.x, pos.z);
        return !containsLazyChunk(chunk);
    }
}
