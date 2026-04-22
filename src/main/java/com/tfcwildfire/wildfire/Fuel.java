package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class Fuel {

    private Fuel() {}

    public enum Class {
        NONE,
        FINE,
        MEDIUM,
        HEAVY
    }

    public record FuelProps(
        Class fuelClass,
        int flammability,
        int spreadSpeed
    ) {}

    public static FuelProps of(ServerLevel level, BlockPos fuelPos, Direction face) {
        final BlockState s = level.getBlockState(fuelPos);
        if (!s.isFlammable(level, fuelPos, face)) {
            return new FuelProps(Class.NONE, 0, 0);
        }

        final int flammability = s.getFlammability(level, fuelPos, face);
        final int spreadSpeed = s.getFireSpreadSpeed(level, fuelPos, face);

        final Class cls;
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE_BY_TREES)) {
            cls = Class.FINE;
        } else if (s.is(BlockTags.LOGS_THAT_BURN) || s.is(BlockTags.PLANKS)) {
            cls = Class.HEAVY;
        } else {
            cls = Class.MEDIUM;
        }

        return new FuelProps(cls, flammability, spreadSpeed);
    }

    public static float classIgnitionMultiplier(Class c) {
        return switch (c) {
            case NONE -> 0f;
            case FINE -> 1.35f;
            case MEDIUM -> 1.0f;
            case HEAVY -> 0.70f;
        };
    }

    public static float classCanopyMultiplier(Class c) {
        return switch (c) {
            case FINE -> 1.75f;
            default -> 1.0f;
        };
    }

    public static float normalizeFlammability(int flammability) {
        return Mth.clamp(flammability / 60f, 0f, 1f);
    }

    public static float normalizeSpreadSpeed(int spreadSpeed) {
        return Mth.clamp(spreadSpeed / 60f, 0f, 1f);
    }
}
