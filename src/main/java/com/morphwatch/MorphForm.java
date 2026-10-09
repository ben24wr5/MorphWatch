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
 * Powers (G and H) live in {@link Abilities}, super powers (B and N) in {@link SuperPowers}, punch effects in {@link MobAttacks}.
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
            kin(EntityType.SNOW_GOLEM)),

    // ---- every other mob in 1.20.1 (powers in MobPowers)
    ALLAY(EntityType.ALLAY, 20, 100, 200, Flag.FLIES | Flag.NO_FALL, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
            kin(EntityType.ALLAY)),
    CAMEL(EntityType.CAMEL, 32, 80, 300, 0, SoundEvents.CAMEL_AMBIENT,
            kin(EntityType.CAMEL), fx(MobEffects.MOVEMENT_SPEED, 0)),
    COW(EntityType.COW, 10, 200, 160, 0, SoundEvents.COW_AMBIENT,
            kin(EntityType.COW, EntityType.MOOSHROOM)),
    DONKEY(EntityType.DONKEY, 22, 100, 200, 0, SoundEvents.DONKEY_AMBIENT,
            kin(EntityType.DONKEY, EntityType.MULE), fx(MobEffects.MOVEMENT_SPEED, 0)),
    MULE(EntityType.MULE, 22, 100, 240, 0, SoundEvents.MULE_AMBIENT,
            kin(EntityType.MULE, EntityType.DONKEY), fx(MobEffects.MOVEMENT_SPEED, 0)),
    HORSE(EntityType.HORSE, 22, 200, 100, 0, SoundEvents.HORSE_AMBIENT,
            kin(EntityType.HORSE), fx(MobEffects.MOVEMENT_SPEED, 1), fx(MobEffects.JUMP, 1)),
    SKELETON_HORSE(EntityType.SKELETON_HORSE, 15, 200, 100, 0, SoundEvents.SKELETON_HORSE_AMBIENT,
            kin(EntityType.SKELETON_HORSE, EntityType.SKELETON, EntityType.STRAY), fx(MobEffects.MOVEMENT_SPEED, 1), fx(MobEffects.JUMP, 1)),
    ZOMBIE_HORSE(EntityType.ZOMBIE_HORSE, 15, 200, 100, 0, SoundEvents.ZOMBIE_HORSE_AMBIENT,
            kin(EntityType.ZOMBIE_HORSE, EntityType.ZOMBIE, EntityType.HUSK), fx(MobEffects.MOVEMENT_SPEED, 1), fx(MobEffects.JUMP, 1)),
    FOX(EntityType.FOX, 10, 60, 300, Flag.NO_FALL, SoundEvents.FOX_AMBIENT,
            kin(EntityType.FOX), fx(MobEffects.MOVEMENT_SPEED, 0), fx(MobEffects.NIGHT_VISION, 0)),
    FROG(EntityType.FROG, 10, 80, 60, Flag.NO_FALL, SoundEvents.FROG_AMBIENT,
            kin(EntityType.FROG), fx(MobEffects.JUMP, 1), fx(MobEffects.WATER_BREATHING, 0)),
    MOOSHROOM(EntityType.MOOSHROOM, 10, 200, 160, 0, SoundEvents.COW_AMBIENT,
            kin(EntityType.MOOSHROOM, EntityType.COW)),
    OCELOT(EntityType.OCELOT, 10, 60, 200, Flag.NO_FALL, SoundEvents.OCELOT_AMBIENT,
            kin(EntityType.OCELOT), fx(MobEffects.MOVEMENT_SPEED, 1), fx(MobEffects.NIGHT_VISION, 0)),
    PARROT(EntityType.PARROT, 6, 160, 60, Flag.FLIES | Flag.NO_FALL, SoundEvents.PARROT_AMBIENT,
            kin(EntityType.PARROT)),
    PIG(EntityType.PIG, 10, 200, 100, 0, SoundEvents.PIG_AMBIENT,
            kin(EntityType.PIG)),
    RABBIT(EntityType.RABBIT, 6, 60, 200, Flag.NO_FALL, SoundEvents.RABBIT_AMBIENT,
            kin(EntityType.RABBIT), fx(MobEffects.JUMP, 2), fx(MobEffects.MOVEMENT_SPEED, 1)),
    SHEEP(EntityType.SHEEP, 8, 300, 200, 0, SoundEvents.SHEEP_AMBIENT,
            kin(EntityType.SHEEP)),
    SNIFFER(EntityType.SNIFFER, 14, 200, 300, 0, SoundEvents.SNIFFER_IDLE,
            kin(EntityType.SNIFFER)),
    VILLAGER(EntityType.VILLAGER, 20, 1200, 200, 0, SoundEvents.VILLAGER_AMBIENT,
            kin(EntityType.VILLAGER, EntityType.WANDERING_TRADER)),
    WANDERING_TRADER(EntityType.WANDERING_TRADER, 20, 400, 300, 0, SoundEvents.WANDERING_TRADER_AMBIENT,
            kin(EntityType.WANDERING_TRADER, EntityType.VILLAGER, EntityType.TRADER_LLAMA)),
    BEE(EntityType.BEE, 10, 60, 300, Flag.FLIES | Flag.NO_FALL, SoundEvents.BEE_POLLINATE,
            kin(EntityType.BEE)),
    GOAT(EntityType.GOAT, 10, 100, 80, Flag.NO_FALL, SoundEvents.GOAT_AMBIENT,
            kin(EntityType.GOAT), fx(MobEffects.JUMP, 1)),
    LLAMA(EntityType.LLAMA, 22, 40, 100, 0, SoundEvents.LLAMA_AMBIENT,
            kin(EntityType.LLAMA, EntityType.TRADER_LLAMA)),
    TRADER_LLAMA(EntityType.TRADER_LLAMA, 22, 40, 100, 0, SoundEvents.LLAMA_AMBIENT,
            kin(EntityType.TRADER_LLAMA, EntityType.LLAMA, EntityType.WANDERING_TRADER)),
    PANDA(EntityType.PANDA, 20, 80, 200, 0, SoundEvents.PANDA_AMBIENT,
            kin(EntityType.PANDA)),
    POLAR_BEAR(EntityType.POLAR_BEAR, 30, 60, 200, 0, SoundEvents.POLAR_BEAR_AMBIENT,
            kin(EntityType.POLAR_BEAR), fx(MobEffects.DAMAGE_BOOST, 0)),
    WOLF(EntityType.WOLF, 16, 40, 300, 0, SoundEvents.WOLF_AMBIENT,
            kin(EntityType.WOLF), fx(MobEffects.MOVEMENT_SPEED, 0)),
    AXOLOTL(EntityType.AXOLOTL, 14, 300, 200, 0, SoundEvents.AXOLOTL_IDLE_AIR,
            kin(EntityType.AXOLOTL), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    COD(EntityType.COD, 6, 60, 300, 0, SoundEvents.COD_FLOP,
            kin(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    SALMON(EntityType.SALMON, 6, 60, 300, 0, SoundEvents.SALMON_FLOP,
            kin(EntityType.SALMON, EntityType.COD, EntityType.TROPICAL_FISH), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    TROPICAL_FISH(EntityType.TROPICAL_FISH, 6, 200, 300, 0, SoundEvents.TROPICAL_FISH_FLOP,
            kin(EntityType.TROPICAL_FISH, EntityType.COD, EntityType.SALMON), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    PUFFERFISH(EntityType.PUFFERFISH, 6, 100, 300, 0, SoundEvents.PUFFER_FISH_BLOW_UP,
            kin(EntityType.PUFFERFISH), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    SQUID(EntityType.SQUID, 10, 100, 60, 0, SoundEvents.SQUID_AMBIENT,
            kin(EntityType.SQUID, EntityType.GLOW_SQUID), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    GLOW_SQUID(EntityType.GLOW_SQUID, 10, 100, 60, 0, SoundEvents.GLOW_SQUID_AMBIENT,
            kin(EntityType.GLOW_SQUID, EntityType.SQUID), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0), fx(MobEffects.NIGHT_VISION, 0)),
    DOLPHIN(EntityType.DOLPHIN, 10, 200, 400, 0, SoundEvents.DOLPHIN_AMBIENT,
            kin(EntityType.DOLPHIN), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0), fx(MobEffects.NIGHT_VISION, 0)),
    TURTLE(EntityType.TURTLE, 30, 300, 200, 0, SoundEvents.TURTLE_AMBIENT_LAND,
            kin(EntityType.TURTLE), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    TADPOLE(EntityType.TADPOLE, 6, 100, 60, 0, SoundEvents.TADPOLE_FLOP,
            kin(EntityType.TADPOLE, EntityType.FROG), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    GUARDIAN(EntityType.GUARDIAN, 30, 40, 300, 0, SoundEvents.GUARDIAN_AMBIENT,
            kin(EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    DROWNED(EntityType.DROWNED, 20, 40, 200, 0, SoundEvents.DROWNED_AMBIENT,
            kin(EntityType.DROWNED, EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE_VILLAGER), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0)),
    STRIDER(EntityType.STRIDER, 20, 100, 200, 0, SoundEvents.STRIDER_AMBIENT,
            kin(EntityType.STRIDER), fx(MobEffects.FIRE_RESISTANCE, 0)),
    GHAST(EntityType.GHAST, 10, 40, 200, Flag.FLIES | Flag.NO_FALL, SoundEvents.GHAST_AMBIENT,
            kin(EntityType.GHAST), fx(MobEffects.FIRE_RESISTANCE, 0)),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, 16, 60, 100, Flag.NO_FALL, SoundEvents.MAGMA_CUBE_SQUISH,
            kin(EntityType.MAGMA_CUBE), fx(MobEffects.FIRE_RESISTANCE, 0), fx(MobEffects.JUMP, 1)),
    HOGLIN(EntityType.HOGLIN, 40, 60, 100, 0, SoundEvents.HOGLIN_AMBIENT,
            kin(EntityType.HOGLIN), fx(MobEffects.DAMAGE_BOOST, 0)),
    ZOGLIN(EntityType.ZOGLIN, 40, 60, 100, 0, SoundEvents.ZOGLIN_AMBIENT,
            kin(EntityType.ZOGLIN), fx(MobEffects.DAMAGE_BOOST, 0)),
    PIGLIN(EntityType.PIGLIN, 16, 30, 100, 0, SoundEvents.PIGLIN_AMBIENT,
            kin(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)),
    PIGLIN_BRUTE(EntityType.PIGLIN_BRUTE, 50, 60, 400, 0, SoundEvents.PIGLIN_BRUTE_AMBIENT,
            kin(EntityType.PIGLIN_BRUTE, EntityType.PIGLIN), fx(MobEffects.DAMAGE_BOOST, 0)),
    ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, 20, 60, 200, 0, SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT,
            kin(EntityType.ZOMBIFIED_PIGLIN), fx(MobEffects.FIRE_RESISTANCE, 0)),
    CAVE_SPIDER(EntityType.CAVE_SPIDER, 12, 60, 60, Flag.CLIMBS, SoundEvents.SPIDER_AMBIENT,
            kin(EntityType.CAVE_SPIDER, EntityType.SPIDER), fx(MobEffects.NIGHT_VISION, 0)),
    HUSK(EntityType.HUSK, 20, 60, 200, 0, SoundEvents.HUSK_AMBIENT,
            kin(EntityType.HUSK, EntityType.ZOMBIE, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER), fx(MobEffects.DAMAGE_BOOST, 0)),
    STRAY(EntityType.STRAY, 20, 20, 200, 0, SoundEvents.STRAY_AMBIENT,
            kin(EntityType.STRAY, EntityType.SKELETON), fx(MobEffects.MOVEMENT_SPEED, 0)),
    ZOMBIE_VILLAGER(EntityType.ZOMBIE_VILLAGER, 20, 60, 600, 0, SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
            kin(EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED), fx(MobEffects.DAMAGE_BOOST, 0)),
    ENDERMITE(EntityType.ENDERMITE, 8, 30, 100, 0, SoundEvents.ENDERMITE_AMBIENT,
            kin(EntityType.ENDERMITE)),
    SILVERFISH(EntityType.SILVERFISH, 8, 30, 200, 0, SoundEvents.SILVERFISH_AMBIENT,
            kin(EntityType.SILVERFISH)),
    SLIME(EntityType.SLIME, 16, 40, 60, Flag.NO_FALL, SoundEvents.SLIME_SQUISH,
            kin(EntityType.SLIME), fx(MobEffects.JUMP, 1)),
    PHANTOM(EntityType.PHANTOM, 20, 60, 200, Flag.FLIES | Flag.NO_FALL, SoundEvents.PHANTOM_AMBIENT,
            kin(EntityType.PHANTOM)),
    SHULKER(EntityType.SHULKER, 30, 60, 300, 0, SoundEvents.SHULKER_AMBIENT,
            kin(EntityType.SHULKER)),
    WITCH(EntityType.WITCH, 26, 40, 300, 0, SoundEvents.WITCH_AMBIENT,
            kin(EntityType.WITCH, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER)),
    PILLAGER(EntityType.PILLAGER, 24, 20, 100, 0, SoundEvents.PILLAGER_AMBIENT,
            kin(EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.RAVAGER, EntityType.VEX, EntityType.WITCH)),
    VINDICATOR(EntityType.VINDICATOR, 24, 40, 300, 0, SoundEvents.VINDICATOR_AMBIENT,
            kin(EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.EVOKER, EntityType.RAVAGER, EntityType.VEX, EntityType.WITCH), fx(MobEffects.MOVEMENT_SPEED, 0)),
    EVOKER(EntityType.EVOKER, 24, 60, 400, 0, SoundEvents.EVOKER_AMBIENT,
            kin(EntityType.EVOKER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.VEX, EntityType.WITCH)),
    VEX(EntityType.VEX, 14, 40, 100, Flag.FLIES | Flag.NO_FALL, SoundEvents.VEX_AMBIENT,
            kin(EntityType.VEX, EntityType.EVOKER, EntityType.PILLAGER, EntityType.VINDICATOR)),
    RAVAGER(EntityType.RAVAGER, 100, 100, 60, 0, SoundEvents.RAVAGER_AMBIENT,
            kin(EntityType.RAVAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER), fx(MobEffects.DAMAGE_BOOST, 1)),
    WARDEN(EntityType.WARDEN, 200, 100, 300, Flag.NO_FALL, SoundEvents.WARDEN_AMBIENT,
            kin(EntityType.WARDEN), fx(MobEffects.DAMAGE_BOOST, 2), fx(MobEffects.DAMAGE_RESISTANCE, 0)),
    ELDER_GUARDIAN(EntityType.ELDER_GUARDIAN, 80, 40, 400, 0, SoundEvents.ELDER_GUARDIAN_AMBIENT,
            kin(EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN), fx(MobEffects.WATER_BREATHING, 0), fx(MobEffects.DOLPHINS_GRACE, 0), fx(MobEffects.DAMAGE_RESISTANCE, 0)),
    WITHER(EntityType.WITHER, 200, 30, 100, Flag.FLIES | Flag.NO_FALL, SoundEvents.WITHER_AMBIENT,
            kin(EntityType.WITHER, EntityType.WITHER_SKELETON), fx(MobEffects.FIRE_RESISTANCE, 0), fx(MobEffects.DAMAGE_RESISTANCE, 0)),
    ENDER_DRAGON(EntityType.ENDER_DRAGON, 200, 30, 100, Flag.FLIES | Flag.NO_FALL, SoundEvents.ENDER_DRAGON_GROWL,
            kin(EntityType.ENDER_DRAGON, EntityType.ENDERMAN), fx(MobEffects.FIRE_RESISTANCE, 0), fx(MobEffects.DAMAGE_RESISTANCE, 0));

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
