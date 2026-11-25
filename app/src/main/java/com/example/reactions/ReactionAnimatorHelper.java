package com.example.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java "Pulso VYB" physics & signature particle animation engine.
 * Supports all 7 VYB reactions:
 * 1) Pura vida — double shaka wave + golden/cyan stars
 * 2) Buena vibra — rapid electric buzz + brightness pulse + 8 orbiting stars
 * 3) ¡Qué rajado! — base seismic quake + 4 rising white smoke puffs + 9 glowing embers
 * 4) ¡Qué chiva! — chill head tilt + diagonal lens glint sweep + white sparkle
 * 5) ¡De una! — vertical pin-drop bounce + 2 concentric radar rings
 * 6) ¡Qué agüevado! — heavy deflated slump + 3 falling teardrop raindrops
 * 7) ¡Qué chicha! — furious swell + rapid X-shake + 2 diagonal ear steam jets
 */
public final class ReactionAnimatorHelper {

    public enum MotionStyle {
        HEARTBEAT_PULSE,
        FLOATING_SWAY,
        VOLCANO_ERUPT,
        EXCITED_HOP,
        AFFIRMATIVE_SNAP,
        DROOP_SIGH,
        FIERY_SHAKE
    }

    public static final long SIGNATURE_DURATION_MS = 1200L;

    private static final Map<View, AnimatorSet> ACTIVE_ANIMATORS = new WeakHashMap<>();
    private static final Map<View, AnimatorSet> ACTIVE_BURST_ANIMATORS = new WeakHashMap<>();
    private static final Map<View, ValueAnimator> ACTIVE_PARTICLE_ANIMATORS = new WeakHashMap<>();
    private static final Map<View, MotionStyle> ACTIVE_STYLES = new WeakHashMap<>();
    private static final Map<View, Boolean> DETACH_LISTENERS = new WeakHashMap<>();
    private static final Map<View, View.OnLayoutChangeListener> PIVOT_LAYOUT_LISTENERS = new WeakHashMap<>();
    private static final Map<String, Long> IN_FLIGHT_ARRIVAL_UPTIME = new ConcurrentHashMap<>();

    private ReactionAnimatorHelper() {
    }

    /**
     * Exact damped spring curve from Pulso VYB HTML prototype: 1 - exp(-8.5*t)*cos(6.8*t)
     * normalized so f(0)=0 and f(1)=1.
     */
    @NonNull
    public static TimeInterpolator createCascadeSpringInterpolator() {
        final float decay = 8.5f;
        final float freq = 6.8f;
        final float endVal = 1f - (float) (Math.exp(-decay) * Math.cos(freq));
        return input -> {
            if (input <= 0f) return 0f;
            if (input >= 1f) return 1f;
            float raw = 1f - (float) (Math.exp(-decay * input) * Math.cos(freq * input));
            return raw / (endVal != 0f ? endVal : 1f);
        };
    }

    public static void markParabolicFlightInFlight(@Nullable String reactionKey, long flightDurationMs) {
        if (reactionKey == null || reactionKey.isEmpty() || flightDurationMs <= 0L) {
            return;
        }
        IN_FLIGHT_ARRIVAL_UPTIME.put(reactionKey, SystemClock.uptimeMillis() + flightDurationMs);
    }

    public static long consumeParabolicFlightDelayMs(@Nullable String reactionKey) {
        if (reactionKey == null || reactionKey.isEmpty()) {
            return 0L;
        }
        Long arrivalUptime = IN_FLIGHT_ARRIVAL_UPTIME.remove(reactionKey);
        if (arrivalUptime == null) {
            return 0L;
        }
        long remaining = arrivalUptime - SystemClock.uptimeMillis();
        return Math.max(0L, Math.min(500L, remaining));
    }

    @NonNull
    public static MotionStyle resolveStyleForKey(@Nullable String reactionKey) {
        if (reactionKey == null) {
            return MotionStyle.FLOATING_SWAY;
        }
        switch (reactionKey) {
            case ReactionCatalog.KEY_PURA_VIDA:
                return MotionStyle.HEARTBEAT_PULSE;
            case ReactionCatalog.KEY_BUENA_VIBRA:
                return MotionStyle.FLOATING_SWAY;
            case ReactionCatalog.KEY_QUE_RAJADO:
                return MotionStyle.VOLCANO_ERUPT;
            case ReactionCatalog.KEY_QUE_CHIVA:
                return MotionStyle.EXCITED_HOP;
            case ReactionCatalog.KEY_DE_UNA:
                return MotionStyle.AFFIRMATIVE_SNAP;
            case ReactionCatalog.KEY_QUE_AGUEVADO:
                return MotionStyle.DROOP_SIGH;
            case ReactionCatalog.KEY_QUE_CHICHA:
                return MotionStyle.FIERY_SHAKE;
            default:
                return MotionStyle.FLOATING_SWAY;
        }
    }

    public static int resolvePrimaryAccentColor(@Nullable String reactionKey) {
        if (reactionKey == null) {
            return Color.parseColor("#38BDF8");
        }
        switch (reactionKey) {
            case ReactionCatalog.KEY_PURA_VIDA:
                return Color.parseColor("#10B981");
            case ReactionCatalog.KEY_BUENA_VIBRA:
                return Color.parseColor("#A855F7");
            case ReactionCatalog.KEY_QUE_RAJADO:
                return Color.parseColor("#FF9100");
            case ReactionCatalog.KEY_QUE_CHIVA:
                return Color.parseColor("#F59E0B");
            case ReactionCatalog.KEY_DE_UNA:
                return Color.parseColor("#FF5252");
            case ReactionCatalog.KEY_QUE_AGUEVADO:
                return Color.parseColor("#38BDF8");
            case ReactionCatalog.KEY_QUE_CHICHA:
                return Color.parseColor("#EF4444");
            default:
                return Color.parseColor("#38BDF8");
        }
    }

    public static int resolveSecondaryAccentColor(@Nullable String reactionKey) {
        if (reactionKey == null) {
            return Color.parseColor("#2563EB");
        }
        switch (reactionKey) {
            case ReactionCatalog.KEY_PURA_VIDA:
                return Color.parseColor("#00E5FF");
            case ReactionCatalog.KEY_BUENA_VIBRA:
                return Color.parseColor("#FFE082");
            case ReactionCatalog.KEY_QUE_RAJADO:
                return Color.parseColor("#FFEA00");
            case ReactionCatalog.KEY_QUE_CHIVA:
                return Color.parseColor("#FFFFFF");
            case ReactionCatalog.KEY_DE_UNA:
                return Color.parseColor("#FF8A65");
            case ReactionCatalog.KEY_QUE_AGUEVADO:
                return Color.parseColor("#94A3B8");
            case ReactionCatalog.KEY_QUE_CHICHA:
                return Color.parseColor("#FFFFFF");
            default:
                return Color.parseColor("#2563EB");
        }
    }

    public static void attachIdleAnimation(
            @NonNull View targetView,
            @Nullable String reactionKey,
            long startDelayMs,
            float intensityScale
    ) {
        clearIdleAnimationOnly(targetView);

        float density = targetView.getResources().getDisplayMetrics().density;
        float amp = Math.max(0.55f, Math.min(1.45f, intensityScale));
        MotionStyle style = resolveStyleForKey(reactionKey);

        ensureDetachCleanupHook(targetView);
        applyPivotForStyle(targetView, style);
        attachPivotLayoutSync(targetView, style);

        AnimatorSet set = buildAnimatorSet(targetView, style, density, amp);
        if (startDelayMs > 0L) {
            set.setStartDelay(startDelayMs);
        }
        ACTIVE_ANIMATORS.put(targetView, set);
        ACTIVE_STYLES.put(targetView, style);
        set.start();
    }

    /**
     * Subtle hover pop + accent burst used when gliding over a reaction icon.
     */
    public static void playHoverSpark(@NonNull View targetView, @Nullable String reactionKey) {
        cancelBurstAnimator(targetView);
        MotionStyle style = resolveStyleForKey(reactionKey);
        applyPivotForStyle(targetView, style);

        PropertyValuesHolder sx = PropertyValuesHolder.ofKeyframe(
                View.SCALE_X,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.28f, 1.14f),
                Keyframe.ofFloat(0.62f, 0.95f),
                Keyframe.ofFloat(1f, 1f)
        );
        PropertyValuesHolder sy = PropertyValuesHolder.ofKeyframe(
                View.SCALE_Y,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.28f, 0.90f),
                Keyframe.ofFloat(0.62f, 1.10f),
                Keyframe.ofFloat(1f, 1f)
        );
        ObjectAnimator pop = ObjectAnimator.ofPropertyValuesHolder(targetView, sx, sy);
        pop.setDuration(220L);
        pop.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        AnimatorSet burstSet = new AnimatorSet();
        burstSet.play(pop);
        ACTIVE_BURST_ANIMATORS.put(targetView, burstSet);
        burstSet.start();

        spawnSignatureParticles(targetView, reactionKey, 320L, true);
    }

    /**
     * Triggers the full 1.2-second Pulso VYB exclusive signature choreography + particles.
     */
    public static void playSelectionBurst(@NonNull View targetView, @Nullable String reactionKey, float scaleBoost) {
        playSignatureMicroAnimation(targetView, reactionKey);
    }

    /**
     * Executes the exact 1200ms signature micro-animation and physical particle system for each of the 7 emotions.
     */
    public static void playSignatureMicroAnimation(@NonNull View targetView, @Nullable String reactionKey) {
        cancelBurstAnimator(targetView);
        cancelParticleAnimator(targetView);
        clearBrightnessFilter(targetView);

        ensureDetachCleanupHook(targetView);
        MotionStyle style = resolveStyleForKey(reactionKey);
        applyPivotForStyle(targetView, style);

        float density = targetView.getResources().getDisplayMetrics().density;
        ObjectAnimator bodyAnim = buildSignatureBodyAnimator(targetView, style, density);
        bodyAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clearBrightnessFilter(targetView);
                resetTransforms(targetView);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                clearBrightnessFilter(targetView);
                resetTransforms(targetView);
            }
        });

        AnimatorSet burstSet = new AnimatorSet();
        if (style == MotionStyle.FLOATING_SWAY || style == MotionStyle.FIERY_SHAKE) {
            ValueAnimator flashAnim = buildBrightnessFlashAnimator(targetView, style);
            burstSet.playTogether(bodyAnim, flashAnim);
        } else {
            burstSet.play(bodyAnim);
        }
        ACTIVE_BURST_ANIMATORS.put(targetView, burstSet);
        burstSet.start();

        spawnSignatureParticles(targetView, reactionKey, SIGNATURE_DURATION_MS, false);
    }

    @NonNull
    private static ObjectAnimator buildSignatureBodyAnimator(
            @NonNull View target,
            @NonNull MotionStyle style,
            float density
    ) {
        switch (style) {
            case HEARTBEAT_PULSE: {
                // Pura vida: double Shaka wave (scale 1 -> 1.42 -> 1.45 -> 1.38 -> 1.25 -> 1, rot -20 -> +24 -> -16 -> +14 -> 0)
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.18f, 1.42f),
                                Keyframe.ofFloat(0.38f, 1.45f),
                                Keyframe.ofFloat(0.58f, 1.38f),
                                Keyframe.ofFloat(0.78f, 1.25f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.18f, 1.42f),
                                Keyframe.ofFloat(0.38f, 1.45f),
                                Keyframe.ofFloat(0.58f, 1.38f),
                                Keyframe.ofFloat(0.78f, 1.25f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.ROTATION,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.18f, -20f),
                                Keyframe.ofFloat(0.38f, 24f),
                                Keyframe.ofFloat(0.58f, -16f),
                                Keyframe.ofFloat(0.78f, 14f),
                                Keyframe.ofFloat(1.00f, 0f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new PathInterpolator(0.42f, 0f, 0.58f, 1f));
                return anim;
            }
            case FLOATING_SWAY: {
                // Buena vibra: rapid electric buzz + XY jitter
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.10f, 1.45f),
                                Keyframe.ofFloat(0.20f, 1.42f),
                                Keyframe.ofFloat(0.32f, 1.48f),
                                Keyframe.ofFloat(0.45f, 1.40f),
                                Keyframe.ofFloat(0.62f, 1.32f),
                                Keyframe.ofFloat(0.80f, 1.15f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.10f, 1.45f),
                                Keyframe.ofFloat(0.20f, 1.42f),
                                Keyframe.ofFloat(0.32f, 1.48f),
                                Keyframe.ofFloat(0.45f, 1.40f),
                                Keyframe.ofFloat(0.62f, 1.32f),
                                Keyframe.ofFloat(0.80f, 1.15f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_X,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.10f, -2.0f * density),
                                Keyframe.ofFloat(0.20f, 2.5f * density),
                                Keyframe.ofFloat(0.32f, -2.0f * density),
                                Keyframe.ofFloat(0.45f, 2.0f * density),
                                Keyframe.ofFloat(0.62f, -1.5f * density),
                                Keyframe.ofFloat(0.80f, 1.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_Y,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.10f, -1.0f * density),
                                Keyframe.ofFloat(0.20f, 1.0f * density),
                                Keyframe.ofFloat(0.32f, 1.5f * density),
                                Keyframe.ofFloat(0.45f, -1.5f * density),
                                Keyframe.ofFloat(0.62f, 0f),
                                Keyframe.ofFloat(0.80f, 0f),
                                Keyframe.ofFloat(1.00f, 0f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new LinearInterpolator());
                return anim;
            }
            case VOLCANO_ERUPT: {
                // ¡Qué rajado!: base seismic quake + eruption swell
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.12f, 1.35f),
                                Keyframe.ofFloat(0.24f, 1.38f),
                                Keyframe.ofFloat(0.36f, 1.40f),
                                Keyframe.ofFloat(0.50f, 1.35f),
                                Keyframe.ofFloat(0.72f, 1.20f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.12f, 1.35f),
                                Keyframe.ofFloat(0.24f, 1.38f),
                                Keyframe.ofFloat(0.36f, 1.40f),
                                Keyframe.ofFloat(0.50f, 1.35f),
                                Keyframe.ofFloat(0.72f, 1.20f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_X,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.12f, -2.5f * density),
                                Keyframe.ofFloat(0.24f, 2.5f * density),
                                Keyframe.ofFloat(0.36f, -2.0f * density),
                                Keyframe.ofFloat(0.50f, 2.0f * density),
                                Keyframe.ofFloat(0.72f, -1.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_Y,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.12f, 1.0f * density),
                                Keyframe.ofFloat(0.24f, -1.0f * density),
                                Keyframe.ofFloat(0.36f, 0f),
                                Keyframe.ofFloat(0.50f, 1.0f * density),
                                Keyframe.ofFloat(0.72f, 0f),
                                Keyframe.ofFloat(1.00f, 0f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new DecelerateInterpolator());
                return anim;
            }
            case EXCITED_HOP: {
                // ¡Qué chiva!: chill relaxed head tilt (-10° -> +7° -> 0°) + subtle lift
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.28f, 1.38f),
                                Keyframe.ofFloat(0.65f, 1.40f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.28f, 1.38f),
                                Keyframe.ofFloat(0.65f, 1.40f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.ROTATION,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.28f, -10f),
                                Keyframe.ofFloat(0.65f, 7f),
                                Keyframe.ofFloat(1.00f, 0f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_Y,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.28f, -3.0f * density),
                                Keyframe.ofFloat(0.65f, -4.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new PathInterpolator(0.34f, 1.56f, 0.64f, 1f));
                return anim;
            }
            case AFFIRMATIVE_SNAP: {
                // ¡De una!: vertical pin-drop bounce with bottom squash impact
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_Y,
                                Keyframe.ofFloat(0.00f, -14.0f * density),
                                Keyframe.ofFloat(0.24f, 2.0f * density),
                                Keyframe.ofFloat(0.48f, -7.0f * density),
                                Keyframe.ofFloat(0.68f, 1.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.25f),
                                Keyframe.ofFloat(0.24f, 1.38f),
                                Keyframe.ofFloat(0.48f, 1.18f),
                                Keyframe.ofFloat(0.68f, 1.14f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.25f),
                                Keyframe.ofFloat(0.24f, 0.86f),
                                Keyframe.ofFloat(0.48f, 1.26f),
                                Keyframe.ofFloat(0.68f, 0.94f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
                return anim;
            }
            case DROOP_SIGH: {
                // ¡Qué agüevado!: heavy downward slump
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_Y,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.30f, 4.0f * density),
                                Keyframe.ofFloat(0.68f, 5.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.30f, 1.24f),
                                Keyframe.ofFloat(0.68f, 1.22f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.30f, 0.92f),
                                Keyframe.ofFloat(0.68f, 0.94f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new PathInterpolator(0.42f, 0f, 0.58f, 1f));
                return anim;
            }
            case FIERY_SHAKE:
            default: {
                // ¡Qué chicha!: furious swell + rapid horizontal shake
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
                        target,
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_X,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.14f, 1.42f),
                                Keyframe.ofFloat(0.26f, 1.45f),
                                Keyframe.ofFloat(0.38f, 1.44f),
                                Keyframe.ofFloat(0.50f, 1.42f),
                                Keyframe.ofFloat(0.68f, 1.30f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.SCALE_Y,
                                Keyframe.ofFloat(0.00f, 1.00f),
                                Keyframe.ofFloat(0.14f, 1.42f),
                                Keyframe.ofFloat(0.26f, 1.45f),
                                Keyframe.ofFloat(0.38f, 1.44f),
                                Keyframe.ofFloat(0.50f, 1.42f),
                                Keyframe.ofFloat(0.68f, 1.30f),
                                Keyframe.ofFloat(1.00f, 1.00f)
                        ),
                        PropertyValuesHolder.ofKeyframe(
                                View.TRANSLATION_X,
                                Keyframe.ofFloat(0.00f, 0f),
                                Keyframe.ofFloat(0.14f, -3.0f * density),
                                Keyframe.ofFloat(0.26f, 3.5f * density),
                                Keyframe.ofFloat(0.38f, -3.0f * density),
                                Keyframe.ofFloat(0.50f, 3.0f * density),
                                Keyframe.ofFloat(0.68f, -2.0f * density),
                                Keyframe.ofFloat(1.00f, 0f)
                        )
                );
                anim.setDuration(SIGNATURE_DURATION_MS);
                anim.setInterpolator(new PathInterpolator(0.42f, 0f, 0.58f, 1f));
                return anim;
            }
        }
    }

    @NonNull
    private static ValueAnimator buildBrightnessFlashAnimator(
            @NonNull View targetView,
            @NonNull MotionStyle style
    ) {
        ValueAnimator flash = ValueAnimator.ofFloat(0f, 1f);
        flash.setDuration(SIGNATURE_DURATION_MS);
        flash.addUpdateListener(anim -> {
            if (!(targetView instanceof ImageView)) return;
            float t = (float) anim.getAnimatedValue();
            float gain;
            if (style == MotionStyle.FLOATING_SWAY) {
                // Pulse brightness matching HTML: 1 -> 1.85 -> 1.3 -> 1.9 -> 1.4 -> 1.6 -> 1.2 -> 1
                double wave = Math.sin(t * Math.PI * 5.0);
                gain = 1f + (float) (Math.max(0.0, wave) * (1.0 - t) * 0.75);
            } else {
                // Furious red swell brightness boost: up to 1.25x
                gain = 1f + (float) Math.sin(t * Math.PI) * 0.25f;
            }
            applyBrightnessScale((ImageView) targetView, gain);
        });
        flash.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clearBrightnessFilter(targetView);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                clearBrightnessFilter(targetView);
            }
        });
        return flash;
    }

    private static void applyBrightnessScale(@NonNull ImageView imageView, float scale) {
        if (scale <= 1.01f) {
            imageView.clearColorFilter();
            return;
        }
        ColorMatrix cm = new ColorMatrix(new float[]{
                scale, 0f, 0f, 0f, 0f,
                0f, scale, 0f, 0f, 0f,
                0f, 0f, scale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
        });
        imageView.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    private static void clearBrightnessFilter(@NonNull View targetView) {
        if (targetView instanceof ImageView) {
            ((ImageView) targetView).clearColorFilter();
        }
    }

    private static void spawnSignatureParticles(
            @NonNull View targetView,
            @Nullable String reactionKey,
            long durationMs,
            boolean subtleHover
    ) {
        try {
            int w = targetView.getWidth();
            int h = targetView.getHeight();
            if (w <= 0 || h <= 0) {
                ViewGroup.LayoutParams lp = targetView.getLayoutParams();
                if (lp != null && lp.width > 0 && lp.height > 0) {
                    w = lp.width;
                    h = lp.height;
                } else {
                    return;
                }
            }

            final float density = targetView.getResources().getDisplayMetrics().density;
            final MotionStyle style = resolveStyleForKey(reactionKey);
            final List<ParticleSpec> particles = subtleHover
                    ? buildHoverParticles(reactionKey, density)
                    : buildSignatureParticles(style, density, w, h);

            final View rootView = targetView.getRootView();
            final boolean useRootOverlay = targetView.isAttachedToWindow() && (rootView instanceof ViewGroup) && rootView.getWidth() > 0;
            final ViewOverlay targetOverlay = useRootOverlay ? rootView.getOverlay() : targetView.getOverlay();

            final float originX;
            final float originY;
            final int overlayWidth;
            final int overlayHeight;

            if (useRootOverlay) {
                int[] targetLoc = new int[2];
                int[] rootLoc = new int[2];
                targetView.getLocationInWindow(targetLoc);
                rootView.getLocationInWindow(rootLoc);
                originX = (targetLoc[0] - rootLoc[0]) + (w * 0.5f);
                originY = (targetLoc[1] - rootLoc[1]) + (h * 0.5f);
                overlayWidth = rootView.getWidth();
                overlayHeight = rootView.getHeight();
            } else {
                originX = w * 0.5f;
                originY = h * 0.5f;
                overlayWidth = w;
                overlayHeight = h;
            }

            final SignatureParticlesOverlayDrawable drawable = new SignatureParticlesOverlayDrawable(
                    overlayWidth,
                    overlayHeight,
                    originX,
                    originY,
                    w,
                    h,
                    density,
                    style,
                    !subtleHover,
                    particles
            );
            targetOverlay.add(drawable);

            final float totalDurationSec = durationMs / 1000f;
            ValueAnimator anim = ValueAnimator.ofFloat(0f, totalDurationSec);
            anim.setDuration(durationMs);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> drawable.setElapsedSec((float) a.getAnimatedValue()));
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        targetOverlay.remove(drawable);
                    } catch (Exception ignored) {
                    }
                    ACTIVE_PARTICLE_ANIMATORS.remove(targetView);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    try {
                        targetOverlay.remove(drawable);
                    } catch (Exception ignored) {
                    }
                    ACTIVE_PARTICLE_ANIMATORS.remove(targetView);
                }
            });
            ACTIVE_PARTICLE_ANIMATORS.put(targetView, anim);
            anim.start();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static List<ParticleSpec> buildHoverParticles(@Nullable String reactionKey, float density) {
        List<ParticleSpec> list = new ArrayList<>(6);
        int primary = resolvePrimaryAccentColor(reactionKey);
        int secondary = resolveSecondaryAccentColor(reactionKey);
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 * i) / 6.0;
            float spd = (24f + (i % 2) * 10f) * density;
            list.add(ParticleSpec.star(
                    0f,
                    0f,
                    (float) (Math.cos(angle) * spd),
                    (float) (Math.sin(angle) * spd),
                    0f,
                    0.32f,
                    0f,
                    (4.5f + (i % 2) * 1.5f) * density,
                    i % 2 == 0 ? primary : secondary
            ));
        }
        return list;
    }

    @NonNull
    private static List<ParticleSpec> buildSignatureParticles(
            @NonNull MotionStyle style,
            float density,
            int viewW,
            int viewH
    ) {
        List<ParticleSpec> list = new ArrayList<>(14);
        Random rng = new Random(style.ordinal() * 1337L + viewW);

        switch (style) {
            case HEARTBEAT_PULSE: {
                // Pura vida: 10 golden & cyan 4-point stars bursting radially
                int[] colors = {
                        Color.parseColor("#FFD54F"),
                        Color.parseColor("#00E5FF"),
                        Color.parseColor("#FFF59D"),
                        Color.parseColor("#4DD0E1")
                };
                for (int i = 0; i < 10; i++) {
                    double angle = (Math.PI * 2.0 * i) / 10.0 + (rng.nextDouble() - 0.5) * 0.3;
                    double spd = (48.0 + rng.nextDouble() * 44.0) * density;
                    float size = (7.0f + rng.nextFloat() * 4.0f) * density;
                    list.add(ParticleSpec.star(
                            0f,
                            0f,
                            (float) (Math.cos(angle) * spd),
                            (float) (Math.sin(angle) * spd) - 12f * density,
                            35f * density,
                            1.05f,
                            0f,
                            size,
                            colors[i % colors.length]
                    ));
                }
                break;
            }
            case FLOATING_SWAY: {
                // Buena vibra: 8 orbiting stars around the icon
                for (int i = 0; i < 8; i++) {
                    int color = (i % 2 == 0)
                            ? Color.parseColor("#FFE082")
                            : Color.parseColor("#CE93D8");
                    float orbitR = (22f + (i % 2) * 7f) * density;
                    float orbitAngle = (float) ((Math.PI * 2.0 * i) / 8.0);
                    list.add(ParticleSpec.orbitStar(
                            orbitR,
                            orbitAngle,
                            7.5f,
                            1.15f,
                            6.5f * density,
                            color
                    ));
                }
                break;
            }
            case VOLCANO_ERUPT: {
                // ¡Qué rajado!: 4 rising white smoke puffs + 9 incandescent glowing embers
                for (int i = 0; i < 4; i++) {
                    list.add(ParticleSpec.smoke(
                            ((rng.nextFloat() - 0.5f) * 8f) * density,
                            -8f * density,
                            ((rng.nextFloat() - 0.5f) * 14f) * density,
                            (-34f - i * 8f) * density,
                            -6f * density,
                            1.15f,
                            0f,
                            (11f + i * 2.5f) * density,
                            Color.parseColor("#ECEFF1")
                    ));
                }
                for (int i = 0; i < 9; i++) {
                    int emberColor = (i % 2 == 0)
                            ? Color.parseColor("#FF9100")
                            : Color.parseColor("#FFEA00");
                    list.add(ParticleSpec.ember(
                            ((rng.nextFloat() - 0.5f) * 10f) * density,
                            -6f * density,
                            ((rng.nextFloat() - 0.5f) * 42f) * density,
                            (-52f - rng.nextFloat() * 35f) * density,
                            22f * density,
                            1.10f,
                            0f,
                            (4f + rng.nextFloat() * 2.5f) * density,
                            emberColor
                    ));
                }
                break;
            }
            case EXCITED_HOP: {
                // ¡Qué chiva!: diagonal lens glint sweep + crisp corner white star
                list.add(ParticleSpec.star(
                        7f * density,
                        -4f * density,
                        8f * density,
                        -10f * density,
                        0f,
                        0.95f,
                        0f,
                        10f * density,
                        Color.WHITE
                ));
                break;
            }
            case AFFIRMATIVE_SNAP: {
                // ¡De una!: 2 concentric red-coral radar rings staggered by 0.22s
                list.add(ParticleSpec.ring(
                        0f,
                        5f * density,
                        1.05f,
                        0f,
                        38f * density,
                        Color.parseColor("#FF5252")
                ));
                list.add(ParticleSpec.ring(
                        0f,
                        5f * density,
                        1.05f,
                        0.22f,
                        38f * density,
                        Color.parseColor("#FF5252")
                ));
                break;
            }
            case DROOP_SIGH: {
                // ¡Qué agüevado!: 3 falling teardrop raindrops staggered by 0.14s
                float[] offsets = {-6f, 0f, 6f};
                for (int i = 0; i < offsets.length; i++) {
                    list.add(ParticleSpec.raindrop(
                            offsets[i] * density,
                            6f * density,
                            0f,
                            36f * density,
                            48f * density,
                            0.95f,
                            i * 0.14f,
                            5.5f * density,
                            Color.parseColor("#38BDF8")
                    ));
                }
                break;
            }
            case FIERY_SHAKE:
            default: {
                // ¡Qué chicha!: 2 diagonal jets of white steam (3 staggered puffs each side)
                int[] dirs = {-1, 1};
                for (int dir : dirs) {
                    for (int j = 0; j < 3; j++) {
                        list.add(ParticleSpec.smoke(
                                dir * 10f * density,
                                -4f * density,
                                dir * (44f + j * 14f) * density,
                                (-32f - j * 10f) * density,
                                -8f * density,
                                1.0f,
                                j * 0.10f,
                                (9f + j * 2f) * density,
                                Color.WHITE
                        ));
                    }
                }
                break;
            }
        }
        return list;
    }

    private static void clearIdleAnimationOnly(@NonNull View targetView) {
        AnimatorSet existing = ACTIVE_ANIMATORS.remove(targetView);
        ACTIVE_STYLES.remove(targetView);
        if (existing != null) {
            for (Animator child : existing.getChildAnimations()) {
                child.cancel();
            }
            existing.cancel();
        }
        View.OnLayoutChangeListener listener = PIVOT_LAYOUT_LISTENERS.remove(targetView);
        if (listener != null) {
            targetView.removeOnLayoutChangeListener(listener);
        }
    }

    public static void clearAnimation(@NonNull View targetView) {
        clearIdleAnimationOnly(targetView);
        cancelBurstAnimator(targetView);
        cancelParticleAnimator(targetView);
        clearBrightnessFilter(targetView);
        resetTransforms(targetView);
    }

    private static void cancelBurstAnimator(@NonNull View targetView) {
        AnimatorSet burst = ACTIVE_BURST_ANIMATORS.remove(targetView);
        if (burst != null) {
            for (Animator child : burst.getChildAnimations()) {
                child.cancel();
            }
            burst.cancel();
        }
    }

    private static void cancelParticleAnimator(@NonNull View targetView) {
        ValueAnimator particleAnim = ACTIVE_PARTICLE_ANIMATORS.remove(targetView);
        if (particleAnim != null) {
            particleAnim.cancel();
        }
    }

    public static void resetTransforms(@NonNull View targetView) {
        targetView.setScaleX(1f);
        targetView.setScaleY(1f);
        targetView.setTranslationX(0f);
        targetView.setTranslationY(0f);
        targetView.setRotation(0f);
    }

    @VisibleForTesting
    public static boolean hasActiveAnimation(@NonNull View targetView) {
        AnimatorSet set = ACTIVE_ANIMATORS.get(targetView);
        return set != null && (set.isStarted() || set.isRunning());
    }

    @VisibleForTesting
    @Nullable
    public static MotionStyle getActiveStyle(@NonNull View targetView) {
        return ACTIVE_STYLES.get(targetView);
    }

    private static void ensureDetachCleanupHook(@NonNull View targetView) {
        if (Boolean.TRUE.equals(DETACH_LISTENERS.get(targetView))) {
            return;
        }
        DETACH_LISTENERS.put(targetView, Boolean.TRUE);
        targetView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                clearAnimation(v);
            }
        });
    }

    private static void attachPivotLayoutSync(@NonNull View targetView, @NonNull MotionStyle style) {
        View.OnLayoutChangeListener oldListener = PIVOT_LAYOUT_LISTENERS.remove(targetView);
        if (oldListener != null) {
            targetView.removeOnLayoutChangeListener(oldListener);
        }
        View.OnLayoutChangeListener newListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        applyPivotForStyle(v, style);
        PIVOT_LAYOUT_LISTENERS.put(targetView, newListener);
        targetView.addOnLayoutChangeListener(newListener);
    }

    private static void applyPivotForStyle(@NonNull View target, @NonNull MotionStyle style) {
        float w = target.getWidth();
        float h = target.getHeight();
        if (w <= 0f || h <= 0f) {
            ViewGroup.LayoutParams lp = target.getLayoutParams();
            if (lp != null && lp.width > 0 && lp.height > 0) {
                w = lp.width;
                h = lp.height;
            } else {
                return;
            }
        }
        switch (style) {
            case HEARTBEAT_PULSE:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.62f);
                break;
            case FLOATING_SWAY:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.52f);
                break;
            case VOLCANO_ERUPT:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.90f);
                break;
            case EXCITED_HOP:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.72f);
                break;
            case AFFIRMATIVE_SNAP:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.88f);
                break;
            case DROOP_SIGH:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.55f);
                break;
            case FIERY_SHAKE:
                target.setPivotX(w * 0.50f);
                target.setPivotY(h * 0.60f);
                break;
        }
    }

    @NonNull
    private static AnimatorSet buildAnimatorSet(
            @NonNull View target,
            @NonNull MotionStyle style,
            float density,
            float amp
    ) {
        AnimatorSet set = new AnimatorSet();
        PathInterpolator organicEase = new PathInterpolator(0.37f, 0f, 0.22f, 1f);

        switch (style) {
            case HEARTBEAT_PULSE: {
                long duration = 1600L;
                ObjectAnimator body = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_X,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.14f, 1.00f + 0.12f * amp),
                                        Keyframe.ofFloat(0.28f, 0.96f),
                                        Keyframe.ofFloat(0.42f, 1.00f + 0.09f * amp),
                                        Keyframe.ofFloat(0.62f, 1.00f),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_Y,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.14f, 1.00f + 0.10f * amp),
                                        Keyframe.ofFloat(0.28f, 1.02f),
                                        Keyframe.ofFloat(0.42f, 1.00f + 0.11f * amp),
                                        Keyframe.ofFloat(0.62f, 1.00f),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.ROTATION,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.16f, -5.5f * amp),
                                        Keyframe.ofFloat(0.38f, 6.0f * amp),
                                        Keyframe.ofFloat(0.62f, 0f),
                                        Keyframe.ofFloat(1.00f, 0f)
                                )
                        ),
                        duration,
                        organicEase
                );
                set.play(body);
                break;
            }
            case FLOATING_SWAY: {
                long cycleDuration = 2200L;
                ObjectAnimator xOrb = loop(
                        ObjectAnimator.ofFloat(
                                target,
                                View.TRANSLATION_X,
                                0f, 1.5f * density * amp, 0f, -1.5f * density * amp, 0f
                        ),
                        cycleDuration,
                        organicEase
                );
                ObjectAnimator yOrb = loop(
                        ObjectAnimator.ofFloat(
                                target,
                                View.TRANSLATION_Y,
                                0f, -2.4f * density * amp, 0f, 1.2f * density * amp, 0f
                        ),
                        cycleDuration / 2L,
                        organicEase
                );
                ObjectAnimator breath = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f, 1f + 0.06f * amp, 0.97f),
                                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.02f, 1f + 0.05f * amp, 1.02f)
                        ),
                        1500L,
                        organicEase
                );
                set.playTogether(xOrb, yOrb, breath);
                break;
            }
            case VOLCANO_ERUPT: {
                // Gentle simmering volcano idle pulse + mini magma tremor
                long duration = 1750L;
                ObjectAnimator erupt = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_X,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.18f, 1.00f + 0.08f * amp),
                                        Keyframe.ofFloat(0.36f, 0.95f),
                                        Keyframe.ofFloat(0.54f, 1.00f + 0.10f * amp),
                                        Keyframe.ofFloat(0.76f, 1.00f),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_Y,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.18f, 0.94f),
                                        Keyframe.ofFloat(0.36f, 1.00f + 0.11f * amp),
                                        Keyframe.ofFloat(0.54f, 0.96f),
                                        Keyframe.ofFloat(0.76f, 1.00f),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.TRANSLATION_X,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.24f, -1.1f * density * amp),
                                        Keyframe.ofFloat(0.36f, 1.1f * density * amp),
                                        Keyframe.ofFloat(0.48f, -0.8f * density * amp),
                                        Keyframe.ofFloat(0.62f, 0f),
                                        Keyframe.ofFloat(1.00f, 0f)
                                )
                        ),
                        duration,
                        organicEase
                );
                set.play(erupt);
                break;
            }
            case EXCITED_HOP: {
                long duration = 1850L;
                ObjectAnimator chill = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.ROTATION,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.26f, -5.0f * amp),
                                        Keyframe.ofFloat(0.62f, 4.0f * amp),
                                        Keyframe.ofFloat(1.00f, 0f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.TRANSLATION_Y,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.34f, -2.2f * density * amp),
                                        Keyframe.ofFloat(0.68f, -1.5f * density * amp),
                                        Keyframe.ofFloat(1.00f, 0f)
                                )
                        ),
                        duration,
                        organicEase
                );
                set.play(chill);
                break;
            }
            case AFFIRMATIVE_SNAP: {
                long duration = 1520L;
                ObjectAnimator snap = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.TRANSLATION_Y,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.22f, -3.5f * density * amp),
                                        Keyframe.ofFloat(0.40f, 0.5f * density * amp),
                                        Keyframe.ofFloat(0.56f, -1.5f * density * amp),
                                        Keyframe.ofFloat(0.74f, 0f),
                                        Keyframe.ofFloat(1.00f, 0f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_Y,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.22f, 1.00f + 0.08f * amp),
                                        Keyframe.ofFloat(0.40f, 0.92f),
                                        Keyframe.ofFloat(0.56f, 1.04f),
                                        Keyframe.ofFloat(0.74f, 1.00f),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                )
                        ),
                        duration,
                        organicEase
                );
                set.play(snap);
                break;
            }
            case DROOP_SIGH: {
                long duration = 2100L;
                ObjectAnimator droop = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.TRANSLATION_Y,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.38f, 2.5f * density * amp),
                                        Keyframe.ofFloat(0.68f, 2.8f * density * amp),
                                        Keyframe.ofFloat(1.00f, 0f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_X,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.42f, 1.00f + 0.07f * amp),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_Y,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.42f, 1.00f - 0.07f * amp),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                )
                        ),
                        duration,
                        organicEase
                );
                set.play(droop);
                break;
            }
            case FIERY_SHAKE: {
                long duration = 1280L;
                ObjectAnimator flare = loop(
                        ObjectAnimator.ofPropertyValuesHolder(
                                target,
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_X,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.24f, 1.00f + 0.10f * amp),
                                        Keyframe.ofFloat(0.54f, 1.00f + 0.05f * amp),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.SCALE_Y,
                                        Keyframe.ofFloat(0.00f, 1.00f),
                                        Keyframe.ofFloat(0.24f, 1.00f + 0.10f * amp),
                                        Keyframe.ofFloat(0.54f, 1.00f + 0.05f * amp),
                                        Keyframe.ofFloat(1.00f, 1.00f)
                                ),
                                PropertyValuesHolder.ofKeyframe(
                                        View.TRANSLATION_X,
                                        Keyframe.ofFloat(0.00f, 0f),
                                        Keyframe.ofFloat(0.18f, -1.6f * density * amp),
                                        Keyframe.ofFloat(0.28f, 1.8f * density * amp),
                                        Keyframe.ofFloat(0.38f, -1.5f * density * amp),
                                        Keyframe.ofFloat(0.48f, 1.3f * density * amp),
                                        Keyframe.ofFloat(0.62f, 0f),
                                        Keyframe.ofFloat(1.00f, 0f)
                                )
                        ),
                        duration,
                        new LinearInterpolator()
                );
                set.play(flare);
                break;
            }
        }
        return set;
    }

    @NonNull
    private static ObjectAnimator loop(
            @NonNull ObjectAnimator animator,
            long durationMs,
            @NonNull TimeInterpolator interpolator
    ) {
        animator.setDuration(durationMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(interpolator);
        return animator;
    }

    private enum ParticleShape {
        STAR,
        SMOKE,
        EMBER,
        RING,
        RAINDROP
    }

    private static final class ParticleSpec {
        final ParticleShape shape;
        final float startX;
        final float startY;
        final float vx;
        final float vy;
        final float gravity;
        final float lifeSec;
        final float delaySec;
        final float size;
        final float maxRadius;
        final int color;
        final float orbitRadius;
        final float orbitStartAngle;
        final float orbitSpeed;

        private ParticleSpec(
                ParticleShape shape,
                float startX,
                float startY,
                float vx,
                float vy,
                float gravity,
                float lifeSec,
                float delaySec,
                float size,
                float maxRadius,
                int color,
                float orbitRadius,
                float orbitStartAngle,
                float orbitSpeed
        ) {
            this.shape = shape;
            this.startX = startX;
            this.startY = startY;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.lifeSec = lifeSec;
            this.delaySec = delaySec;
            this.size = size;
            this.maxRadius = maxRadius;
            this.color = color;
            this.orbitRadius = orbitRadius;
            this.orbitStartAngle = orbitStartAngle;
            this.orbitSpeed = orbitSpeed;
        }

        static ParticleSpec star(float x, float y, float vx, float vy, float g, float life, float delay, float size, int color) {
            return new ParticleSpec(ParticleShape.STAR, x, y, vx, vy, g, life, delay, size, 0f, color, 0f, 0f, 0f);
        }

        static ParticleSpec orbitStar(float r, float startAngle, float speed, float life, float size, int color) {
            return new ParticleSpec(ParticleShape.STAR, 0f, 0f, 0f, 0f, 0f, life, 0f, size, 0f, color, r, startAngle, speed);
        }

        static ParticleSpec smoke(float x, float y, float vx, float vy, float g, float life, float delay, float size, int color) {
            return new ParticleSpec(ParticleShape.SMOKE, x, y, vx, vy, g, life, delay, size, 0f, color, 0f, 0f, 0f);
        }

        static ParticleSpec ember(float x, float y, float vx, float vy, float g, float life, float delay, float size, int color) {
            return new ParticleSpec(ParticleShape.EMBER, x, y, vx, vy, g, life, delay, size, 0f, color, 0f, 0f, 0f);
        }

        static ParticleSpec ring(float x, float y, float life, float delay, float maxRadius, int color) {
            return new ParticleSpec(ParticleShape.RING, x, y, 0f, 0f, 0f, life, delay, 12f, maxRadius, color, 0f, 0f, 0f);
        }

        static ParticleSpec raindrop(float x, float y, float vx, float vy, float g, float life, float delay, float size, int color) {
            return new ParticleSpec(ParticleShape.RAINDROP, x, y, vx, vy, g, life, delay, size, 0f, color, 0f, 0f, 0f);
        }
    }

    /**
     * Pure-Canvas ViewOverlay drawable executing all 7 Pulso VYB particle effects
     * (4-point stars, orbiting stars, rising smoke puffs, glowing embers, lens glint sweep,
     * concentric radar rings, teardrop raindrops, and side steam jets).
     */
    private static final class SignatureParticlesOverlayDrawable extends Drawable {
        private final float originX;
        private final float originY;
        private final int viewW;
        private final int viewH;
        private final float density;
        private final MotionStyle style;
        private final boolean fullSignature;
        private final List<ParticleSpec> particles;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path workPath = new Path();
        private float elapsedSec = 0f;

        SignatureParticlesOverlayDrawable(
                int overlayW,
                int overlayH,
                float originX,
                float originY,
                int viewW,
                int viewH,
                float density,
                MotionStyle style,
                boolean fullSignature,
                List<ParticleSpec> particles
        ) {
            this.originX = originX;
            this.originY = originY;
            this.viewW = viewW;
            this.viewH = viewH;
            this.density = density;
            this.style = style;
            this.fullSignature = fullSignature;
            this.particles = particles;
            setBounds(0, 0, overlayW, overlayH);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
        }

        void setElapsedSec(float elapsedSec) {
            this.elapsedSec = Math.max(0f, elapsedSec);
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            // Special diagonal sunglasses glint sweep for ¡Qué chiva!
            if (fullSignature && style == MotionStyle.EXCITED_HOP && elapsedSec >= 0.12f && elapsedSec <= 1.07f) {
                float gt = (elapsedSec - 0.12f) / 0.95f;
                float sweepOffset = (-0.52f + 1.04f * gt) * viewW;
                float glintAlpha = (float) Math.sin(gt * Math.PI) * 0.78f;
                float bandHalfH = viewH * 0.14f;
                float bandCenterY = originY - (viewH * 0.06f);

                workPath.reset();
                workPath.moveTo(originX + sweepOffset - 4f * density, bandCenterY - bandHalfH);
                workPath.lineTo(originX + sweepOffset + 3f * density, bandCenterY - bandHalfH);
                workPath.lineTo(originX + sweepOffset + 8f * density, bandCenterY + bandHalfH);
                workPath.lineTo(originX + sweepOffset + 1f * density, bandCenterY + bandHalfH);
                workPath.close();

                fillPaint.setShader(null);
                fillPaint.setColor(Color.WHITE);
                fillPaint.setAlpha(Math.round(255f * glintAlpha));
                canvas.drawPath(workPath, fillPaint);
            }

            for (int i = 0; i < particles.size(); i++) {
                ParticleSpec p = particles.get(i);
                float age = elapsedSec - p.delaySec;
                if (age < 0f || age >= p.lifeSec) {
                    continue;
                }
                float progress = age / p.lifeSec;

                if (p.orbitRadius > 0f) {
                    float angle = p.orbitStartAngle + (p.orbitSpeed * age);
                    float r = p.orbitRadius * (0.4f + 0.7f * (float) Math.sin(progress * Math.PI));
                    float px = originX + (float) Math.cos(angle) * r;
                    float py = originY + (float) Math.sin(angle) * r;
                    float alpha = (float) Math.sin(progress * Math.PI);
                    float scale = 0.7f + 0.5f * alpha;
                    drawFourPointStar(canvas, px, py, p.size * 0.5f * scale, p.color, alpha);
                } else if (p.shape == ParticleShape.RING) {
                    float radius = (6f * density) + (p.maxRadius - 6f * density) * progress;
                    float alpha = (1f - progress) * 0.85f;
                    strokePaint.setColor(p.color);
                    strokePaint.setAlpha(clampAlpha(alpha));
                    strokePaint.setStrokeWidth(Math.max(1.2f * density, 2.2f * density * (1f - progress * 0.45f)));
                    canvas.drawCircle(originX + p.startX, originY + p.startY, radius, strokePaint);
                } else {
                    float px = originX + p.startX + (p.vx * age);
                    float py = originY + p.startY + (p.vy * age) + (0.5f * p.gravity * age * age);
                    float alpha = progress < 0.2f
                            ? (progress / 0.2f)
                            : Math.max(0f, 1f - ((progress - 0.2f) / 0.8f));
                    float scale = p.shape == ParticleShape.SMOKE
                            ? (0.6f + progress * 0.9f)
                            : (1f - progress * 0.35f);

                    switch (p.shape) {
                        case STAR:
                            drawFourPointStar(canvas, px, py, p.size * 0.5f * scale, p.color, alpha);
                            break;
                        case SMOKE: {
                            float radius = Math.max(1f, (p.size * 0.5f) * scale);
                            int a = clampAlpha(alpha * 0.88f);
                            int c0 = Color.argb(a, Color.red(p.color), Color.green(p.color), Color.blue(p.color));
                            int c1 = Color.argb(0, Color.red(p.color), Color.green(p.color), Color.blue(p.color));
                            fillPaint.setShader(new RadialGradient(px, py, radius, c0, c1, Shader.TileMode.CLAMP));
                            canvas.drawCircle(px, py, radius, fillPaint);
                            fillPaint.setShader(null);
                            break;
                        }
                        case EMBER: {
                            float radius = Math.max(1f, (p.size * 0.5f) * scale);
                            int glowA = clampAlpha(alpha * 0.45f);
                            fillPaint.setShader(null);
                            fillPaint.setColor(p.color);
                            fillPaint.setAlpha(glowA);
                            canvas.drawCircle(px, py, radius * 2.1f, fillPaint);
                            fillPaint.setAlpha(clampAlpha(alpha));
                            canvas.drawCircle(px, py, radius, fillPaint);
                            break;
                        }
                        case RAINDROP: {
                            drawRaindrop(canvas, px, py, p.size * scale, p.color, alpha);
                            break;
                        }
                        case RING:
                            break;
                    }
                }
            }
        }

        private void drawFourPointStar(
                @NonNull Canvas canvas,
                float cx,
                float cy,
                float r,
                int color,
                float alpha
        ) {
            if (r <= 0.2f || alpha <= 0.01f) return;
            float inner = r * 0.28f;
            workPath.reset();
            workPath.moveTo(cx, cy - r);
            workPath.lineTo(cx + inner, cy - inner);
            workPath.lineTo(cx + r, cy);
            workPath.lineTo(cx + inner, cy + inner);
            workPath.lineTo(cx, cy + r);
            workPath.lineTo(cx - inner, cy + inner);
            workPath.lineTo(cx - r, cy);
            workPath.lineTo(cx - inner, cy - inner);
            workPath.close();

            fillPaint.setShader(null);
            fillPaint.setColor(color);
            fillPaint.setAlpha(clampAlpha(alpha));
            canvas.drawPath(workPath, fillPaint);
        }

        private void drawRaindrop(
                @NonNull Canvas canvas,
                float cx,
                float cy,
                float width,
                int color,
                float alpha
        ) {
            if (width <= 0.5f || alpha <= 0.01f) return;
            float halfW = width * 0.5f;
            float topY = cy - width * 0.7f;
            float bulbY = cy + width * 0.35f;
            workPath.reset();
            workPath.moveTo(cx, topY);
            workPath.quadTo(cx + halfW * 1.15f, cy, cx + halfW, bulbY);
            workPath.arcTo(
                    cx - halfW,
                    bulbY - halfW,
                    cx + halfW,
                    bulbY + halfW,
                    0f,
                    180f,
                    false
            );
            workPath.quadTo(cx - halfW * 1.15f, cy, cx, topY);
            workPath.close();

            fillPaint.setShader(null);
            fillPaint.setColor(color);
            fillPaint.setAlpha(clampAlpha(alpha));
            canvas.drawPath(workPath, fillPaint);
        }

        private static int clampAlpha(float alpha) {
            return Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
