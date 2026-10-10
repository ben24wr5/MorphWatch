package com.morphwatch.client;

import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The transformation animation, for every player you can see:
 * gold light spirals up from the watch, the player spins and shrinks/grows into the new shape,
 * a flash at the halfway point, a little jingle, and your own camera pulls out to third person.
 */
public final class TransformAnims {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.2F), 1.0F);
    private static final DustParticleOptions GOLD_SMALL = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.45F), 0.6F);
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35F, 1.0F, 0.2F), 1.2F);
    private static final DustParticleOptions GREEN_SMALL = new DustParticleOptions(new Vector3f(0.7F, 1.0F, 0.5F), 0.7F);
    /** Major arpeggio for turning into a mob, the same notes falling for turning back to human. */
    private static final float[] JINGLE_UP = {0.8F, 1.0F, 1.2F, 1.6F};
    private static final float[] JINGLE_DOWN = {1.6F, 1.2F, 1.0F, 0.8F};

    /** How long the camera stays pulled out after the animation ends. */
    private static final int CAMERA_HOLD_TICKS = 15;

    public static final class Anim {
        public final MorphForm from;
        public final MorphForm to;
        public final long startTick;
        public final int duration;
        int lastTickHandled = -1;
        boolean burstDone = false;
        /** Your own full transformation sequence (see TransformSequence). */
        public boolean sequence = false;
        int lastBeep = Integer.MIN_VALUE;

        Anim(MorphForm from, MorphForm to, long startTick, int duration) {
            this.from = from;
            this.to = to;
            this.startTick = startTick;
            this.duration = Math.max(1, duration);
        }

        /** 0 at the start, 1 at the end (can go past 1 while we wait for the server). */
        public float progress(float now) {
            return (now - startTick) / duration;
        }

        /** How far the body has changed: 0..1 (in the sequence it changes during the "morph" part only). */
        public float morphProgress(float now) {
            if (!sequence) return progress(now);
            return Mth.clamp((now - startTick - TransformSequence.MORPH_START)
                    / (TransformSequence.MORPH_END - TransformSequence.MORPH_START), 0.0F, 1.0F);
        }

        /** Old shape for the first half, new shape for the second half. */
        public MorphForm drawForm(float now) {
            return morphProgress(now) < 0.5F ? from : to;
        }
    }

    private static final Map<UUID, Anim> ANIMS = new HashMap<>();
    private static boolean cameraPulledOut = false;
    private static CameraType savedCamera = CameraType.FIRST_PERSON;
    private static long cameraRestoreAt = -1;
    /** Your own transformation background: when it started, and when it goes away (-1 = still going). */
    private static long backgroundStart = -1000;
    private static long backgroundEnd = -1000;
    private static final int BACKGROUND_FADE_IN = 3;
    private static final int BACKGROUND_FADE_OUT = 6;

    private TransformAnims() {}

    /** 0..1: how strongly the transformation background shows behind you right now. */
    public static float backgroundAlpha(float now) {
        float in = Mth.clamp((now - backgroundStart) / BACKGROUND_FADE_IN, 0.0F, 1.0F);
        if (backgroundEnd < 0) return in;
        float out = Mth.clamp((backgroundEnd - now) / BACKGROUND_FADE_OUT, 0.0F, 1.0F);
        return Math.min(in, out);
    }

    public static void start(Player player, MorphForm from, MorphForm to, int ticks, boolean allowSequence) {
        long now = player.level().getGameTime();
        Minecraft mc = Minecraft.getInstance();
        // Your own transformation plays the full cut-scene. Turning back to human starts at the green
        // flood (as a mob you have no arm up to slam the watch).
        boolean sequence = player == mc.player && allowSequence;
        boolean skipSlam = sequence && to == MorphForm.NONE;
        int skip = skipSlam ? com.morphwatch.Transformer.SEQUENCE_HUMAN_SKIP : 0;
        // Back to human: first the badge on your chest flashes white and red, then the cut-scene
        int flash = skipSlam ? com.morphwatch.Transformer.HUMAN_FLASH_TICKS : 0;
        Anim anim = new Anim(from, to, now - skip + flash, sequence ? com.morphwatch.Transformer.SEQUENCE_TICKS : ticks);
        anim.sequence = sequence;
        if (skipSlam) anim.lastTickHandled = skip - 1;
        ANIMS.put(player.getUUID(), anim);

        if (player == mc.player) {
            if (!cameraPulledOut) savedCamera = mc.options.getCameraType();
            if (sequence) {
                // the slam close-up first (straight to facing you when turning back to human)
                mc.options.setCameraType(skipSlam ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                cameraPulledOut = true;
                TransformSequence.begin(player);
            } else if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                cameraPulledOut = true;
            }
            cameraRestoreAt = -1;
            backgroundStart = sequence ? anim.startTick + (long) TransformSequence.GREEN_END - 4 : now;
            backgroundEnd = -1;
        }
    }

    public static Anim get(Player player) {
        return ANIMS.get(player.getUUID());
    }

    public static float now(Player player, float partialTick) {
        return player.level().getGameTime() + partialTick;
    }

    // -------------------------------------------------------------- shape maths

    /** How tall a form looks: real mob height (Iron Golem is bigger than you), 1.8 for a human. */
    public static float visualHeight(MorphForm form) {
        return form == MorphForm.NONE ? 1.8F : form.type().getDimensions().height;
    }

    /** Smooth start and end. */
    public static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * How much to scale the shape being drawn right now, so the size slides smoothly from the old
     * shape's height to the new one, with a little squeeze in the middle where the swap happens.
     */
    public static float scaleFor(Anim anim, float now) {
        float t = Mth.clamp(anim.morphProgress(now), 0.0F, 1.0F);
        float height = Mth.lerp(ease(t), visualHeight(anim.from), visualHeight(anim.to));
        float squeeze = 1.0F - 0.3F * Mth.sin((float) Math.PI * t);
        float drawn = visualHeight(anim.drawForm(now));
        return Math.max(0.05F, height / drawn * squeeze);
    }

    /** Two full spins over the animation, fastest in the middle. */
    public static float spinDegrees(Anim anim, float now) {
        return 720.0F * ease(anim.morphProgress(now));
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            ANIMS.clear();
            return;
        }
        long gameTime = mc.level.getGameTime();
        Iterator<Map.Entry<UUID, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Anim> entry = it.next();
            Anim anim = entry.getValue();
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            int elapsed = (int) (gameTime - anim.startTick);

            // Keep showing the new shape until the server confirms it (or give up after a while).
            boolean done = elapsed >= anim.duration
                    && (player == null || MorphData.getForm(player) == anim.to || elapsed > anim.duration + 40);
            if (done) {
                if (player == mc.player) {
                    int hold = anim.sequence ? 1 : CAMERA_HOLD_TICKS;
                    cameraRestoreAt = gameTime + hold;
                    backgroundEnd = gameTime + hold;
                }
                it.remove();
                continue;
            }
            // Back to human: beeps while the badge flashes
            if (anim.sequence && anim.to == MorphForm.NONE && player == mc.player
                    && elapsed < TransformSequence.SLAM_END && elapsed != anim.lastBeep) {
                anim.lastBeep = elapsed;
                int colour = TransformSequence.badgeFlash(anim, elapsed);
                if (colour >= 0 && (elapsed % 3 == 0)) {
                    mc.level.playLocalSound(player.getX(), player.getY() + 1, player.getZ(),
                            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F,
                            colour == TransformSequence.RED ? 0.7F : 1.4F, false);
                }
            }
            // Your sequence: after the slam close-up the camera turns round to face you
            if (anim.sequence && player == mc.player && elapsed >= TransformSequence.SLAM_END
                    && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (player == null || elapsed > anim.duration) continue;

            while (anim.lastTickHandled < elapsed) {
                anim.lastTickHandled++;
                effects(mc, player, anim, anim.lastTickHandled);
            }
        }

        // Put the camera back to first person once the show is over.
        if (cameraPulledOut && cameraRestoreAt >= 0 && gameTime >= cameraRestoreAt) {
            mc.options.setCameraType(savedCamera);
            cameraPulledOut = false;
            cameraRestoreAt = -1;
        }
    }

    /** Particles and sounds for one tick of the animation. */
    private static void effects(Minecraft mc, Player player, Anim anim, int tick) {
        if (anim.sequence) TransformSequence.tickSounds(mc, player, tick);
        float t = anim.sequence ? anim.morphProgress(anim.startTick + tick) : tick / (float) anim.duration;
        if (anim.sequence && (t <= 0.0F || t >= 1.0F)) return;   // energy only while the body changes
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float height = Mth.lerp(ease(t), visualHeight(anim.from), visualHeight(anim.to));
        float width = Math.max(0.5F, player.getBbWidth());

        // Gold light spiralling up from the wrist (green energy in your own sequence)
        DustParticleOptions big = anim.sequence ? GREEN : GOLD;
        DustParticleOptions small = anim.sequence ? GREEN_SMALL : GOLD_SMALL;
        for (int strand = 0; strand < 3; strand++) {
            double angle = t * Math.PI * 6 + strand * (Math.PI * 2 / 3);
            double r = width * 0.9;
            double py = y + height * Mth.clamp(t * 1.2F, 0.0F, 1.0F);
            mc.level.addParticle(big, x + Math.cos(angle) * r, py, z + Math.sin(angle) * r, 0, 0.02, 0);
            mc.level.addParticle(small, x + Math.cos(angle + 0.4) * r * 0.7, py - 0.2, z + Math.sin(angle + 0.4) * r * 0.7,
                    0, 0.04, 0);
        }

        // Jingle: one note every 2 ticks at the start
        float[] notes = anim.to == MorphForm.NONE ? JINGLE_DOWN : JINGLE_UP;
        if (tick % 2 == 0 && tick / 2 < notes.length) {
            mc.level.playLocalSound(x, y + 1, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    1.2F, notes[tick / 2], false);
        }

        // Halfway: burst of light, flash on your own screen
        if (!anim.burstDone && t >= 0.5F) {
            anim.burstDone = true;
            for (int i = 0; i < 40; i++) {
                double a = mc.level.random.nextDouble() * Math.PI * 2;
                double up = mc.level.random.nextDouble() * 0.3;
                double speed = 0.15 + mc.level.random.nextDouble() * 0.2;
                mc.level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : big, x, y + height * 0.5, z,
                        Math.cos(a) * speed, up, Math.sin(a) * speed);
            }
            mc.level.playLocalSound(x, y + 1, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.6F, false);
            if (player == mc.player && !anim.sequence) ClientState.flash();
        }
    }

    public static void clear() {
        ANIMS.clear();
        cameraPulledOut = false;
        cameraRestoreAt = -1;
        backgroundStart = -1000;
        backgroundEnd = -1000;
    }
}
