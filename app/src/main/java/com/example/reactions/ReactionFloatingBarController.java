package com.example.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;

/**
 * "Pulso VYB" floating reaction bar controller:
 * 1) Spring cascade entrance (25ms stagger per reaction icon, damped spring overshoot)
 * 2) Continuous Gaussian magnetic scrubbing (touch & cursor hover) with -14dp lift, 1.45x peak scale, neighbor push, and velocity-tilted floating label
 * 3) Parabolic quadratic-Bézier flight of selected icon onto the message bubble corner
 */
public final class ReactionFloatingBarController {

    public interface OnReactionSelectedListener {
        void onReactionSelected(@NonNull VybReaction reaction);
    }

    private static final int DARK_BAR_BG = Color.argb(236, 17, 28, 54);
    private static final int DARK_BAR_BORDER = Color.argb(128, 96, 165, 250);
    private static final int DARK_LABEL_BG = Color.argb(248, 22, 36, 70);
    private static final int DARK_LABEL_BORDER = Color.argb(170, 56, 189, 248);
    private static final int DARK_LABEL_TEXT = Color.parseColor("#F8FAFC");
    private static final int DARK_ACTIVE_SLOT_GLOW = Color.argb(46, 56, 189, 248);
    private static final int DARK_ACTIVE_SLOT_BORDER = Color.parseColor("#38BDF8");

    private static final int LIGHT_BAR_BG = Color.argb(246, 255, 255, 255);
    private static final int LIGHT_BAR_BORDER = Color.argb(54, 15, 23, 42);
    private static final int LIGHT_LABEL_BG = Color.argb(250, 255, 255, 255);
    private static final int LIGHT_LABEL_BORDER = Color.argb(90, 37, 99, 235);
    private static final int LIGHT_LABEL_TEXT = Color.parseColor("#0F172A");
    private static final int LIGHT_ACTIVE_SLOT_GLOW = Color.argb(28, 37, 99, 235);
    private static final int LIGHT_ACTIVE_SLOT_BORDER = Color.parseColor("#2563EB");

    private static final long HAPTIC_DURATION_MS = 20L;
    private static final long CASCADE_STAGGER_MS = 25L;
    private static final long CASCADE_SLOT_DURATION_MS = 340L;
    private static final long PARABOLIC_FLIGHT_DURATION_MS = 360L;

    public static final String TAG_FLOATING_LABEL = "vyb_reaction_floating_label";
    public static final String TAG_BAR_CONTAINER = "vyb_reaction_bar_container";

    private final Context context;
    @Nullable
    private PopupWindow activePopup;
    @Nullable
    private View activePopupRoot;
    @Nullable
    private ValueAnimator activePhysicsTicker;
    private final List<ImageView> activeIconViews = new ArrayList<>();

    public ReactionFloatingBarController(@NonNull Context context) {
        this.context = context;
    }

    public boolean isShowing() {
        return activePopup != null && activePopup.isShowing();
    }

    @VisibleForTesting
    @Nullable
    public View getPopupRootForTest() {
        return activePopupRoot;
    }

    public void dismiss() {
        if (activePhysicsTicker != null) {
            activePhysicsTicker.cancel();
            activePhysicsTicker = null;
        }
        for (ImageView iconView : activeIconViews) {
            ReactionAnimatorHelper.clearAnimation(iconView);
        }
        activeIconViews.clear();
        if (activePopup != null) {
            try {
                if (activePopup.isShowing()) {
                    activePopup.dismiss();
                }
            } catch (Exception ignored) {
            }
            activePopup = null;
        }
        activePopupRoot = null;
    }

    public void showAboveAnchor(
            @NonNull View rootAnchorView,
            @NonNull Rect anchorBoundsInWindow,
            @NonNull OnReactionSelectedListener listener
    ) {
        showAboveAnchor(rootAnchorView, anchorBoundsInWindow, true, null, listener, null);
    }

    public void showAboveAnchor(
            @NonNull View rootAnchorView,
            @NonNull Rect anchorBoundsInWindow,
            boolean isDarkTheme,
            @Nullable String activeReactionKey,
            @NonNull OnReactionSelectedListener listener,
            @Nullable Runnable onDismissCallback
    ) {
        showAboveAnchor(
                rootAnchorView,
                anchorBoundsInWindow,
                isDarkTheme,
                activeReactionKey,
                null,
                true,
                listener,
                onDismissCallback
        );
    }

    @SuppressLint("ClickableViewAccessibility")
    public void showAboveAnchor(
            @NonNull View rootAnchorView,
            @NonNull Rect anchorBoundsInWindow,
            boolean isDarkTheme,
            @Nullable String activeReactionKey,
            @Nullable Rect flightDestinationInWindow,
            boolean focusablePopup,
            @NonNull OnReactionSelectedListener listener,
            @Nullable Runnable onDismissCallback
    ) {
        dismiss();

        List<VybReaction> reactions = ReactionCatalog.getAll();
        if (reactions.isEmpty()) {
            return;
        }

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density;
        int screenWidth = metrics.widthPixels;

        int maxPerSlot = Math.max(dpToPx(32f, density), (screenWidth - dpToPx(84f, density)) / reactions.size());
        int slotPadPx = dpToPx(3.5f, density);
        int iconSizePx = Math.min(dpToPx(35f, density), Math.max(dpToPx(26f, density), maxPerSlot - (slotPadPx * 2)));
        int slotSizePx = iconSizePx + (slotPadPx * 2);
        int iconGapPx = dpToPx(3f, density);
        int barPadHorizontalPx = dpToPx(9f, density);
        int barPadVerticalPx = dpToPx(6.5f, density);
        int topSpaceForHoverPx = dpToPx(64f, density);
        int rootSidePadPx = dpToPx(12f, density);
        int barCornerPx = dpToPx(28f, density);
        int borderWidthPx = Math.max(1, dpToPx(1f, density));

        int barBgColor = isDarkTheme ? DARK_BAR_BG : LIGHT_BAR_BG;
        int barBorderColor = isDarkTheme ? DARK_BAR_BORDER : LIGHT_BAR_BORDER;
        int labelBgColor = isDarkTheme ? DARK_LABEL_BG : LIGHT_LABEL_BG;
        int labelBorderColor = isDarkTheme ? DARK_LABEL_BORDER : LIGHT_LABEL_BORDER;
        int labelTextColor = isDarkTheme ? DARK_LABEL_TEXT : LIGHT_LABEL_TEXT;
        int activeSlotGlowColor = isDarkTheme ? DARK_ACTIVE_SLOT_GLOW : LIGHT_ACTIVE_SLOT_GLOW;
        int activeSlotBorderColor = isDarkTheme ? DARK_ACTIVE_SLOT_BORDER : LIGHT_ACTIVE_SLOT_BORDER;

        FrameLayout rootContainer = new FrameLayout(context);
        rootContainer.setClipChildren(false);
        rootContainer.setClipToPadding(false);
        rootContainer.setPadding(rootSidePadPx, 0, rootSidePadPx, dpToPx(8f, density));

        // Floating glass Spanish pill tooltip shown while touching/sliding over any reaction
        TextView floatingLabelView = new TextView(context);
        floatingLabelView.setTag(TAG_FLOATING_LABEL);
        floatingLabelView.setTextColor(labelTextColor);
        floatingLabelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        floatingLabelView.setTypeface(Typeface.DEFAULT_BOLD);
        floatingLabelView.setSingleLine(true);
        floatingLabelView.setPadding(
                dpToPx(12f, density),
                dpToPx(5.5f, density),
                dpToPx(12f, density),
                dpToPx(5.5f, density)
        );
        GradientDrawable labelBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        labelBgColor,
                        isDarkTheme ? Color.argb(248, 16, 27, 54) : Color.argb(250, 245, 248, 255)
                }
        );
        labelBg.setCornerRadius(dpToPx(16f, density));
        labelBg.setStroke(borderWidthPx, labelBorderColor);
        floatingLabelView.setBackground(labelBg);
        floatingLabelView.setElevation(dpToPx(12f, density));
        floatingLabelView.setVisibility(View.INVISIBLE);

        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelLp.gravity = Gravity.TOP | Gravity.START;
        labelLp.topMargin = dpToPx(2f, density);
        rootContainer.addView(floatingLabelView, labelLp);

        // VYB frosted capsule bar with unified touch + cursor tracking
        TouchTrackingBarLayout barContainer = new TouchTrackingBarLayout(context);
        barContainer.setTag(TAG_BAR_CONTAINER);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        barContainer.setGravity(Gravity.CENTER_VERTICAL);
        barContainer.setClipChildren(false);
        barContainer.setClipToPadding(false);
        barContainer.setClickable(true);
        barContainer.setFocusable(true);
        barContainer.setPadding(barPadHorizontalPx, barPadVerticalPx, barPadHorizontalPx, barPadVerticalPx);

        GradientDrawable barBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        barBgColor,
                        isDarkTheme ? Color.argb(236, 12, 20, 40) : Color.argb(246, 244, 248, 255)
                }
        );
        barBg.setCornerRadius(barCornerPx);
        barBg.setStroke(borderWidthPx, barBorderColor);
        barContainer.setBackground(barBg);
        barContainer.setElevation(dpToPx(14f, density));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barLp.gravity = Gravity.TOP | Gravity.START;
        barLp.topMargin = topSpaceForHoverPx;
        rootContainer.addView(barContainer, barLp);

        activeIconViews.clear();
        List<View> slotViews = new ArrayList<>(reactions.size());
        List<View> slotHighlightViews = new ArrayList<>(reactions.size());
        List<ImageView> iconViews = new ArrayList<>(reactions.size());

        for (int i = 0; i < reactions.size(); i++) {
            VybReaction reaction = reactions.get(i);
            boolean isCurrentlySelected = activeReactionKey != null && activeReactionKey.equals(reaction.getKey());

            FrameLayout slotView = new FrameLayout(context);
            slotView.setClipChildren(false);
            slotView.setClipToPadding(false);
            slotView.setPadding(slotPadPx, slotPadPx, slotPadPx, slotPadPx);
            slotView.setContentDescription(reaction.getLabel());
            slotView.setClickable(false);
            slotView.setFocusable(false);
            // Anchor pivot at center-bottom matching Pulso VYB HTML transform-origin: center bottom
            slotView.setPivotX(slotSizePx * 0.5f);
            slotView.setPivotY((float) slotSizePx);

            if (isCurrentlySelected) {
                GradientDrawable selectedBg = new GradientDrawable();
                selectedBg.setShape(GradientDrawable.OVAL);
                selectedBg.setColor(activeSlotGlowColor);
                slotView.setBackground(selectedBg);
            }

            // Glass highlight pill behind hovered slot
            View hoverHighlight = new View(context);
            GradientDrawable hlBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            isDarkTheme ? Color.argb(38, 255, 255, 255) : Color.argb(34, 37, 99, 235),
                            isDarkTheme ? Color.argb(12, 255, 255, 255) : Color.argb(10, 37, 99, 235)
                    }
            );
            hlBg.setCornerRadius(dpToPx(14f, density));
            hlBg.setStroke(
                    Math.max(1, dpToPx(0.8f, density)),
                    isDarkTheme ? Color.argb(40, 255, 255, 255) : Color.argb(40, 37, 99, 235)
            );
            hoverHighlight.setBackground(hlBg);
            hoverHighlight.setAlpha(0f);
            FrameLayout.LayoutParams hlLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            slotView.addView(hoverHighlight, hlLp);

            ImageView iconView = new ImageView(context);
            iconView.setImageResource(reaction.getDrawableResId());
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setContentDescription(null);
            iconView.setClickable(false);
            FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
            imgLp.gravity = Gravity.CENTER;
            slotView.addView(iconView, imgLp);

            if (isCurrentlySelected) {
                View activeDot = new View(context);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(activeSlotBorderColor);
                activeDot.setBackground(dotBg);
                int dotSizePx = Math.max(4, dpToPx(4f, density));
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSizePx, dotSizePx);
                dotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                dotLp.bottomMargin = -dpToPx(3f, density);
                slotView.addView(activeDot, dotLp);
            }

            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(slotSizePx, slotSizePx);
            if (i < reactions.size() - 1) {
                slotLp.rightMargin = iconGapPx;
            }
            barContainer.addView(slotView, slotLp);
            slotViews.add(slotView);
            slotHighlightViews.add(hoverHighlight);
            iconViews.add(iconView);
            activeIconViews.add(iconView);
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        rootContainer.measure(widthSpec, heightSpec);

        int popupWidth = rootContainer.getMeasuredWidth();
        int popupHeight = rootContainer.getMeasuredHeight();

        final int slotCount = slotViews.size();
        final float[] targetScale = new float[slotCount];
        final float[] targetY = new float[slotCount];
        final float[] targetX = new float[slotCount];
        final float[] currentScale = new float[slotCount];
        final float[] currentY = new float[slotCount];
        final float[] currentX = new float[slotCount];
        final boolean[] cascadeDone = new boolean[slotCount];
        for (int i = 0; i < slotCount; i++) {
            targetScale[i] = 1f;
            currentScale[i] = 1f;
        }

        final MagneticScrubState scrubState = new MagneticScrubState();
        final long showStartUptime = SystemClock.uptimeMillis();
        final float sigmaPx = dpToPx(44f, density);
        final float maxLiftYPx = -dpToPx(14f, density);
        final float pushRadiusPx = dpToPx(95f, density);
        final float pushAmpPx = dpToPx(6.5f, density);

        // Physics ticker driving smooth 60fps Gaussian magnetic lerp + velocity-tilted label
        ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(1000L);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(anim -> {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, Math.max(0.004f, (now - scrubState.lastFrameUptime) / 1000f));
            scrubState.lastFrameUptime = now;

            float slotLerp = 1f - (float) Math.exp(-22.0 * dt);
            for (int i = 0; i < slotCount; i++) {
                if (!cascadeDone[i]) {
                    long elapsedForSlot = now - showStartUptime;
                    if (elapsedForSlot >= (i * CASCADE_STAGGER_MS + CASCADE_SLOT_DURATION_MS) || scrubState.pointerActive) {
                        cascadeDone[i] = true;
                        View slot = slotViews.get(i);
                        currentScale[i] = slot.getScaleX();
                        currentY[i] = slot.getTranslationY();
                        currentX[i] = slot.getTranslationX();
                    } else {
                        continue;
                    }
                }
                currentScale[i] += (targetScale[i] - currentScale[i]) * slotLerp;
                currentY[i] += (targetY[i] - currentY[i]) * slotLerp;
                currentX[i] += (targetX[i] - currentX[i]) * slotLerp;

                View slot = slotViews.get(i);
                slot.setScaleX(currentScale[i]);
                slot.setScaleY(currentScale[i]);
                slot.setTranslationY(currentY[i]);
                slot.setTranslationX(currentX[i]);
            }

            float labelLerp = 1f - (float) Math.exp(-24.0 * dt);
            if (floatingLabelView.getVisibility() == View.VISIBLE) {
                scrubState.labelX += (scrubState.targetLabelX - scrubState.labelX) * labelLerp;
                float targetTilt = Math.max(-8f, Math.min(8f, scrubState.pointerVelocity * 0.35f));
                scrubState.labelTilt += (targetTilt - scrubState.labelTilt) * labelLerp;
                scrubState.pointerVelocity *= (float) Math.pow(0.82, dt * 60f);

                floatingLabelView.setTranslationX(scrubState.labelX);
                floatingLabelView.setRotation(scrubState.labelTilt);
            }
        });
        activePhysicsTicker = ticker;

        final Runnable updateMagneticState = () -> {
            if (!scrubState.pointerActive) {
                for (int i = 0; i < slotCount; i++) {
                    targetScale[i] = 1f;
                    targetY[i] = 0f;
                    targetX[i] = 0f;
                    slotHighlightViews.get(i).animate().alpha(0f).setDuration(110L).start();
                }
                scrubState.hoveredIndex = -1;
                hideFloatingLabel(floatingLabelView);
                return;
            }

            int barW = barContainer.getWidth() > 0 ? barContainer.getWidth() : barContainer.getMeasuredWidth();
            int closestIdx = -1;
            float minDist = Float.MAX_VALUE;

            for (int i = 0; i < slotCount; i++) {
                View slot = slotViews.get(i);
                float baseCenter;
                if (slot.getWidth() > 0) {
                    baseCenter = ((slot.getLeft() + slot.getRight()) * 0.5f);
                } else {
                    float approxSlotW = (float) Math.max(1, barW) / slotCount;
                    baseCenter = (i + 0.5f) * approxSlotW;
                }
                float dist = scrubState.pointerX - baseCenter;
                float absDist = Math.abs(dist);
                if (absDist < minDist) {
                    minDist = absDist;
                    closestIdx = i;
                }

                float g = (float) Math.exp(-(dist * dist) / (2.0 * sigmaPx * sigmaPx));
                targetScale[i] = 1f + (0.45f * g);
                targetY[i] = maxLiftYPx * g;

                if (absDist > 2f * density && absDist < pushRadiusPx) {
                    targetX[i] = -Math.signum(dist) * pushAmpPx
                            * (float) Math.sin((absDist / pushRadiusPx) * Math.PI);
                } else {
                    targetX[i] = 0f;
                }

                // Step immediately part-way so both hardware and synchronous tests reflect Gaussian shape
                if (cascadeDone[i] || scrubState.pointerActive) {
                    cascadeDone[i] = true;
                    slot.animate().cancel();
                    currentScale[i] += (targetScale[i] - currentScale[i]) * 0.45f;
                    currentY[i] += (targetY[i] - currentY[i]) * 0.45f;
                    currentX[i] += (targetX[i] - currentX[i]) * 0.45f;
                    slot.setScaleX(currentScale[i]);
                    slot.setScaleY(currentScale[i]);
                    slot.setTranslationY(currentY[i]);
                    slot.setTranslationX(currentX[i]);
                }
            }

            if (closestIdx != scrubState.hoveredIndex) {
                int previousHovered = scrubState.hoveredIndex;
                scrubState.hoveredIndex = closestIdx;

                for (int i = 0; i < slotCount; i++) {
                    slotHighlightViews.get(i).setAlpha(i == closestIdx ? 1f : 0f);
                }
                if (previousHovered >= 0 && previousHovered < iconViews.size() && previousHovered != closestIdx) {
                    ReactionAnimatorHelper.attachIdleAnimation(
                            iconViews.get(previousHovered),
                            reactions.get(previousHovered).getKey(),
                            80L,
                            1.0f
                    );
                }
                if (closestIdx >= 0 && closestIdx < reactions.size()) {
                    VybReaction hoveredReaction = reactions.get(closestIdx);
                    ImageView hoveredIcon = iconViews.get(closestIdx);
                    ReactionAnimatorHelper.playHoverSpark(hoveredIcon, hoveredReaction.getKey());
                    ReactionAnimatorHelper.attachIdleAnimation(
                            hoveredIcon,
                            hoveredReaction.getKey(),
                            170L,
                            1.22f
                    );
                    performShortHaptic(context);
                    int reactionAccent = ReactionAnimatorHelper.resolvePrimaryAccentColor(hoveredReaction.getKey());
                    labelBg.setStroke(borderWidthPx, reactionAccent);
                    updateFloatingLabelPosition(
                            rootContainer,
                            barContainer,
                            slotViews.get(closestIdx),
                            floatingLabelView,
                            hoveredReaction.getLabel(),
                            scrubState,
                            density
                    );
                }
            } else if (closestIdx >= 0 && closestIdx < reactions.size()) {
                updateFloatingLabelPosition(
                        rootContainer,
                        barContainer,
                        slotViews.get(closestIdx),
                        floatingLabelView,
                        reactions.get(closestIdx).getLabel(),
                        scrubState,
                        density
                );
            }
        };

        // Cursor / trackpad hover support ("Scrubbing táctil y con cursor")
        barContainer.setOnGenericMotionListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                if (!scrubState.pointerActive) {
                    scrubState.prevPointerX = event.getX();
                    scrubState.pointerVelocity = 0f;
                } else {
                    scrubState.pointerVelocity = event.getX() - scrubState.prevPointerX;
                    scrubState.prevPointerX = event.getX();
                }
                scrubState.pointerX = event.getX();
                scrubState.pointerActive = true;
                updateMagneticState.run();
                return true;
            } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                scrubState.pointerActive = false;
                scrubState.pointerVelocity = 0f;
                updateMagneticState.run();
                return true;
            }
            return false;
        });

        barContainer.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int barW = barContainer.getWidth() > 0 ? barContainer.getWidth() : barContainer.getMeasuredWidth();
                int barH = barContainer.getHeight() > 0 ? barContainer.getHeight() : barContainer.getMeasuredHeight();
                float topTol = dpToPx(72f, density);
                float botTol = dpToPx(44f, density);
                float horizTol = dpToPx(20f, density);

                boolean inside = event.getY() >= -topTol
                        && event.getY() <= barH + botTol
                        && event.getX() >= -horizTol
                        && event.getX() <= barW + horizTol;

                if (inside) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        scrubState.prevPointerX = event.getX();
                        scrubState.pointerVelocity = 0f;
                    } else {
                        scrubState.pointerVelocity = event.getX() - scrubState.prevPointerX;
                        scrubState.prevPointerX = event.getX();
                    }
                    scrubState.pointerX = event.getX();
                    scrubState.pointerActive = true;
                } else {
                    scrubState.pointerActive = false;
                    scrubState.pointerVelocity = 0f;
                }
                updateMagneticState.run();
                return true;
            } else if (action == MotionEvent.ACTION_UP) {
                int hoveredAtRelease = resolveHoveredIconIndex(
                        barContainer,
                        slotViews,
                        event.getX(),
                        event.getY(),
                        density
                );
                int selectedIdx = hoveredAtRelease >= 0 ? hoveredAtRelease : scrubState.hoveredIndex;
                scrubState.pointerActive = false;
                scrubState.hoveredIndex = -1;
                if (hoveredAtRelease >= 0 && selectedIdx >= 0 && selectedIdx < reactions.size()) {
                    VybReaction chosen = reactions.get(selectedIdx);
                    performShortHaptic(context);

                    // Launch parabolic flight before dismissing popup
                    View chosenSlot = slotViews.get(selectedIdx);
                    int[] slotLoc = new int[2];
                    chosenSlot.getLocationInWindow(slotLoc);
                    float startCenterX = slotLoc[0] + (chosenSlot.getWidth() > 0 ? chosenSlot.getWidth() * 0.5f : slotSizePx * 0.5f);
                    float startCenterY = slotLoc[1] + (chosenSlot.getHeight() > 0 ? chosenSlot.getHeight() * 0.5f : slotSizePx * 0.5f);

                    float destCenterX;
                    float destCenterY;
                    if (flightDestinationInWindow != null) {
                        destCenterX = flightDestinationInWindow.exactCenterX();
                        destCenterY = flightDestinationInWindow.exactCenterY();
                    } else {
                        destCenterX = anchorBoundsInWindow.right - dpToPx(22f, density);
                        destCenterY = anchorBoundsInWindow.bottom + dpToPx(8f, density);
                    }

                    ReactionAnimatorHelper.markParabolicFlightInFlight(chosen.getKey(), PARABOLIC_FLIGHT_DURATION_MS);
                    launchParabolicFlightOverlay(
                            rootAnchorView,
                            chosen,
                            startCenterX,
                            startCenterY,
                            iconSizePx,
                            destCenterX,
                            destCenterY,
                            density
                    );

                    listener.onReactionSelected(chosen);
                }
                dismiss();
                return true;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                int previousHovered = scrubState.hoveredIndex;
                scrubState.pointerActive = false;
                scrubState.pointerVelocity = 0f;
                updateMagneticState.run();
                if (previousHovered >= 0 && previousHovered < iconViews.size()) {
                    ReactionAnimatorHelper.attachIdleAnimation(
                            iconViews.get(previousHovered),
                            reactions.get(previousHovered).getKey(),
                            80L,
                            1.0f
                    );
                }
                return true;
            }
            return false;
        });

        PopupWindow popupWindow = new PopupWindow(
                rootContainer,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                focusablePopup
        );
        popupWindow.setOutsideTouchable(focusablePopup);
        popupWindow.setFocusable(focusablePopup);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(0f);
        popupWindow.setOnDismissListener(() -> {
            if (activePhysicsTicker != null) {
                activePhysicsTicker.cancel();
                activePhysicsTicker = null;
            }
            for (ImageView iconView : iconViews) {
                ReactionAnimatorHelper.clearAnimation(iconView);
            }
            activeIconViews.removeAll(iconViews);
            if (activePopup == popupWindow) {
                activePopup = null;
                activePopupRoot = null;
            }
            if (onDismissCallback != null) {
                onDismissCallback.run();
            }
        });
        activePopup = popupWindow;
        activePopupRoot = rootContainer;

        int screenMargin = dpToPx(8f, density);
        int topSafeMargin = dpToPx(24f, density);
        int gapFromBubble = dpToPx(4f, density);

        int desiredX = anchorBoundsInWindow.centerX() - (popupWidth / 2);
        int clampedX = Math.max(screenMargin, Math.min(desiredX, screenWidth - popupWidth - screenMargin));

        int desiredY = anchorBoundsInWindow.top - popupHeight - gapFromBubble;
        if (desiredY < topSafeMargin) {
            desiredY = anchorBoundsInWindow.bottom + gapFromBubble;
        }

        performShortHaptic(context);

        // 1. Spring cascade entrance: capsule bounce + 25ms staggered icon cascade
        barContainer.setScaleX(0.84f);
        barContainer.setScaleY(0.84f);
        barContainer.setTranslationY(dpToPx(14f, density));
        barContainer.setAlpha(0f);

        popupWindow.showAtLocation(rootAnchorView, Gravity.NO_GRAVITY, clampedX, desiredY);

        barContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(240L)
                .setInterpolator(new OvershootInterpolator(1.35f))
                .start();

        for (int i = 0; i < slotViews.size(); i++) {
            View slot = slotViews.get(i);
            long staggerDelay = i * CASCADE_STAGGER_MS;
            slot.setScaleX(0.01f);
            slot.setScaleY(0.01f);
            slot.setTranslationY(dpToPx(18f, density));
            slot.setAlpha(0f);
            slot.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setStartDelay(staggerDelay)
                    .setDuration(CASCADE_SLOT_DURATION_MS)
                    .setInterpolator(ReactionAnimatorHelper.createCascadeSpringInterpolator())
                    .start();
            ReactionAnimatorHelper.attachIdleAnimation(
                    iconViews.get(i),
                    reactions.get(i).getKey(),
                    CASCADE_SLOT_DURATION_MS + staggerDelay,
                    1.0f
            );
        }

        scrubState.lastFrameUptime = SystemClock.uptimeMillis();
        ticker.start();
    }

    /**
     * Animates the selected reaction icon in a parabolic quadratic Bézier arc across the window overlay
     * onto the corner of the target message bubble.
     */
    private void launchParabolicFlightOverlay(
            @NonNull View rootAnchorView,
            @NonNull VybReaction chosen,
            float startWindowX,
            float startWindowY,
            int iconSizePx,
            float endWindowX,
            float endWindowY,
            float density
    ) {
        try {
            View rootView = rootAnchorView.getRootView();
            if (!(rootView instanceof ViewGroup) || !rootAnchorView.isAttachedToWindow()) {
                return;
            }
            int[] rootLoc = new int[2];
            rootView.getLocationInWindow(rootLoc);

            final float startX = startWindowX - rootLoc[0];
            final float startY = startWindowY - rootLoc[1];
            final float endX = endWindowX - rootLoc[0];
            final float endY = endWindowY - rootLoc[1];

            final float ctrlX = (startX + endX) * 0.5f + (startX > endX ? dpToPx(28f, density) : -dpToPx(28f, density));
            final float ctrlY = Math.min(startY, endY) - dpToPx(46f, density);
            final float maxTilt = startX > endX ? -16f : 16f;

            final int flyerSize = Math.round(iconSizePx * 1.15f);
            final ImageView flyer = new ImageView(context);
            flyer.setImageResource(chosen.getDrawableResId());
            flyer.setScaleType(ImageView.ScaleType.FIT_CENTER);
            flyer.measure(
                    View.MeasureSpec.makeMeasureSpec(flyerSize, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(flyerSize, View.MeasureSpec.EXACTLY)
            );
            flyer.layout(0, 0, flyerSize, flyerSize);
            flyer.setPivotX(flyerSize * 0.5f);
            flyer.setPivotY(flyerSize * 0.5f);
            flyer.setTranslationX(startX - flyerSize * 0.5f);
            flyer.setTranslationY(startY - flyerSize * 0.5f);
            flyer.setScaleX(1.38f);
            flyer.setScaleY(1.38f);

            final ViewOverlay overlay = rootView.getOverlay();
            if (overlay instanceof android.view.ViewGroupOverlay) {
                ((android.view.ViewGroupOverlay) overlay).add(flyer);
            } else {
                return;
            }

            ValueAnimator flight = ValueAnimator.ofFloat(0f, 1f);
            flight.setDuration(PARABOLIC_FLIGHT_DURATION_MS);
            flight.setInterpolator(new LinearInterpolator());
            flight.addUpdateListener(anim -> {
                float t = (float) anim.getAnimatedValue();
                float ease = 1f - (float) Math.pow(1f - t, 3.0);
                float inv = 1f - ease;

                float curX = (inv * inv * startX) + (2f * inv * ease * ctrlX) + (ease * ease * endX);
                float curY = (inv * inv * startY) + (2f * inv * ease * ctrlY) + (ease * ease * endY);
                float scale = 1.38f - (0.55f * ease);
                float rotate = (float) Math.sin(ease * Math.PI) * maxTilt;

                flyer.setTranslationX(curX - flyerSize * 0.5f);
                flyer.setTranslationY(curY - flyerSize * 0.5f);
                flyer.setScaleX(scale);
                flyer.setScaleY(scale);
                flyer.setRotation(rotate);
            });
            flight.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        ((android.view.ViewGroupOverlay) overlay).remove(flyer);
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    try {
                        ((android.view.ViewGroupOverlay) overlay).remove(flyer);
                    } catch (Exception ignored) {
                    }
                }
            });
            flight.start();
        } catch (Exception ignored) {
        }
    }

    private static int resolveHoveredIconIndex(
            @NonNull LinearLayout barContainer,
            @NonNull List<View> slotViews,
            float touchX,
            float touchY,
            float density
    ) {
        if (slotViews.isEmpty()) {
            return -1;
        }
        int barW = barContainer.getWidth() > 0 ? barContainer.getWidth() : barContainer.getMeasuredWidth();
        int barH = barContainer.getHeight() > 0 ? barContainer.getHeight() : barContainer.getMeasuredHeight();

        float topTolerance = dpToPx(72f, density);
        float bottomTolerance = dpToPx(44f, density);
        if (touchY < -topTolerance || touchY > barH + bottomTolerance) {
            return -1;
        }
        float horizontalTolerance = dpToPx(20f, density);
        if (touchX < -horizontalTolerance || touchX > barW + horizontalTolerance) {
            return -1;
        }

        int bestIdx = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < slotViews.size(); i++) {
            View slot = slotViews.get(i);
            float centerX;
            if (slot.getWidth() > 0) {
                centerX = (slot.getLeft() + slot.getRight()) * 0.5f;
            } else {
                float approxSlotW = (float) Math.max(1, barW) / slotViews.size();
                centerX = (i + 0.5f) * approxSlotW;
            }
            float dist = Math.abs(touchX - centerX);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static void updateFloatingLabelPosition(
            @NonNull FrameLayout rootContainer,
            @NonNull LinearLayout barContainer,
            @NonNull View targetSlot,
            @NonNull TextView floatingLabelView,
            @NonNull String labelText,
            @NonNull MagneticScrubState scrubState,
            float density
    ) {
        boolean wasVisible = floatingLabelView.getVisibility() == View.VISIBLE;
        if (!labelText.contentEquals(floatingLabelView.getText())) {
            floatingLabelView.setText(labelText);
        }
        floatingLabelView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int labelWidth = floatingLabelView.getMeasuredWidth();
        int rootWidth = rootContainer.getWidth() > 0
                ? rootContainer.getWidth()
                : rootContainer.getMeasuredWidth();

        float slotCenterX;
        if (targetSlot.getWidth() > 0) {
            slotCenterX = (targetSlot.getLeft() + targetSlot.getRight()) * 0.5f;
        } else {
            int barWidth = barContainer.getWidth() > 0 ? barContainer.getWidth() : barContainer.getMeasuredWidth();
            slotCenterX = barWidth * 0.5f;
        }

        float barLeft = barContainer.getLeft() > 0 ? barContainer.getLeft() : rootContainer.getPaddingLeft();
        float iconCenterInRoot = barLeft + slotCenterX;
        float desiredTranslationX = iconCenterInRoot - (labelWidth * 0.5f);
        float minX = dpToPx(4f, density);
        float maxX = Math.max(minX, rootWidth - labelWidth - dpToPx(4f, density));
        float clampedX = Math.max(minX, Math.min(desiredTranslationX, maxX));

        scrubState.targetLabelX = clampedX;
        float immediateTilt = Math.max(-8f, Math.min(8f, scrubState.pointerVelocity * 0.35f));

        if (!wasVisible) {
            scrubState.labelX = clampedX;
            scrubState.labelTilt = immediateTilt;
            floatingLabelView.setTranslationX(clampedX);
            floatingLabelView.setRotation(immediateTilt);
            floatingLabelView.setVisibility(View.VISIBLE);
            floatingLabelView.setScaleX(0.76f);
            floatingLabelView.setScaleY(0.76f);
            floatingLabelView.setAlpha(0.75f);
            floatingLabelView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(145L)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
        } else {
            scrubState.labelX += (clampedX - scrubState.labelX) * 0.48f;
            scrubState.labelTilt += (immediateTilt - scrubState.labelTilt) * 0.45f;
            floatingLabelView.setTranslationX(scrubState.labelX);
            floatingLabelView.setRotation(scrubState.labelTilt);
        }
    }

    private static void hideFloatingLabel(@NonNull TextView floatingLabelView) {
        floatingLabelView.animate().cancel();
        floatingLabelView.setRotation(0f);
        floatingLabelView.setVisibility(View.INVISIBLE);
    }

    public static void performShortHaptic(@NonNull Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                Vibrator vibrator = manager != null ? manager.getDefaultVibrator() : null;
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                    );
                }
            } else {
                @SuppressWarnings("deprecation")
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                                VibrationEffect.createOneShot(HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                        );
                    } else {
                        @SuppressWarnings("deprecation")
                        long duration = HAPTIC_DURATION_MS;
                        vibrator.vibrate(duration);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int dpToPx(float dp, float density) {
        return Math.round(dp * density);
    }

    private static final class MagneticScrubState {
        boolean pointerActive = false;
        int hoveredIndex = -1;
        float pointerX = 0f;
        float prevPointerX = 0f;
        float pointerVelocity = 0f;
        float labelX = 0f;
        float targetLabelX = 0f;
        float labelTilt = 0f;
        long lastFrameUptime = 0L;
    }

    private static final class TouchTrackingBarLayout extends LinearLayout {
        TouchTrackingBarLayout(@NonNull Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }
}
