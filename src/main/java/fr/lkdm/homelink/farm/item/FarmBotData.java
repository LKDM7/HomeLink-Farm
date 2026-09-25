package fr.lkdm.homelink.farm.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a FarmBot keeps while carried as an item: battery level and harvest counter. Its
 * cargo never travels inside the item (it is handed out when the robot is picked up).
 */
public record FarmBotData(int battery, int harvested) {
    public static final FarmBotData NEW = new FarmBotData(100, 0);

    public static final Codec<FarmBotData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100).fieldOf("battery").forGetter(FarmBotData::battery),
            Codec.INT.fieldOf("harvested").forGetter(FarmBotData::harvested)
    ).apply(instance, FarmBotData::new));

    public static final StreamCodec<ByteBuf, FarmBotData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FarmBotData::battery,
            ByteBufCodecs.VAR_INT, FarmBotData::harvested,
            FarmBotData::new);

    public FarmBotData {
        battery = Math.max(0, Math.min(100, battery));
        harvested = Math.max(0, harvested);
    }
}
