package com.mushurelie.mcmcp.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Ray-trace generique (bloc + entite) utilise par raycast() et get_camera_view().
 * DOIT etre appele depuis le thread principal (Level n'est pas thread-safe).
 */
public final class RaycastHelper {

    private RaycastHelper() {
    }

    public static final class Result {
        public final boolean hit;
        public final String hitType; // "block", "entity" ou "none"
        public final BlockPos blockPos;
        public final BlockState blockState;
        public final Entity entity;
        public final Vec3 hitVec;
        public final double distance;

        private Result(boolean hit, String hitType, BlockPos blockPos, BlockState blockState,
                       Entity entity, Vec3 hitVec, double distance) {
            this.hit = hit;
            this.hitType = hitType;
            this.blockPos = blockPos;
            this.blockState = blockState;
            this.entity = entity;
            this.hitVec = hitVec;
            this.distance = distance;
        }

        static Result none(Vec3 endPoint, double distance) {
            return new Result(false, "none", null, null, null, endPoint, distance);
        }

        static Result block(BlockPos pos, BlockState state, Vec3 hitVec, double distance) {
            return new Result(true, "block", pos, state, null, hitVec, distance);
        }

        static Result entity(Entity entity, Vec3 hitVec, double distance) {
            return new Result(true, "entity", null, null, entity, hitVec, distance);
        }
    }

    public static Result raycast(Level world, Vec3 origin, Vec3 direction, double maxDistance, Entity ignore) {
        Vec3 dir = direction.normalize();
        Vec3 end = origin.add(dir.x * maxDistance, dir.y * maxDistance, dir.z * maxDistance);

        BlockHitResult blockHit = world.clip(new ClipContext(origin, end,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ignore));
        double blockDistance = blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
            ? origin.distanceTo(blockHit.getLocation())
            : maxDistance;

        // Recherche de la premiere entite touchee, plus proche que le bloc trouve.
        AABB searchBox = new AABB(origin.x, origin.y, origin.z, end.x, end.y, end.z).inflate(1.0D);
        List<Entity> candidates = world.getEntities(ignore, searchBox, entity -> true);

        Entity closestEntity = null;
        Vec3 closestEntityHit = null;
        double closestEntityDistance = blockDistance;

        for (Entity candidate : candidates) {
            if (!candidate.canBeCollidedWith()) {
                continue;
            }
            AABB box = candidate.getBoundingBox().inflate(0.1D);
            Optional<Vec3> intercept = box.clip(origin, end);
            if (!intercept.isPresent()) {
                continue;
            }
            double distance = origin.distanceTo(intercept.get());
            if (distance < closestEntityDistance) {
                closestEntityDistance = distance;
                closestEntity = candidate;
                closestEntityHit = intercept.get();
            }
        }

        if (closestEntity != null) {
            return Result.entity(closestEntity, closestEntityHit, closestEntityDistance);
        }
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            return Result.block(pos, world.getBlockState(pos), blockHit.getLocation(), blockDistance);
        }
        return Result.none(end, maxDistance);
    }
}
