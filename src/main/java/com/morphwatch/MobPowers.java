package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The four powers of every mob added in the "all the mobs" update: G and H (regular) and
 * B and N (super). Each power is put together from a few building blocks below (dash, leap,
 * aura, cone, hit, shoot, helpers...) so they all behave the same way.
 * "s" is the strength: 1.0 normal, x2 when G is charged, more with a better watch or golden form.
 */
public final class MobPowers {
    private MobPowers() {}

    @FunctionalInterface
    interface Power {
        boolean use(ServerPlayer p, float s, int targetId);
    }

    record Fx(MobEffect effect, int ticks, int amp) {}

    private record Kit(String[] names, Power[] powers) {}

    enum Dmg { MELEE, SONIC, MAGIC, FREEZE }

    enum Shot { ARROW, FIRE_ARROW, SLOW_ARROW, PIERCING_ARROW, BIG_FIREBALL, WITHER_SKULL, BLUE_SKULL, DRAGON_FIREBALL,
        TRIDENT, BAD_POTION }

    private static final Map<MorphForm, Kit> KITS = new EnumMap<>(MorphForm.class);

    public static boolean has(MorphForm form) {
        return KITS.containsKey(form);
    }

    /** slot: 1 = G, 2 = H, 3 = B, 4 = N. */
    public static String name(MorphForm form, int slot) {
        Kit kit = KITS.get(form);
        return kit == null ? "" : kit.names[slot - 1];
    }

    public static boolean use(ServerPlayer p, MorphForm form, int slot, float s, int targetId) {
        Kit kit = KITS.get(form);
        if (kit == null) return false;
        if (slot <= 2) WatchActions.tell(p, kit.names[slot - 1], ChatFormatting.AQUA);
        return kit.powers[slot - 1].use(p, s, targetId);
    }

    private static void kit(MorphForm form, String g, Power pg, String h, Power ph, String b, Power pb, String n, Power pn) {
        KITS.put(form, new Kit(new String[]{g, h, b, n}, new Power[]{pg, ph, pb, pn}));
    }

    // ============================================================================ the mobs

    static {
        ParticleOptions sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
        ParticleOptions mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
        ParticleOptions stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        ParticleOptions ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        ParticleOptions web = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
        ParticleOptions bamboo = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BAMBOO));
        ParticleOptions wool = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState());

        Fx slow = fx(MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
        Fx weak = fx(MobEffects.WEAKNESS, 120, 1);
        Fx blind = fx(MobEffects.BLINDNESS, 100, 0);
        Fx poison = fx(MobEffects.POISON, 100, 0);
        Fx glowing = fx(MobEffects.GLOWING, 200, 0);
        Fx confuse = fx(MobEffects.CONFUSION, 120, 0);
        Fx hunger = fx(MobEffects.HUNGER, 200, 1);
        Fx speed2 = fx(MobEffects.MOVEMENT_SPEED, 200, 1);
        Fx speed3 = fx(MobEffects.MOVEMENT_SPEED, 200, 2);
        Fx strength = fx(MobEffects.DAMAGE_BOOST, 300, 1);
        Fx resist = fx(MobEffects.DAMAGE_RESISTANCE, 300, 1);
        Fx resist3 = fx(MobEffects.DAMAGE_RESISTANCE, 200, 2);
        Fx absorb = fx(MobEffects.ABSORPTION, 400, 1);
        Fx regen = fx(MobEffects.REGENERATION, 160, 1);
        Fx nightVision = fx(MobEffects.NIGHT_VISION, 600, 0);
        Fx waterBreath = fx(MobEffects.WATER_BREATHING, 600, 0);
        Fx grace = fx(MobEffects.DOLPHINS_GRACE, 200, 1);
        Fx fireRes = fx(MobEffects.FIRE_RESISTANCE, 400, 0);

        SoundEvent sweep = SoundEvents.PLAYER_ATTACK_SWEEP;
        SoundEvent strong = SoundEvents.PLAYER_ATTACK_STRONG;
        SoundEvent knock = SoundEvents.PLAYER_ATTACK_KNOCKBACK;
        SoundEvent eat = SoundEvents.GENERIC_EAT;
        SoundEvent splash = SoundEvents.GENERIC_SPLASH;

        Power gallop = self(ParticleTypes.CLOUD, SoundEvents.HORSE_GALLOP, speed3);
        Power backKick = aura(3, 5, 1.8, 0.3, 0, ParticleTypes.CLOUD, knock);
        Power megaKick = hit(4, 12, 3.0, 0.5, 0, Dmg.MELEE, ParticleTypes.CRIT, strong);
        Power bubbles = friends(10, ParticleTypes.BUBBLE_POP, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, waterBreath, grace);
        Power fishFrenzy = cone(10, 0.7, 5, 1.0, 0, ParticleTypes.SPLASH, splash);
        Power swimRocket = dash(2.2, 0.2, 0, false, ParticleTypes.BUBBLE, SoundEvents.DOLPHIN_JUMP);
        Power jet = dash(1.8, 0.2, 0, false, ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT);
        Power hide = cloak(300, true, ParticleTypes.POOF, SoundEvents.ENDERMAN_TELEPORT);
        Power spikes = spikes(200, ParticleTypes.CRIT, SoundEvents.PUFFER_FISH_STING);
        Power spitShot = hit(16, 4, 0.4, 0, 0, Dmg.MELEE, ParticleTypes.SPIT, SoundEvents.LLAMA_SPIT, slow);
        Power spitStorm = cone(14, 0.75, 5, 0.6, 0, ParticleTypes.SPIT, SoundEvents.LLAMA_SPIT, slow);

        // ---------------------------------------------------------------- animals and friendly mobs
        kit(MorphForm.ALLAY,
                "Item Magnet", magnet(12),
                "Happy Dance", heal(6, false, ParticleTypes.NOTE, SoundEvents.AMETHYST_BLOCK_CHIME, regen),
                "Music Storm", aura(8, 6, 1.0, 0.2, 0, ParticleTypes.NOTE, SoundEvents.AMETHYST_BLOCK_CHIME),
                "Vanish", hide);
        kit(MorphForm.CAMEL,
                "Camel Dash", dash(2.0, 0.4, 0, true, ParticleTypes.CLOUD, SoundEvents.CAMEL_DASH),
                "Sand Kick", cone(6, 0.6, 1, 0.8, 0, sand, SoundEvents.SAND_BREAK, blind),
                "Stampede", dash(2.4, 0.3, 8, true, sand, SoundEvents.CAMEL_DASH),
                "Desert Rest", heal(12, false, ParticleTypes.HEART, SoundEvents.CAMEL_SIT, regen));
        kit(MorphForm.COW,
                "Milk Heal", heal(4, true, ParticleTypes.HEART, SoundEvents.COW_MILK),
                "Moo", aura(8, 0, 0.5, 0, 0, ParticleTypes.NOTE, SoundEvents.COW_HURT, slow),
                "Cow Charge", dash(2.2, 0.3, 7, true, ParticleTypes.CLOUD, knock),
                "Cow Shield", steady(300, ParticleTypes.ENCHANTED_HIT, SoundEvents.ARMOR_EQUIP_LEATHER, resist, absorb));
        kit(MorphForm.DONKEY,
                "Back Kick", backKick,
                "Pack Mule", self(ParticleTypes.HAPPY_VILLAGER, SoundEvents.DONKEY_CHEST, speed2, fx(MobEffects.DIG_SPEED, 600, 1)),
                "Mega Kick", megaKick,
                "Bray", aura(10, 0, 1.5, 0.2, 0, ParticleTypes.NOTE, SoundEvents.DONKEY_ANGRY, slow));
        kit(MorphForm.MULE,
                "Back Kick", backKick,
                "Stubborn", steady(300, ParticleTypes.ENCHANTED_HIT, SoundEvents.MULE_ANGRY, resist),
                "Mega Kick", megaKick,
                "Snack Bag", give(2, SoundEvents.ITEM_PICKUP, Items.BREAD, Items.APPLE, Items.CARROT, Items.COOKIE, Items.BAKED_POTATO));
        kit(MorphForm.HORSE,
                "Gallop", gallop,
                "Buck", aura(3, 4, 2.0, 0.4, 0, ParticleTypes.CLOUD, SoundEvents.HORSE_ANGRY),
                "Horse Charge", dash(2.4, 0.3, 8, true, ParticleTypes.CLOUD, SoundEvents.HORSE_GALLOP),
                "Mega Leap", leap(1.4, 1.2, ParticleTypes.CLOUD, SoundEvents.HORSE_JUMP));
        kit(MorphForm.SKELETON_HORSE,
                "Gallop", gallop,
                "Bone Kick", aura(3, 5, 2.0, 0.4, 0, ParticleTypes.CRIT, SoundEvents.SKELETON_HORSE_HURT),
                "Lightning Strike", lightning(24),
                "Ghost Ride", cloak(300, true, ParticleTypes.SOUL, SoundEvents.SKELETON_HORSE_AMBIENT, speed3));
        kit(MorphForm.ZOMBIE_HORSE,
                "Gallop", gallop,
                "Rotten Kick", aura(3, 5, 2.0, 0.4, 0, ParticleTypes.CRIT, SoundEvents.ZOMBIE_HORSE_HURT, hunger),
                "Undead Charge", dash(2.4, 0.3, 8, true, ParticleTypes.SMOKE, SoundEvents.ZOMBIE_HORSE_AMBIENT),
                "Undead Strength", self(ParticleTypes.ANGRY_VILLAGER, SoundEvents.ZOMBIE_HORSE_AMBIENT, strength, resist));
        kit(MorphForm.FOX,
                "Fox Pounce", dash(1.4, 0.6, 5, true, ParticleTypes.CRIT, SoundEvents.FOX_BITE),
                "Sneaky", hide,
                "Fox Ambush", all(blink(16), hit(5, 8, 0.5, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.FOX_BITE)),
                "Night Hunter", self(ParticleTypes.CRIT, SoundEvents.FOX_SCREECH, nightVision, strength, speed2));
        kit(MorphForm.FROG,
                "Tongue Grab", pull(10, ParticleTypes.DRIPPING_LAVA, SoundEvents.FROG_TONGUE),
                "Big Hop", leap(1.0, 0.5, ParticleTypes.CLOUD, SoundEvents.FROG_LONG_JUMP),
                "Gulp", gulp(),
                "Lily Pad Leap", leap(1.5, 1.5, ParticleTypes.SPLASH, SoundEvents.FROG_LONG_JUMP));
        kit(MorphForm.MOOSHROOM,
                "Mushroom Stew", snack(8, 6, ParticleTypes.HEART, SoundEvents.MOOSHROOM_MILK),
                "Spore Cloud", aura(5, 1, 0.3, 0, 0, ParticleTypes.MYCELIUM, SoundEvents.FUNGUS_PLACE, poison),
                "Mushroom Stampede", dash(2.2, 0.3, 8, true, ParticleTypes.MYCELIUM, knock),
                "Fungus Shield", self(ParticleTypes.MYCELIUM, SoundEvents.FUNGUS_PLACE, regen, resist));
        kit(MorphForm.OCELOT,
                "Jungle Dash", dash(1.8, 0.2, 0, true, ParticleTypes.HAPPY_VILLAGER, SoundEvents.OCELOT_AMBIENT),
                "Hiss", (p, s, t) -> SuperPowers.superN(p, MorphForm.CAT, s, t),
                "Pounce Strike", dash(1.6, 0.6, 9, true, ParticleTypes.SWEEP_ATTACK, sweep),
                "Jungle Stealth", cloak(300, true, ParticleTypes.HAPPY_VILLAGER, SoundEvents.OCELOT_AMBIENT, speed2));
        kit(MorphForm.PARROT,
                "Mimic", aura(10, 0, 1.4, 0.2, 0, ParticleTypes.NOTE, SoundEvents.PARROT_IMITATE_CREEPER, slow),
                "Flutter", leap(0.9, 0.5, ParticleTypes.CLOUD, SoundEvents.PARROT_FLY),
                "Dive Bomb", dash(2.2, -0.2, 8, false, ParticleTypes.CRIT, SoundEvents.PHANTOM_SWOOP),
                "Feather Storm", cone(12, 0.6, 3, 2.2, 0, ParticleTypes.CLOUD, SoundEvents.PARROT_FLY));
        kit(MorphForm.PIG,
                "Mud Roll", self(mud, SoundEvents.MUD_STEP, resist),
                "Oink Charge", dash(2.0, 0.3, 5, true, mud, SoundEvents.PIG_HURT),
                "Pig Rocket", leap(0.8, 2.4, ParticleTypes.FIREWORK, SoundEvents.FIREWORK_ROCKET_LAUNCH),
                "Truffle Snack", snack(6, 6, ParticleTypes.HEART, eat));
        kit(MorphForm.RABBIT,
                "Super Hop", leap(1.2, 0.6, ParticleTypes.CLOUD, SoundEvents.RABBIT_JUMP),
                "Carrot Snack", snack(4, 4, ParticleTypes.HEART, eat),
                "Killer Bunny Bite", hit(4, 10, 0.6, 0, 0, Dmg.MELEE, ParticleTypes.DAMAGE_INDICATOR, SoundEvents.RABBIT_ATTACK),
                "Burrow Escape", cloak(200, true, ParticleTypes.POOF, SoundEvents.RABBIT_JUMP, speed3));
        kit(MorphForm.SHEEP,
                "Wool Shield", self(wool, SoundEvents.WOOL_PLACE, absorb),
                "Grass Snack", snack(4, 3, ParticleTypes.HAPPY_VILLAGER, SoundEvents.SHEEP_AMBIENT),
                "Rainbow Wool Blast", cone(12, 0.7, 6, 1.6, 0, ParticleTypes.NOTE, SoundEvents.WOOL_BREAK),
                "Fluffy Bounce", leap(1.3, 0.3, wool, SoundEvents.WOOL_PLACE));
        kit(MorphForm.SNIFFER,
                "Sniff", glow(24, SoundEvents.SNIFFER_SNIFFING),
                "Dig", give(1, SoundEvents.SNIFFER_DIGGING, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS,
                        Items.MELON_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD),
                "Ancient Stomp", aura(5, 7, 1.5, 0.5, 0, stone, SoundEvents.GENERIC_EXPLODE),
                "Dig Up Treasure", give(1, SoundEvents.SNIFFER_DROP_SEED, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
                        Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD, Items.ARCHER_POTTERY_SHERD, Items.PRIZE_POTTERY_SHERD,
                        Items.AMETHYST_SHARD, Items.LAPIS_LAZULI, Items.DIAMOND));
        kit(MorphForm.VILLAGER,
                "Bargain", self(ParticleTypes.HAPPY_VILLAGER, SoundEvents.VILLAGER_YES, fx(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0)),
                "Bread Snack", snack(4, 5, ParticleTypes.HAPPY_VILLAGER, eat),
                "Call Iron Golem", helpers(EntityType.IRON_GOLEM, 1, SoundEvents.IRON_GOLEM_REPAIR),
                "Panic Run", self(ParticleTypes.SPLASH, SoundEvents.VILLAGER_HURT, speed3, fx(MobEffects.JUMP, 200, 1)));
        kit(MorphForm.WANDERING_TRADER,
                "Invisibility Potion", cloak(300, true, ParticleTypes.WITCH, SoundEvents.WANDERING_TRADER_DRINK_POTION),
                "Milk Bucket", heal(2, true, ParticleTypes.HEART, SoundEvents.WANDERING_TRADER_DRINK_MILK),
                "Call 2 Llamas", helpers(EntityType.TRADER_LLAMA, 2, SoundEvents.LLAMA_AMBIENT),
                "Wander Teleport", randomTeleport(20));
        kit(MorphForm.BEE,
                "Sting", hit(4, 3, 0.3, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.BEE_STING, poison),
                "Pollinate", friends(8, ParticleTypes.FALLING_NECTAR, SoundEvents.BEE_POLLINATE, regen),
                "Bee Swarm", helpers(EntityType.BEE, 3, SoundEvents.BEE_LOOP_AGGRESSIVE),
                "Honey Shield", self(ParticleTypes.FALLING_HONEY, SoundEvents.HONEY_BLOCK_PLACE, regen, absorb));
        kit(MorphForm.GOAT,
                "Ram", dash(1.8, 0.2, 6, true, ParticleTypes.CLOUD, SoundEvents.GOAT_RAM_IMPACT),
                "Goat Leap", leap(1.2, 0.8, ParticleTypes.CLOUD, SoundEvents.GOAT_LONG_JUMP),
                "Mega Ram", dash(2.6, 0.2, 10, true, ParticleTypes.EXPLOSION, SoundEvents.GOAT_RAM_IMPACT),
                "Goat Horn", all(aura(12, 0, 1.2, 0, 0, ParticleTypes.NOTE, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), slow, weak),
                        self(ParticleTypes.ANGRY_VILLAGER, SoundEvents.GOAT_SCREAMING_AMBIENT, strength)));
        kit(MorphForm.LLAMA,
                "Spit", spitShot,
                "Kick", backKick,
                "Spit Storm", spitStorm,
                "Llama Drama", aura(10, 0, 0.6, 0, 0, ParticleTypes.SPIT, SoundEvents.LLAMA_ANGRY, slow, weak));
        kit(MorphForm.TRADER_LLAMA,
                "Spit", spitShot,
                "Kick", backKick,
                "Spit Storm", spitStorm,
                "Caravan", helpers(EntityType.TRADER_LLAMA, 2, SoundEvents.LLAMA_AMBIENT));
        kit(MorphForm.PANDA,
                "Panda Roll", dash(1.6, 0.2, 4, true, ParticleTypes.CLOUD, SoundEvents.PANDA_PRE_SNEEZE),
                "Bamboo Snack", snack(6, 4, bamboo, SoundEvents.PANDA_EAT),
                "Panda Slam", aura(4, 8, 1.2, 0.6, 0, ParticleTypes.EXPLOSION, SoundEvents.PANDA_BITE),
                "Mega Sneeze", aura(8, 2, 2.6, 0.4, 0, ParticleTypes.ITEM_SLIME, SoundEvents.PANDA_SNEEZE));
        kit(MorphForm.POLAR_BEAR,
                "Bear Swipe", hit(4, 7, 1.0, 0, 0, Dmg.MELEE, ParticleTypes.SWEEP_ATTACK, sweep),
                "Roar", aura(10, 0, 1.4, 0.2, 0, ParticleTypes.CLOUD, SoundEvents.POLAR_BEAR_WARNING, slow),
                "Ice Slam", aura(5, 8, 1.2, 0.4, 0, ice, SoundEvents.GLASS_BREAK, fx(MobEffects.MOVEMENT_SLOWDOWN, 120, 3)),
                "Mama Bear Rage", self(ParticleTypes.ANGRY_VILLAGER, SoundEvents.POLAR_BEAR_WARNING, strength, speed2));
        kit(MorphForm.WOLF,
                "Bite", hit(3, 5, 0.4, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.WOLF_GROWL),
                "Howl", self(ParticleTypes.NOTE, SoundEvents.WOLF_HOWL, strength),
                "Wolf Pack", helpers(EntityType.WOLF, 2, SoundEvents.WOLF_HOWL),
                "Hunt", all(self(ParticleTypes.CRIT, SoundEvents.WOLF_GROWL, speed2), glow(24, SoundEvents.WOLF_HOWL)));

        // ---------------------------------------------------------------- water mobs
        kit(MorphForm.AXOLOTL,
                "Play Dead", cloak(200, false, ParticleTypes.HEART, SoundEvents.AXOLOTL_HURT, regen),
                "Water Splash", all(heal(6, false, ParticleTypes.SPLASH, SoundEvents.AXOLOTL_SPLASH), extinguish()),
                "Tidal Bite", dash(2.0, 0.2, 8, false, ParticleTypes.SPLASH, SoundEvents.AXOLOTL_ATTACK),
                "Bubble Shield", self(ParticleTypes.BUBBLE_POP, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, absorb, waterBreath));
        kit(MorphForm.COD,
                "Flop Jump", leap(0.8, 0.6, ParticleTypes.SPLASH, SoundEvents.COD_FLOP),
                "Bubbles", bubbles,
                "Fish Frenzy", fishFrenzy,
                "Swim Rocket", swimRocket);
        kit(MorphForm.SALMON,
                "Upstream Leap", leap(1.2, 0.8, ParticleTypes.SPLASH, SoundEvents.SALMON_FLOP),
                "Bubbles", bubbles,
                "Fish Frenzy", fishFrenzy,
                "Swim Rocket", swimRocket);
        kit(MorphForm.TROPICAL_FISH,
                "Color Flash", aura(8, 0, 0.3, 0, 0, ParticleTypes.WAX_ON, SoundEvents.TROPICAL_FISH_FLOP, confuse, glowing),
                "Bubbles", bubbles,
                "Rainbow Blast", cone(12, 0.7, 6, 1.4, 0, ParticleTypes.NOTE, splash, confuse),
                "Coral Hide", hide);
        kit(MorphForm.PUFFERFISH,
                "Puff Up", aura(3, 2, 0.6, 0, 0, ParticleTypes.CRIT, SoundEvents.PUFFER_FISH_BLOW_UP, poison),
                "Spikes", spikes,
                "Toxic Burst", aura(8, 3, 1.0, 0, 0, ParticleTypes.ITEM_SLIME, SoundEvents.PUFFER_FISH_BLOW_UP,
                        fx(MobEffects.POISON, 160, 1), confuse),
                "Spike Shield", all(spikes, self(ParticleTypes.CRIT, SoundEvents.PUFFER_FISH_BLOW_UP, resist)));
        kit(MorphForm.SQUID,
                "Ink Cloud", aura(6, 0, 0, 0, 0, ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT, blind),
                "Jet", jet,
                "Ink Bomb", aura(12, 4, 0.8, 0, 0, ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT, blind, slow),
                "Tentacle Grab", pull(8, ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT));
        kit(MorphForm.GLOW_SQUID,
                "Glow Ink", aura(8, 0, 0, 0, 0, ParticleTypes.GLOW_SQUID_INK, SoundEvents.GLOW_SQUID_SQUIRT, glowing, blind),
                "Jet", dash(1.8, 0.2, 0, false, ParticleTypes.GLOW_SQUID_INK, SoundEvents.GLOW_SQUID_SQUIRT),
                "Glow Burst", aura(14, 4, 0.8, 0, 0, ParticleTypes.GLOW, SoundEvents.GLOW_SQUID_SQUIRT, glowing, blind, slow),
                "Light Up", friends(16, ParticleTypes.GLOW, SoundEvents.AMETHYST_BLOCK_CHIME, nightVision));
        kit(MorphForm.DOLPHIN,
                "Dolphin's Grace", self(ParticleTypes.DOLPHIN, SoundEvents.DOLPHIN_PLAY, fx(MobEffects.DOLPHINS_GRACE, 300, 2), speed2),
                "Treasure Sense", all(friends(16, ParticleTypes.DOLPHIN, SoundEvents.DOLPHIN_PLAY, nightVision, waterBreath),
                        glow(32, SoundEvents.DOLPHIN_AMBIENT_WATER)),
                "Dolphin Ram", dash(2.4, 0.2, 8, false, ParticleTypes.DOLPHIN, SoundEvents.DOLPHIN_ATTACK),
                "Big Splash", aura(6, 3, 1.0, 1.0, 0, ParticleTypes.SPLASH, SoundEvents.DOLPHIN_JUMP));
        kit(MorphForm.TURTLE,
                "Shell Shield", self(ParticleTypes.ENCHANTED_HIT, SoundEvents.TURTLE_SHAMBLE, resist, fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 0)),
                "Swim Boost", self(ParticleTypes.BUBBLE, SoundEvents.TURTLE_SWIM, grace, speed2),
                "Shell Spin", aura(5, 7, 1.8, 0.3, 0, ParticleTypes.SWEEP_ATTACK, sweep),
                "Turtle Master", steady(300, ParticleTypes.ENCHANTED_HIT, SoundEvents.TURTLE_SHAMBLE,
                        fx(MobEffects.DAMAGE_RESISTANCE, 300, 3), fx(MobEffects.MOVEMENT_SLOWDOWN, 300, 2), absorb));
        kit(MorphForm.TADPOLE,
                "Wiggle", self(ParticleTypes.BUBBLE, SoundEvents.TADPOLE_FLOP, grace),
                "Tiny Hop", leap(0.7, 0.3, ParticleTypes.SPLASH, SoundEvents.TADPOLE_FLOP),
                "Bubble Burst", aura(6, 3, 1.4, 0.3, 0, ParticleTypes.BUBBLE_POP, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE),
                "Hide", hide);
        kit(MorphForm.GUARDIAN,
                "Laser", hit(16, 6, 0.3, 0, 0, Dmg.MAGIC, ParticleTypes.GLOW, SoundEvents.GUARDIAN_ATTACK),
                "Spikes", spikes,
                "Laser Charge", hit(24, 12, 0.6, 0, 0, Dmg.MAGIC, ParticleTypes.ELECTRIC_SPARK, SoundEvents.GUARDIAN_ATTACK),
                "Swim Boost", self(ParticleTypes.BUBBLE, SoundEvents.GUARDIAN_FLOP, grace, speed2));
        kit(MorphForm.DROWNED,
                "Trident Throw", shoot(Shot.TRIDENT, 1, 0, SoundEvents.TRIDENT_THROW),
                "Swim Speed", self(ParticleTypes.BUBBLE, SoundEvents.DROWNED_SWIM, grace, speed2),
                "Thunder Trident", lightning(32),
                "Drowned Pull", pull(12, ParticleTypes.BUBBLE_POP, SoundEvents.DROWNED_SHOOT));

        // ---------------------------------------------------------------- nether mobs
        kit(MorphForm.STRIDER,
                "Hot Feet", aura(3, 2, 0.4, 0, 4, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE),
                "Shiver", aura(8, 0, 0.3, 0, 0, ParticleTypes.SNOWFLAKE, SoundEvents.STRIDER_AMBIENT, slow),
                "Lava Splash", cone(10, 0.7, 5, 0.8, 6, ParticleTypes.LAVA, SoundEvents.LAVA_POP),
                "Heat Shield", self(ParticleTypes.FLAME, SoundEvents.STRIDER_HAPPY, fireRes, resist, regen));
        kit(MorphForm.GHAST,
                "Fireball", shoot(Shot.BIG_FIREBALL, 1, 0, SoundEvents.GHAST_SHOOT),
                "Cry", aura(10, 0, 1.6, 0.2, 0, ParticleTypes.SMOKE, SoundEvents.GHAST_SCREAM, slow),
                "Fireball Barrage", shoot(Shot.BIG_FIREBALL, 3, 12, SoundEvents.GHAST_SHOOT),
                "Ghast Float", self(ParticleTypes.CLOUD, SoundEvents.GHAST_AMBIENT,
                        fx(MobEffects.LEVITATION, 40, 1), fx(MobEffects.SLOW_FALLING, 300, 0)));
        kit(MorphForm.MAGMA_CUBE,
                "Big Bounce", leap(1.2, 0.4, ParticleTypes.FLAME, SoundEvents.MAGMA_CUBE_JUMP),
                "Fire Splash", aura(4, 3, 0.6, 0, 5, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE),
                "Magma Slam", all(aura(6, 8, 1.6, 0.5, 6, ParticleTypes.LAVA, SoundEvents.MAGMA_CUBE_SQUISH),
                        leap(0.7, 0, ParticleTypes.FLAME, SoundEvents.MAGMA_CUBE_JUMP)),
                "Split", helpers(EntityType.MAGMA_CUBE, 3, SoundEvents.MAGMA_CUBE_SQUISH_SMALL));
        kit(MorphForm.HOGLIN,
                "Tusk Toss", hit(4, 6, 0.4, 1.2, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.HOGLIN_ATTACK),
                "Charge", dash(2.0, 0.2, 6, true, ParticleTypes.CLOUD, SoundEvents.HOGLIN_ANGRY),
                "Stampede", dash(2.6, 0.2, 10, true, ParticleTypes.EXPLOSION, SoundEvents.HOGLIN_ANGRY),
                "Hoglin Roar", aura(10, 0, 1.6, 0.3, 0, ParticleTypes.ANGRY_VILLAGER, SoundEvents.HOGLIN_ANGRY, weak));
        kit(MorphForm.ZOGLIN,
                "Tusk Toss", hit(4, 6, 0.4, 1.2, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.ZOGLIN_ATTACK),
                "Charge", dash(2.0, 0.2, 6, true, ParticleTypes.CLOUD, SoundEvents.ZOGLIN_ANGRY),
                "Zoglin Rampage", all(dash(2.6, 0.2, 10, true, ParticleTypes.EXPLOSION, SoundEvents.ZOGLIN_ANGRY),
                        aura(4, 4, 1.2, 0.3, 0, ParticleTypes.CRIT, knock)),
                "Undead Toughness", self(ParticleTypes.ENCHANTED_HIT, SoundEvents.ZOGLIN_ANGRY, resist, regen));
        kit(MorphForm.PIGLIN,
                "Crossbow Shot", shoot(Shot.PIERCING_ARROW, 1, 0, SoundEvents.CROSSBOW_SHOOT),
                "Barter", barter(),
                "Golden Sword Spin", aura(5, 8, 1.0, 0.2, 0, ParticleTypes.SWEEP_ATTACK, sweep),
                "Piglin Army", helpers(EntityType.PIGLIN, 2, SoundEvents.PIGLIN_ANGRY));
        kit(MorphForm.PIGLIN_BRUTE,
                "Axe Chop", hit(4, 9, 0.6, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, strong),
                "Brute Rage", self(ParticleTypes.ANGRY_VILLAGER, SoundEvents.PIGLIN_BRUTE_ANGRY, strength, speed2),
                "Axe Spin", aura(5, 10, 1.2, 0.2, 0, ParticleTypes.SWEEP_ATTACK, sweep),
                "Unstoppable", steady(300, ParticleTypes.ENCHANTED_HIT, SoundEvents.PIGLIN_BRUTE_ANGRY, resist3, strength));
        kit(MorphForm.ZOMBIFIED_PIGLIN,
                "Gold Sword Slash", hit(4, 6, 0.5, 0, 0, Dmg.MELEE, ParticleTypes.SWEEP_ATTACK, sweep),
                "Angry Call", angryCall(32),
                "Horde", helpers(EntityType.ZOMBIFIED_PIGLIN, 2, SoundEvents.ZOMBIFIED_PIGLIN_ANGRY),
                "Fire Shield", all(self(ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE, fireRes, resist),
                        aura(4, 2, 0.6, 0, 5, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE)));

        // ---------------------------------------------------------------- monsters
        kit(MorphForm.CAVE_SPIDER,
                "Poison Bite", hit(4, 3, 0.3, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.SPIDER_HURT, fx(MobEffects.POISON, 140, 1)),
                "Web", (p, s, t) -> Abilities.shootWeb(p, s),
                "Toxic Web Net", webNet(web),
                "Tiny Sneak", hide);
        kit(MorphForm.HUSK,
                "Hunger Bite", hit(4, 5, 0.3, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.HUSK_HURT, hunger),
                "Sand Cloud", aura(6, 0, 0.3, 0, 0, sand, SoundEvents.SAND_BREAK, blind, slow),
                "Sandstorm", cone(14, 0.6, 5, 0.8, 0, sand, SoundEvents.SAND_BREAK, blind, slow, hunger),
                "Desert Toughness", self(sand, SoundEvents.HUSK_AMBIENT, resist, fireRes));
        kit(MorphForm.STRAY,
                "Frost Arrow", shoot(Shot.SLOW_ARROW, 1, 0, SoundEvents.SKELETON_SHOOT),
                "Ice Walk", all(iceWalk(4), self(ParticleTypes.SNOWFLAKE, SoundEvents.GLASS_PLACE, speed2)),
                "Frost Arrow Volley", shoot(Shot.SLOW_ARROW, 7, 6, SoundEvents.SKELETON_SHOOT),
                "Ice Armor", self(ice, SoundEvents.GLASS_PLACE, resist, absorb));
        kit(MorphForm.ZOMBIE_VILLAGER,
                "Zombie Bite", hit(4, 5, 0.3, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.ZOMBIE_VILLAGER_HURT, hunger),
                "Golden Apple", heal(8, false, ParticleTypes.HEART, eat, absorb, regen),
                "Zombie Mob", helpers(EntityType.ZOMBIE, 3, SoundEvents.ZOMBIE_AMBIENT),
                "Villager Shield", self(ParticleTypes.HAPPY_VILLAGER, SoundEvents.ZOMBIE_VILLAGER_CURE, resist, absorb));
        kit(MorphForm.ENDERMITE,
                "Nibble", hit(3, 3, 0.2, 0, 0, Dmg.MELEE, ParticleTypes.PORTAL, SoundEvents.ENDERMITE_HURT),
                "Tiny Teleport", randomTeleport(8),
                "Mite Swarm", helpers(EntityType.ENDERMITE, 4, SoundEvents.ENDERMITE_AMBIENT),
                "Hide", hide);
        kit(MorphForm.SILVERFISH,
                "Nibble", hit(3, 3, 0.2, 0, 0, Dmg.MELEE, stone, SoundEvents.SILVERFISH_HURT),
                "Stone Hide", cloak(300, true, stone, SoundEvents.STONE_PLACE),
                "Silverfish Swarm", helpers(EntityType.SILVERFISH, 4, SoundEvents.SILVERFISH_AMBIENT),
                "Stone Skin", steady(300, stone, SoundEvents.STONE_PLACE, resist3));
        kit(MorphForm.SLIME,
                "Bounce", leap(1.0, 0.4, ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_JUMP),
                "Slime Slap", hit(4, 3, 2.2, 0.3, 0, Dmg.MELEE, ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_ATTACK),
                "Mega Bounce Slam", all(aura(6, 8, 1.8, 0.6, 0, ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_SQUISH),
                        leap(1.0, 0, ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_JUMP)),
                "Split", helpers(EntityType.SLIME, 3, SoundEvents.SLIME_SQUISH_SMALL));
        kit(MorphForm.PHANTOM,
                "Swoop", dash(2.0, 0, 6, false, ParticleTypes.CRIT, SoundEvents.PHANTOM_SWOOP),
                "Night Fly", self(ParticleTypes.CLOUD, SoundEvents.PHANTOM_FLAP, speed2, nightVision),
                "Dive Bomb", dash(2.6, -0.2, 10, false, ParticleTypes.EXPLOSION, SoundEvents.PHANTOM_SWOOP),
                "Insomnia Curse", aura(16, 0, 0, 0, 0, ParticleTypes.SMOKE, SoundEvents.PHANTOM_BITE, slow, weak));
        kit(MorphForm.SHULKER,
                "Levitation Bullet", shulkerBullets(1, 24),
                "Shell Close", steady(200, ParticleTypes.ENCHANTED_HIT, SoundEvents.SHULKER_CLOSE,
                        fx(MobEffects.DAMAGE_RESISTANCE, 200, 3), fx(MobEffects.MOVEMENT_SLOWDOWN, 200, 2)),
                "Bullet Barrage", shulkerBullets(5, 24),
                "Shulker Teleport", randomTeleport(16));
        kit(MorphForm.WITCH,
                "Splash Potion", shoot(Shot.BAD_POTION, 1, 0, SoundEvents.SPLASH_POTION_THROW),
                "Drink Potion", heal(6, false, ParticleTypes.WITCH, SoundEvents.WITCH_DRINK, fireRes),
                "Potion Barrage", shoot(Shot.BAD_POTION, 5, 12, SoundEvents.SPLASH_POTION_THROW),
                "Witch Brew", self(ParticleTypes.WITCH, SoundEvents.WITCH_DRINK, regen, speed2, strength));
        kit(MorphForm.PILLAGER,
                "Crossbow Shot", shoot(Shot.PIERCING_ARROW, 1, 0, SoundEvents.CROSSBOW_SHOOT),
                "Firework Shot", hit(24, 7, 1.0, 0.2, 0, Dmg.MELEE, ParticleTypes.FIREWORK, SoundEvents.FIREWORK_ROCKET_BLAST),
                "Crossbow Volley", shoot(Shot.PIERCING_ARROW, 5, 8, SoundEvents.CROSSBOW_SHOOT),
                "Patrol", helpers(EntityType.PILLAGER, 2, SoundEvents.PILLAGER_CELEBRATE));
        kit(MorphForm.VINDICATOR,
                "Axe Swing", hit(4, 8, 0.6, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, strong),
                "Johnny Rage", self(ParticleTypes.ANGRY_VILLAGER, SoundEvents.VINDICATOR_CELEBRATE, strength, speed2),
                "Axe Whirl", aura(5, 9, 1.2, 0.2, 0, ParticleTypes.SWEEP_ATTACK, sweep),
                "Raid Charge", dash(2.4, 0.2, 9, true, ParticleTypes.CRIT, SoundEvents.VINDICATOR_AMBIENT));
        kit(MorphForm.EVOKER,
                "Fang Line", fangs(false),
                "Vex Call", helpers(EntityType.VEX, 2, SoundEvents.EVOKER_PREPARE_SUMMON),
                "Fang Circle", fangs(true),
                "Totem Shield", self(ParticleTypes.TOTEM_OF_UNDYING, SoundEvents.TOTEM_USE, fx(MobEffects.ABSORPTION, 400, 3), regen));
        kit(MorphForm.VEX,
                "Phase Charge", dash(2.0, 0, 6, false, ParticleTypes.CRIT, SoundEvents.VEX_CHARGE),
                "Ghost Fly", self(ParticleTypes.CLOUD, SoundEvents.VEX_AMBIENT, speed3),
                "Vex Swarm", helpers(EntityType.VEX, 4, SoundEvents.EVOKER_PREPARE_SUMMON),
                "Ghost", hide);
        kit(MorphForm.RAVAGER,
                "Roar", aura(6, 4, 2.0, 0.3, 0, ParticleTypes.CLOUD, SoundEvents.RAVAGER_ROAR, slow),
                "Bite", hit(4, 9, 0.8, 0, 0, Dmg.MELEE, ParticleTypes.CRIT, SoundEvents.RAVAGER_ATTACK),
                "Rampage", dash(2.6, 0.2, 12, true, ParticleTypes.EXPLOSION, SoundEvents.RAVAGER_STEP),
                "Mega Roar", aura(9, 8, 3.0, 0.5, 0, ParticleTypes.EXPLOSION, SoundEvents.RAVAGER_ROAR, weak, slow));

        // ---------------------------------------------------------------- bosses
        kit(MorphForm.WARDEN,
                "Sonic Boom", hit(16, 10, 1.5, 0.2, 0, Dmg.SONIC, ParticleTypes.SONIC_BOOM, SoundEvents.WARDEN_SONIC_BOOM),
                "Darkness Pulse", aura(16, 0, 0, 0, 0, ParticleTypes.SCULK_SOUL, SoundEvents.WARDEN_HEARTBEAT,
                        fx(MobEffects.DARKNESS, 200, 0), slow),
                "Mega Sonic Boom", hit(28, 22, 2.5, 0.4, 0, Dmg.SONIC, ParticleTypes.SONIC_BOOM, SoundEvents.WARDEN_SONIC_BOOM),
                "Sense", glow(48, SoundEvents.WARDEN_SNIFF));
        kit(MorphForm.ELDER_GUARDIAN,
                "Laser", hit(16, 8, 0.4, 0, 0, Dmg.MAGIC, ParticleTypes.GLOW, SoundEvents.GUARDIAN_ATTACK),
                "Mining Curse", aura(24, 0, 0, 0, 0, ParticleTypes.ENCHANT, SoundEvents.ELDER_GUARDIAN_CURSE,
                        fx(MobEffects.DIG_SLOWDOWN, 600, 2), slow, weak),
                "Mega Laser", hit(28, 20, 1.0, 0, 0, Dmg.MAGIC, ParticleTypes.ELECTRIC_SPARK, SoundEvents.GUARDIAN_ATTACK),
                "Spike Shell", all(spikes, self(ParticleTypes.CRIT, SoundEvents.ELDER_GUARDIAN_FLOP, resist)));
        kit(MorphForm.WITHER,
                "Wither Skull", shoot(Shot.WITHER_SKULL, 1, 0, SoundEvents.WITHER_SHOOT),
                "Wither Aura", aura(6, 2, 0.4, 0, 0, ParticleTypes.SMOKE, SoundEvents.WITHER_AMBIENT, fx(MobEffects.WITHER, 120, 1)),
                "Blue Skull Barrage", shoot(Shot.BLUE_SKULL, 3, 10, SoundEvents.WITHER_SHOOT),
                "Wither Armor", all(self(ParticleTypes.SMOKE, SoundEvents.WITHER_SPAWN, resist, regen, absorb),
                        aura(6, 4, 1.8, 0.3, 0, ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE)));
        kit(MorphForm.ENDER_DRAGON,
                "Dragon Fireball", shoot(Shot.DRAGON_FIREBALL, 1, 0, SoundEvents.ENDER_DRAGON_SHOOT),
                "Wing Blast", aura(8, 3, 2.5, 0.6, 0, ParticleTypes.CLOUD, SoundEvents.ENDER_DRAGON_FLAP),
                "Dragon Breath Storm", shoot(Shot.DRAGON_FIREBALL, 3, 15, SoundEvents.ENDER_DRAGON_SHOOT),
                "Dragon Roar", all(aura(12, 0, 1.2, 0.2, 0, ParticleTypes.DRAGON_BREATH, SoundEvents.ENDER_DRAGON_GROWL, weak, slow),
                        self(ParticleTypes.DRAGON_BREATH, SoundEvents.ENDER_DRAGON_GROWL, strength, resist)));
    }

    // ============================================================================ building blocks

    static Fx fx(MobEffect effect, int ticks, int amp) {
        return new Fx(effect, ticks, amp);
    }

    /** Runs the first power; if it worked, the rest too. */
    static Power all(Power... parts) {
        return (p, s, t) -> {
            if (!parts[0].use(p, s, t)) return false;
            for (int i = 1; i < parts.length; i++) parts[i].use(p, s, t);
            return true;
        };
    }

    /** Effects on yourself. */
    static Power self(ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            give(p, s, effects);
            puff(p, particle, 25);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Effects on yourself, and nothing can knock you back for a while. */
    static Power steady(int ticks, ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            give(p, s, effects);
            MorphData.root(p).putLong(MorphData.STEADY, p.level().getGameTime() + (long) (ticks * s));
            puff(p, particle, 25);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Mobs that hit you get hurt back for a while. */
    static Power spikes(int ticks, ParticleOptions particle, SoundEvent sound) {
        return (p, s, t) -> {
            MorphData.root(p).putLong(MorphData.SPIKES, p.level().getGameTime() + (long) (ticks * s));
            puff(p, particle, 30);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    static Power heal(float amount, boolean clearBad, ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            p.heal(amount * s);
            if (clearBad) {
                List<MobEffect> bad = new ArrayList<>();
                for (MobEffectInstance e : p.getActiveEffects()) {
                    if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) bad.add(e.getEffect());
                }
                bad.forEach(p::removeEffect);
            }
            give(p, s, effects);
            puff(p, particle, 15);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Heal and fill up your food. */
    static Power snack(float heal, int food, ParticleOptions particle, SoundEvent sound) {
        return (p, s, t) -> {
            p.heal(heal * s);
            p.getFoodData().eat(food, 0.6F);
            puff(p, particle, 10);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    static Power extinguish() {
        return (p, s, t) -> {
            p.clearFire();
            return true;
        };
    }

    /** Effects for you and every player near you. */
    static Power friends(double radius, ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            for (ServerPlayer other : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(radius))) {
                give(other, s, effects);
                puff(other, particle, 10);
            }
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Zoom the way you're looking. flat = stay along the ground. Hurts mobs in front if damage > 0. */
    static Power dash(double speed, double up, float damage, boolean flat, ParticleOptions particle, SoundEvent sound) {
        return (p, s, t) -> {
            Vec3 look = p.getLookAngle();
            if (damage > 0) {
                for (LivingEntity e : SuperPowers.cone(p, 6, 0.55, MorphData.getForm(p))) {
                    e.hurt(p.damageSources().playerAttack(p), damage * s);
                    e.knockback(1.0, p.getX() - e.getX(), p.getZ() - e.getZ());
                }
            }
            Vec3 dir = flat ? new Vec3(look.x, 0, look.z).normalize() : look;
            p.setDeltaMovement(dir.x * speed, (flat ? 0 : dir.y * speed) + up, dir.z * speed);
            p.hurtMarked = true;
            p.fallDistance = 0;
            SuperPowers.trail(p, particle, 6, 3);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** A big jump: up, plus forward the way you look. You float down safely. */
    static Power leap(double up, double forward, ParticleOptions particle, SoundEvent sound) {
        return (p, s, t) -> {
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            p.setDeltaMovement(flat.x * forward, up, flat.z * forward);
            p.hurtMarked = true;
            p.fallDistance = 0;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false));
            puff(p, particle, 20);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Hits everything around you. */
    static Power aura(double radius, float damage, double knock, double up, int fireSecs,
                      ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            for (LivingEntity e : around(p, radius)) affect(p, e, damage * s, knock, up, fireSecs, Dmg.MELEE, s, effects);
            ring(p, particle, Math.min(radius, 8));
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Hits everything in front of you. */
    static Power cone(double range, double minDot, float damage, double knock, int fireSecs,
                      ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            for (double d = 1.0; d <= range; d += 0.8) {
                Vec3 at = eye.add(look.scale(d));
                double spread = d * 0.15;
                p.serverLevel().sendParticles(particle, at.x, at.y, at.z, 4, spread, spread, spread, 0.02);
            }
            for (LivingEntity e : SuperPowers.cone(p, range, minDot, MorphData.getForm(p))) {
                affect(p, e, damage * s, knock, 0.1, fireSecs, Dmg.MELEE, s, effects);
            }
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Hits the mob you're looking at. */
    static Power hit(double range, float damage, double knock, double up, int fireSecs, Dmg kind,
                     ParticleOptions beam, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, range, t);
            if (target == null) return SuperPowers.noTarget(p, "Look at a mob to use this power");
            SuperPowers.beam(p.serverLevel(), p.getEyePosition(), mid(target), beam, kind == Dmg.SONIC ? 1 : 2);
            affect(p, target, damage * s, knock, up, fireSecs, kind, s, effects);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Pulls the mob you're looking at over to you. */
    static Power pull(double range, ParticleOptions beam, SoundEvent sound) {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, range, t);
            if (target == null) return SuperPowers.noTarget(p, "Look at a mob to grab it");
            SuperPowers.beam(p.serverLevel(), p.getEyePosition(), mid(target), beam, 2);
            Vec3 to = p.position().subtract(target.position());
            target.setDeltaMovement(to.x * 0.3, Math.min(0.8, 0.3 + to.y * 0.15), to.z * 0.3);
            target.hurtMarked = true;
            target.hurt(p.damageSources().playerAttack(p), 2.0F * s);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Frog Gulp: a big bite, and small mobs get gobbled up for huge damage. */
    static Power gulp() {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, 6, t);
            if (target == null) return SuperPowers.noTarget(p, "Look at a mob to gulp it");
            boolean small = target.getBbHeight() < 1.0F && target.getBbWidth() < 1.0F;
            target.hurt(p.damageSources().playerAttack(p), (small ? 30.0F : 6.0F) * s);
            SuperPowers.beam(p.serverLevel(), p.getEyePosition(), mid(target), ParticleTypes.DRIPPING_LAVA, 2);
            Abilities.sound(p, SoundEvents.FROG_EAT, 1.0F);
            if (small) p.heal(4.0F);
            return true;
        };
    }

    static Power shoot(Shot shot, int count, float spread, SoundEvent sound) {
        return (p, s, t) -> {
            ServerLevel level = p.serverLevel();
            for (int i = 0; i < count; i++) {
                float yaw = p.getYRot() + (count == 1 ? 0 : (i - (count - 1) / 2.0F) * spread);
                Vec3 dir = Vec3.directionFromRotation(p.getXRot(), yaw);
                Vec3 start = p.getEyePosition().add(dir.scale(1.2));
                Entity e = switch (shot) {
                    case ARROW, FIRE_ARROW, SLOW_ARROW, PIERCING_ARROW -> {
                        Arrow a = new Arrow(level, p);
                        a.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        a.setBaseDamage(a.getBaseDamage() * Math.max(1.0F, s * 0.75F));
                        if (shot == Shot.FIRE_ARROW) a.setSecondsOnFire(100);
                        if (shot == Shot.SLOW_ARROW) a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                        if (shot == Shot.PIERCING_ARROW) {
                            a.setPierceLevel((byte) 3);
                            a.setShotFromCrossbow(true);
                        }
                        a.shootFromRotation(p, p.getXRot(), yaw, 0.0F, 3.2F, 1.0F);
                        yield a;
                    }
                    case BIG_FIREBALL -> {
                        LargeFireball f = new LargeFireball(level, p, dir.x, dir.y, dir.z, 1);
                        f.setPos(start.x, start.y, start.z);
                        yield f;
                    }
                    case WITHER_SKULL, BLUE_SKULL -> {
                        WitherSkull w = new WitherSkull(level, p, dir.x, dir.y, dir.z);
                        w.setPos(start.x, start.y, start.z);
                        w.setDangerous(shot == Shot.BLUE_SKULL);
                        yield w;
                    }
                    case DRAGON_FIREBALL -> {
                        DragonFireball d = new DragonFireball(level, p, dir.x, dir.y, dir.z);
                        d.setPos(start.x, start.y, start.z);
                        yield d;
                    }
                    case TRIDENT -> {
                        ThrownTrident tr = new ThrownTrident(level, p, new ItemStack(Items.TRIDENT));
                        tr.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        tr.shootFromRotation(p, p.getXRot(), yaw, 0.0F, 2.5F, 1.0F);
                        yield tr;
                    }
                    case BAD_POTION -> {
                        Potion[] bad = {Potions.POISON, Potions.SLOWNESS, Potions.WEAKNESS, Potions.HARMING};
                        ThrownPotion tp = new ThrownPotion(level, p);
                        tp.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), bad[p.getRandom().nextInt(bad.length)]));
                        tp.shootFromRotation(p, p.getXRot(), yaw, -20.0F, 0.75F, 1.0F);
                        yield tp;
                    }
                };
                level.addFreshEntity(e);
            }
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Homing shulker bullets at the mob you're looking at (and more mobs nearby for a barrage). */
    static Power shulkerBullets(int count, double range) {
        return (p, s, t) -> {
            List<LivingEntity> targets = new ArrayList<>();
            LivingEntity first = SuperPowers.lookTarget(p, range, t);
            if (first != null) targets.add(first);
            if (count > 1) {
                for (LivingEntity e : around(p, 16)) {
                    if (targets.size() >= count) break;
                    if (!targets.contains(e)) targets.add(e);
                }
            }
            if (targets.isEmpty()) return SuperPowers.noTarget(p, "Look at a mob to shoot it");
            for (LivingEntity target : targets) {
                ShulkerBullet b = new ShulkerBullet(p.level(), p, target, Direction.Axis.Y);
                b.setPos(p.getX(), p.getEyeY() + 0.5, p.getZ());
                p.level().addFreshEntity(b);
            }
            Abilities.sound(p, SoundEvents.SHULKER_SHOOT, 1.0F);
            return true;
        };
    }

    static Power helpers(EntityType<? extends Mob> type, int count, SoundEvent sound) {
        return (p, s, t) -> {
            SuperPowers.spawnHelpers(p, type, count + (s >= 1.5F ? 1 : 0));
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    /** Invisible (optional) and mobs lose track of you. */
    static Power cloak(int ticks, boolean invisible, ParticleOptions particle, SoundEvent sound, Fx... effects) {
        return (p, s, t) -> {
            int time = (int) (ticks * s);
            if (invisible) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, time, 0, false, false));
            MorphData.root(p).putLong(MorphData.CLOAK, p.level().getGameTime() + time);
            for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
                m.setTarget(null);
            }
            give(p, s, effects);
            puff(p, particle, 30);
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    static Power glow(double radius, SoundEvent sound) {
        return (p, s, t) -> {
            for (LivingEntity e : around(p, radius)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (300 * s), 0));
            Abilities.sound(p, sound, 1.0F);
            return true;
        };
    }

    static Power lightning(double range) {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, range, t);
            if (target == null) return SuperPowers.noTarget(p, "Look at a mob to strike it with lightning");
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(p.level());
            if (bolt == null) return false;
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(p);
            p.level().addFreshEntity(bolt);
            target.hurt(p.damageSources().playerAttack(p), 6.0F * s);
            Abilities.sound(p, SoundEvents.TRIDENT_THUNDER, 1.0F);
            return true;
        };
    }

    /** Evoker fangs: a line the way you look, or two rings around you. */
    static Power fangs(boolean circle) {
        return (p, s, t) -> {
            ServerLevel level = p.serverLevel();
            if (circle) {
                for (int ring = 0; ring < 2; ring++) {
                    double r = ring == 0 ? 2.5 : 5.0;
                    int n = ring == 0 ? 8 : 14;
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        level.addFreshEntity(new EvokerFangs(level, p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r,
                                (float) a, ring * 4, p));
                    }
                }
            } else {
                Vec3 look = p.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                for (int i = 1; i <= 16; i++) {
                    level.addFreshEntity(new EvokerFangs(level, p.getX() + flat.x * 1.25 * i, p.getY(), p.getZ() + flat.z * 1.25 * i,
                            p.getYRot() * ((float) Math.PI / 180F), i, p));
                }
            }
            Abilities.sound(p, SoundEvents.EVOKER_CAST_SPELL, 1.0F);
            return true;
        };
    }

    static Power randomTeleport(double range) {
        return (p, s, t) -> {
            double oldX = p.getX(), oldY = p.getY(), oldZ = p.getZ();
            for (int i = 0; i < 16; i++) {
                double x = p.getX() + (p.getRandom().nextDouble() - 0.5) * 2 * range;
                double y = p.getY() + (p.getRandom().nextInt(16) - 8);
                double z = p.getZ() + (p.getRandom().nextDouble() - 0.5) * 2 * range;
                if (p.randomTeleport(x, y, z, true)) {
                    p.serverLevel().sendParticles(ParticleTypes.PORTAL, oldX, oldY + 1, oldZ, 40, 0.3, 0.8, 0.3, 0.2);
                    Abilities.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.2F);
                    return true;
                }
            }
            return SuperPowers.noTarget(p, "No room to teleport");
        };
    }

    /** Teleport right behind the mob you're looking at. */
    static Power blink(double range) {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, range, t);
            if (target == null) return SuperPowers.noTarget(p, "Look at a mob to sneak up on it");
            Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
            Vec3 behind = target.position().subtract(facing.scale(target.getBbWidth() / 2 + 1.2));
            if (!p.level().noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) {
                behind = target.position().add(p.position().subtract(target.position()).normalize().scale(1.5));
            }
            float yaw = (float) (Math.atan2(target.getZ() - behind.z, target.getX() - behind.x) * 180.0 / Math.PI) - 90.0F;
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.8, 0.3, 0.2);
            p.connection.teleport(behind.x, behind.y, behind.z, yaw, 10.0F);
            p.fallDistance = 0;
            return true;
        };
    }

    /** Random items from the list (count of them). */
    static Power give(int count, SoundEvent sound, Item... pool) {
        return (p, s, t) -> {
            for (int i = 0; i < count; i++) {
                ItemStack stack = new ItemStack(pool[p.getRandom().nextInt(pool.length)]);
                if (!p.getInventory().add(stack)) p.drop(stack, false);
            }
            Abilities.sound(p, sound, 1.0F);
            puff(p, ParticleTypes.HAPPY_VILLAGER, 10);
            return true;
        };
    }

    /** Piglin barter: hold a gold ingot and swap it for random Nether loot. */
    static Power barter() {
        Item[] loot = {Items.ENDER_PEARL, Items.STRING, Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.FIRE_CHARGE,
                Items.LEATHER, Items.SOUL_SAND, Items.NETHER_BRICK, Items.SPECTRAL_ARROW, Items.GRAVEL, Items.BLACKSTONE,
                Items.QUARTZ, Items.IRON_NUGGET, Items.MAGMA_CREAM, Items.GLOWSTONE_DUST};
        return (p, s, t) -> {
            ItemStack held = p.getMainHandItem();
            if (!held.is(Items.GOLD_INGOT)) return SuperPowers.noTarget(p, "Hold a gold ingot to barter");
            held.shrink(1);
            Item item = loot[p.getRandom().nextInt(loot.length)];
            ItemStack stack = new ItemStack(item, item == Items.STRING || item == Items.QUARTZ || item == Items.IRON_NUGGET ? 6 : 2);
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            Abilities.sound(p, SoundEvents.PIGLIN_ADMIRING_ITEM, 1.0F);
            return true;
        };
    }

    /** Pulls dropped items and XP over to you. */
    static Power magnet(double radius) {
        return (p, s, t) -> {
            int n = 0;
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(radius),
                    e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                e.teleportTo(p.getX(), p.getY() + 0.5, p.getZ());
                n++;
            }
            puff(p, ParticleTypes.NOTE, 10);
            Abilities.sound(p, SoundEvents.ALLAY_ITEM_TAKEN, 1.0F);
            if (n == 0) WatchActions.tell(p, "No items nearby", ChatFormatting.GRAY);
            return true;
        };
    }

    /** Every zombified piglin nearby goes after the mob you're looking at. */
    static Power angryCall(double radius) {
        return (p, s, t) -> {
            LivingEntity target = SuperPowers.lookTarget(p, 24, t);
            if (target == null) target = p.getLastHurtMob();
            if (target == null || !target.isAlive()) return SuperPowers.noTarget(p, "Look at a mob to call the piglins on it");
            LivingEntity enemy = target;
            for (ZombifiedPiglin z : p.level().getEntitiesOfClass(ZombifiedPiglin.class, p.getBoundingBox().inflate(radius),
                    z -> z != enemy && z.isAlive())) {
                z.setTarget(enemy);
                z.setPersistentAngerTarget(enemy.getUUID());
                z.startPersistentAngerTimer();
            }
            Abilities.sound(p, SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, 1.0F);
            return true;
        };
    }

    /** Web net: every mob in front gets webbed, slowed and poisoned. */
    static Power webNet(ParticleOptions web) {
        return (p, s, t) -> {
            List<LivingEntity> hit = SuperPowers.cone(p, 14, 0.75, MorphData.getForm(p));
            if (hit.isEmpty()) return SuperPowers.noTarget(p, "Look toward some mobs to throw a web net");
            for (LivingEntity e : hit) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (120 * s), 4));
                e.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (120 * s), 1));
                if (p.mayBuild() && p.level().getBlockState(e.blockPosition()).isAir()) {
                    p.level().setBlockAndUpdate(e.blockPosition(), Blocks.COBWEB.defaultBlockState());
                }
                SuperPowers.beam(p.serverLevel(), p.getEyePosition(), mid(e), web, 1);
            }
            Abilities.sound(p, SoundEvents.SPIDER_AMBIENT, 0.6F);
            return true;
        };
    }

    /** Turns water around you into ice you can walk on (it melts again). */
    static Power iceWalk(int radius) {
        return (p, s, t) -> {
            if (!p.mayBuild()) return true;
            BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
            BlockPos feet = p.blockPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    for (int dy = -1; dy >= -2; dy--) {
                        BlockPos pos = feet.offset(dx, dy, dz);
                        if (p.level().getBlockState(pos).is(Blocks.WATER) && p.level().getBlockState(pos.above()).isAir()) {
                            p.level().setBlockAndUpdate(pos, ice);
                            p.level().scheduleTick(pos, Blocks.FROSTED_ICE, 60 + p.getRandom().nextInt(60));
                            break;
                        }
                    }
                }
            }
            return true;
        };
    }

    // ============================================================================ small helpers

    private static void give(LivingEntity e, float s, Fx... effects) {
        for (Fx f : effects) e.addEffect(new MobEffectInstance(f.effect(), Math.max(20, (int) (f.ticks() * s)), f.amp()));
    }

    private static void affect(ServerPlayer p, LivingEntity e, float damage, double knock, double up, int fireSecs,
                               Dmg kind, float s, Fx... effects) {
        if (damage > 0) e.hurt(source(p, kind), damage);
        if (knock > 0) e.knockback(knock, p.getX() - e.getX(), p.getZ() - e.getZ());
        if (up > 0) {
            e.setDeltaMovement(e.getDeltaMovement().add(0, up, 0));
            e.hurtMarked = true;
        }
        if (fireSecs > 0) e.setSecondsOnFire(fireSecs);
        give(e, s, effects);
    }

    private static DamageSource source(ServerPlayer p, Dmg kind) {
        return switch (kind) {
            case MELEE -> p.damageSources().playerAttack(p);
            case SONIC -> p.damageSources().sonicBoom(p);
            case MAGIC -> p.damageSources().indirectMagic(p, p);
            case FREEZE -> p.damageSources().freeze();
        };
    }

    /** Mobs around you (not you, not your kind, not your helpers). */
    private static List<LivingEntity> around(ServerPlayer p, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Abilities.nearby(p, radius, MorphData.getForm(p))) {
            if (!SuperPowers.isHelper(e) && e.distanceTo(p) <= radius) list.add(e);
        }
        return list;
    }

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() / 2, 0);
    }

    private static void puff(ServerPlayer p, ParticleOptions particle, int count) {
        p.serverLevel().sendParticles(particle, p.getX(), p.getY() + 1, p.getZ(), count, 0.5, 0.7, 0.5, 0.05);
    }

    private static void ring(ServerPlayer p, ParticleOptions particle, double radius) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            p.serverLevel().sendParticles(particle, p.getX() + Math.cos(a) * radius, p.getY() + 0.5,
                    p.getZ() + Math.sin(a) * radius, 2, 0.2, 0.2, 0.2, 0.02);
        }
        puff(p, particle, 15);
    }
}
