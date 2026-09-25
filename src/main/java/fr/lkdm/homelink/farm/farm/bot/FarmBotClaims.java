package fr.lkdm.homelink.farm.farm.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Short-lived reservations of target crops, so two robots working from the same Crop Monitor
 * do not drive to the same plant. Memory only; an expired claim is simply ignored.
 */
public final class FarmBotClaims {
    private record Claim(UUID robot, long expiresAt) {
    }

    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS = new WeakHashMap<>();
    private static final int MAX_CLAIMS_PER_LEVEL = 4096;

    private FarmBotClaims() {
    }

    public static boolean claimedByOther(ServerLevel level, BlockPos pos, UUID robot) {
        Claim claim = CLAIMS.getOrDefault(level, Map.of()).get(pos);
        return claim != null && !claim.robot().equals(robot) && claim.expiresAt() > level.getGameTime();
    }

    public static void claim(ServerLevel level, BlockPos pos, UUID robot, int ticks) {
        Map<BlockPos, Claim> claims = CLAIMS.computeIfAbsent(level, key -> new HashMap<>());
        long now = level.getGameTime();
        if (claims.size() >= MAX_CLAIMS_PER_LEVEL) claims.values().removeIf(claim -> claim.expiresAt() <= now);
        claims.put(pos.immutable(), new Claim(robot, now + ticks));
    }

    public static void release(ServerLevel level, BlockPos pos, UUID robot) {
        Map<BlockPos, Claim> claims = CLAIMS.get(level);
        if (claims != null) claims.computeIfPresent(pos, (key, claim) -> claim.robot().equals(robot) ? null : claim);
    }

    public static void forget(ServerLevel level) {
        CLAIMS.remove(level);
    }
}
