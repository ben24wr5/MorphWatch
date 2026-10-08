package com.morphwatch;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Locale;

/**
 * Every mob the watch can turn you into.
 * Powers (R and Z) live in {@link Abilities}, punch effects in {@link MobAttacks}.
 */
public enum MorphForm {
    NONE(null, 20, 0, 0, 0, null, kin()),

    CHICKEN(EntityType.CHICKEN, 4, 80, 60, Flag.NO_FALL, SoundEvents.CHICKEN_AMBIENT,
            kin(EntityType.CHICKEN), fx(MobEffects.SLOW_FALLING, 0)),
    CAT(EntityType.CAT, 10, 200, 60, Flag.NO_FALL, SoundEvents.CAT_AMBIENT,
            kin(EntityType.CAT), fx(MobEffects.MOVEMENT_SPEED, 0), fx(MobEffects.NIGHT_VISION, 0)),
    CREEPER(EntityType.CREEPER, 20, 300, 160, 0, SoundEvents.CREEPER_PRIMED,
            kin(EntityType.CREEPER)),
    SPIDER(EntityType.SPIDER, 16, 60, 300, Flag.CLIMBS, SoundEvents.SPIDER_AMBIENT,
            kin(EntityType.SPIDER, EntityType.CAVE_SPIDER), fx(MobEffects.NIGHT_VISION, 0)),
    ZOMBIE(EntityType.ZOMBIE, 20, 200, 400, 0, SoundEvents.ZOMBIE_AMBIENT,
            kin(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER),
            fx(MobEffects.DAMAGE_BOOST, 0)),
    SKELETON(EntityType.SKELETON, 20, 20, 400, 0, SoundEvents.SKELETON_AMBIENT,
            kin(EntityType.SKELETON, EntityType.STRAY), fx(MobEffects.MOVEMENT_SPEED, 0)),
    BLAZE(EntityType.BLAZE, 20, 15, 200, Flag.NO_FALL, SoundEvents.BLAZE_AMBIENT,
            kin(EntityType.BLAZE), fx(MobEffects.FIRE_RESISTANCE, 0), fx(MobEffects.SLOW_FALLING, 0)),
    ENDERMAN(EntityType.ENDERMAN, 40, 40, 200, Flag.NO_FALL, SoundEvents.ENDERMAN_AMBIENT,
            kin(EntityType.ENDERMAN), fx(MobEffects.MOVEMENT_SPEED, 0)),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, 20, 200, 60, 0, SoundEvents.WITHER_SKELETON_AMBIENT,
            kin(EntityType.WITHER_SKELETON), fx(MobEffects.FIRE_RESISTANCE, 0), fx(MobEffects.DAMAGE_BOOST, 0)),
    IRON_GOLEM(EntityType.IRON_GOLEM, 100, 100, 80, Flag.NO_FALL, SoundEvents.IRON_GOLEM_REPAIR,
            kin(EntityType.IRON_GOLEM), fx(MobEffects.DAMAGE_BOOST, 0), fx(MobEffects.MOVEMENT_SLOWDOWN, 0)),
    BAT(EntityType.BAT, 6, 300, 200, Flag.FLIES | Flag.NO_FALL, SoundEvents.BAT_AMBIENT,
            kin(EntityType.BAT), fx(MobEffects.NIGHT_VISION, 0)),
    SNOW_GOLEM(EntityType.SNOW_GOLEM, 4, 10, 300, 0, SoundEvents.SNOW_GOLEM_AMBIENT,
            kin(EntityType.SNOW_GOLEM));

    /** Bit flags, kept in their own class so the enum constants above can use them. */
    static final class Flag {
        static final int FLIES = 1;
        static final int NO_FALL = 2;
        static final int CLIMBS = 4;
        private Flag() {}
    }

    public record Effect(MobEffect effect, int amplifier) {}

    private static Effect fx(MobEffect effect, int amplifier) {
        return new Effect(effect, amplifier);
    }

    private static List<EntityType<?>> kin(EntityType<?>... types) {
        return List.of(types);
    }

    private final EntityType<? extends LivingEntity> type;
    private final int maxHealth;
    private final int cooldown1;
    private final int cooldown2;
    private final int flags;
    private final SoundEvent sound;
    private final List<EntityType<?>> kin;
    private final List<Effect> effects;

    MorphForm(EntityType<? extends LivingEntity> type, int maxHealth, int cooldown1, int cooldown2, int flags,
              SoundEvent sound, List<EntityType<?>> kin, Effect... effects) {
        this.type = type;
        this.maxHealth = maxHealth;
        this.cooldown1 = cooldown1;
        this.cooldown2 = cooldown2;
        this.flags = flags;
        this.sound = sound;
        this.kin = kin;
        this.effects = List.of(effects);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public EntityType<? extends LivingEntity> type() { return type; }
    /** Hearts x2 the form has (Iron Golem 100, Chicken 4...). */
    public int maxHealth() { return maxHealth; }
    public int cooldown1() { return cooldown1; }
    public int cooldown2() { return cooldown2; }
    public boolean canFly() { return (flags & Flag.FLIES) != 0; }
    public boolean noFallDamage() { return (flags & Flag.NO_FALL) != 0; }
    public boolean climbsWalls() { return (flags & Flag.CLIMBS) != 0; }
    public SoundEvent sound() { return sound; }
    public List<Effect> effects() { return effects; }

    /** Mobs that count as "your kind": they won't attack you and will follow you. */
    public boolean isKin(EntityType<?> other) {
        return kin.contains(other);
    }

    public Component displayName() {
        return this == NONE ? Component.literal("Human") : type.getDescription();
    }

    public static MorphForm byId(String id) {
        if (id == null || id.isEmpty()) return NONE;
        for (MorphForm f : values()) {
            if (f.id().equals(id)) return f;
        }
        return NONE;
    }

    public static MorphForm byOrdinal(int ordinal) {
        MorphForm[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
    }

    /** The form you get by scanning this mob type (Husks count as Zombies, Strays as Skeletons...). */
    public static MorphForm byType(EntityType<?> type) {
        for (MorphForm f : values()) {
            if (f != NONE && f.type == type) return f;
        }
        for (MorphForm f : values()) {
            if (f != NONE && f.isKin(type)) return f;
        }
        return null;
    }

    /** All the real mob forms (everything except NONE). */
    public static List<MorphForm> mobs() {
        return List.of(values()).subList(1, values().length);
    }
}
