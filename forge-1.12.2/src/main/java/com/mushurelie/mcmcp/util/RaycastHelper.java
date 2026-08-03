package com.mushurelie.mcmcp.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Ray-trace generique (bloc + entite) utilise par raycast() et get_camera_view().
 * DOIT etre appele depuis le thread principal (World n'est pas thread-safe).
 */
public final class RaycastHelper {

    private RaycastHelper() {
    }

    public static final class Result {
        public final boolean hit;
        public final String hitType; // "block", "entity" ou "none"
        public final BlockPos blockPos;
        public final IBlockState blockState;
        public final Entity entity;
        public final Vec3d hitVec;
        public final double distance;

        private Result(boolean hit, String hitType, BlockPos blockPos, IBlockState blockState,
                       Entity entity, Vec3d hitVec, double distance) {
            this.hit = hit;
            this.hitType = hitType;
            this.blockPos = blockPos;
            this.blockState = blockState;
            this.entity = entity;
            this.hitVec = hitVec;
            this.distance = distance;
        }

        static Result none(Vec3d endPoint, double distance) {
            return new Result(false, "none", null, null, null, endPoint, distance);
        }

        static Result block(BlockPos pos, IBlockState state, Vec3d hitVec, double distance) {
            return new Result(true, "block", pos, state, null, hitVec, distance);
        }

        static Result entity(Entity entity, Vec3d hitVec, double distance) {
            return new Result(true, "entity", null, null, entity, hitVec, distance);
        }
    }

    public static Result raycast(World world, Vec3d origin, Vec3d direction, double maxDistance, Entity ignore) {
        Vec3d dir = direction.normalize();
        Vec3d end = origin.add(dir.x * maxDistance, dir.y * maxDistance, dir.z * maxDistance);

        RayTraceResult blockHit = world.rayTraceBlocks(origin, end, false, false, true);
        double blockDistance = blockHit != null
            ? origin.distanceTo(blockHit.hitVec)
            : maxDistance;

        // Recherche de la premiere entite touchee, plus proche que le bloc trouve.
        AxisAlignedBB searchBox = new AxisAlignedBB(origin, end).grow(1.0D);
        List<Entity> candidates = world.getEntitiesWithinAABBExcludingEntity(ignore, searchBox);

        Entity closestEntity = null;
        Vec3d closestEntityHit = null;
        double closestEntityDistance = blockDistance;

        for (Entity candidate : candidates) {
            if (!candidate.canBeCollidedWith() && candidate.getEntityBoundingBox() == null) {
                continue;
            }
            AxisAlignedBB box = candidate.getEntityBoundingBox().grow(candidate.getCollisionBorderSize());
            RayTraceResult intercept = box.calculateIntercept(origin, end);
            if (intercept == null) {
                continue;
            }
            double distance = origin.distanceTo(intercept.hitVec);
            if (distance < closestEntityDistance) {
                closestEntityDistance = distance;
                closestEntity = candidate;
                closestEntityHit = intercept.hitVec;
            }
        }

        if (closestEntity != null) {
            return Result.entity(closestEntity, closestEntityHit, closestEntityDistance);
        }
        if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            return Result.block(pos, world.getBlockState(pos), blockHit.hitVec, blockDistance);
        }
        return Result.none(end, maxDistance);
    }
}
