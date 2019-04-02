package io.github.opencubicchunks.worldpainterplugin;

import static io.github.opencubicchunks.worldpainterplugin.Version.VERSION;
import static java.util.Collections.singletonList;
import static org.pepsoft.worldpainter.Constants.DIM_END;
import static org.pepsoft.worldpainter.Constants.DIM_NETHER;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.GameType.CREATIVE;
import static org.pepsoft.worldpainter.GameType.SURVIVAL;
import static org.pepsoft.worldpainter.Generator.DEFAULT;
import static org.pepsoft.worldpainter.Platform.Capability.BIOMES;
import static org.pepsoft.worldpainter.Platform.Capability.BLOCK_BASED;
import static org.pepsoft.worldpainter.Platform.Capability.PRECALCULATED_LIGHT;
import static org.pepsoft.worldpainter.Platform.Capability.SEED;
import static org.pepsoft.worldpainter.Platform.Capability.SET_SPAWN_POINT;

import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.NBTInputStream;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.worldpainter.Constants;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.JavaPostProcessor;
import org.pepsoft.worldpainter.exporting.PostProcessor;
import org.pepsoft.worldpainter.exporting.WorldExporter;
import org.pepsoft.worldpainter.mapexplorer.MapRecognizer;
import org.pepsoft.worldpainter.plugins.AbstractPlugin;
import org.pepsoft.worldpainter.plugins.BlockBasedPlatformProvider;
import org.pepsoft.worldpainter.util.MinecraftUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class CubicChunksPlatformProvider extends AbstractPlugin implements BlockBasedPlatformProvider {
    public CubicChunksPlatformProvider() {
        super("CubicChunksPlatform", VERSION);
        init();
    }

    // PlatformProvider

    @Override
    public List<Platform> getKeys() {
        return singletonList(CUBICCHUNKS);
    }

    @Override public int[] getDimensions(Platform platform, File file) {
        Path world = file.toPath();
        if (!isCubicWorld(world)) {
            return new int[0];
        }
        try {
            if (platform.equals(CUBICCHUNKS)) {
                try (Stream<Path> dirs = Files.list(world)) {
                    return IntStream.concat(
                        dirs.filter(this::isCubicChunksDimension)
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .filter(name -> name.matches("DIM-?[\\d]+"))
                            .map(name -> name.substring("DIM".length()))
                            .mapToInt(Integer::parseInt), IntStream.of(0)
                            .filter(this::isSupportedDimension)
                            .map(this::toWPDimensionId)
                    ).toArray();
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new int[0];
    }

    private int toWPDimensionId(int id) {
        switch (id) {
            case 0:
                return DIM_NORMAL;
            case 1:
                return DIM_END;
            case -1:
                return DIM_NETHER;
            default:
                assert false;
                return id;
        }
    }

    private boolean isSupportedDimension(int id) {
        return id == 0 || id == 1 || id == -1;
    }

    public boolean isCubicWorld(Path worldDir) {
        Path levelDat = worldDir.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return false;
        }
        try (NBTInputStream in = new NBTInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(levelDat))))) {
            CompoundTag tag = (CompoundTag) in.readTag();
            CompoundTag data = (CompoundTag) tag.getTag("Data");
            return ((ByteTag) data.getTag("isCubicWorld")).getValue() == 1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isCubicChunksDimension(Path path) {
        Path ccData = path.resolve("data/cubicChunksData.dat");
        if (!Files.exists(ccData)) {
            // if this file hasn't been created and this is in fact CC dimension, no harm one as there aren't any chunks yet
            return false;
        }
        try (NBTInputStream in = new NBTInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(ccData))))) {
            CompoundTag tag = (CompoundTag) in.readTag();
            ByteTag isCC = ((ByteTag) tag.getTag("isCubicChunks"));
            return isCC != null && isCC.getValue() == 1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Chunk createChunk(Platform platform, int x, int z, int maxHeight) {
        if (!platform.equals(CUBICCHUNKS)) {
            throw new IllegalArgumentException("Platform " + platform + " not supported");
        }
        return new Chunk16Virtual(x, z, maxHeight, EditMode.EDITABLE);
    }

    @Override
    public ChunkStore getChunkStore(Platform platform, File worldDir, int dimension) {
        if (!platform.equals(CUBICCHUNKS)) {
            throw new IllegalArgumentException("Platform " + platform + " not supported");
        }
        try {
            return new CubicChunkStore(worldDir, dimension, Integer.MAX_VALUE / 2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WorldExporter getExporter(World2 world) {
        Platform platform = world.getPlatform();
        if (!platform.equals(CUBICCHUNKS)) {
            throw new IllegalArgumentException("Platform " + platform + " not supported");
        }
        return new CubicChunksWorldExporter(world);
    }

    @Override
    public File getDefaultExportDir(Platform platform) {
        File minecraftDir = MinecraftUtil.findMinecraftDir();
        return (minecraftDir != null) ? new File(minecraftDir, "saves") : null;
    }

    @Override
    public PostProcessor getPostProcessor(Platform platform) {
        if (!platform.equals(CUBICCHUNKS)) {
            throw new IllegalArgumentException("Platform " + platform + " not supported");
        }
        return new JavaPostProcessor();
    }

    @Override
    public MapRecognizer getMapRecognizer() {
        return new CubicChunksMapRecognizer(this);
    }

    private void init() {

    }

    static final Platform CUBICCHUNKS = new Platform(
        "io.github.oopencubicchunks.cubicchunks",
        "Cubic Chunks (1.10.2-1.12.2)",
        256,
        Math.min(Constants.MAX_HEIGHT, Integer.MAX_VALUE / 2),
        Math.min(Constants.MAX_HEIGHT, Integer.MAX_VALUE / 2),
        Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
        Arrays.asList(SURVIVAL, CREATIVE),
        singletonList(DEFAULT),
        Arrays.asList(DIM_NORMAL, DIM_NETHER, DIM_END),
        EnumSet.of(BLOCK_BASED, BIOMES, PRECALCULATED_LIGHT, SET_SPAWN_POINT, SEED));
}