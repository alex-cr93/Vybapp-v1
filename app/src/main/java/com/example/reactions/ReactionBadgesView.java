package com.example.reactions;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReactionBadgesView extends ViewGroup {

    public interface OnBadgeClickListener {
        void onBadgeClicked(@NonNull VybReaction reaction);
    }

    private final float density;
    private final int figureGapPx;
    private final int rowGapPx;
    private boolean alignEnd = false;
    private final Map<String, Integer> previousCounts = new HashMap<>();
    @Nullable
    private OnBadgeClickListener currentClickListener;
    @Nullable
    private String lastSignature;

    public ReactionBadgesView(@NonNull Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.figureGapPx = Math.round(4f * density);
        this.rowGapPx = Math.round(3f * density);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void bindReactions(
            @Nullable Map<String, List<String>> reactionsMap,
            @Nullable String currentUid,
            boolean alignEnd,
            @Nullable OnBadgeClickListener clickListener
    ) {
        bindReactions(reactionsMap, currentUid, alignEnd, true, clickListener);
    }

    public void bindReactions(
            @Nullable Map<String, List<String>> reactionsMap,
            @Nullable String currentUid,
            boolean alignEnd,
            boolean isDarkTheme,
            @Nullable OnBadgeClickListener clickListener
    ) {
        this.currentClickListener = clickListener;
        List<ReactionCatalog.ActiveReactionEntry> entries =
                ReactionCatalog.getOrderedActiveEntries(reactionsMap, currentUid);

        if (entries.isEmpty()) {
            this.alignEnd = alignEnd;
            this.lastSignature = null;
            previousCounts.clear();
            clearAllChildAnimations();
            removeAllViews();
            setVisibility(View.GONE);
            return;
        }

        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append(alignEnd ? 'E' : 'S').append(isDarkTheme ? 'D' : 'L');
        for (ReactionCatalog.ActiveReactionEntry entry : entries) {
            sigBuilder.append('|')
                    .append(entry.getReaction().getKey())
                    .append(':')
                    .append(entry.getCount())
                    .append(':')
                    .append(entry.isSelectedByCurrentUser() ? '1' : '0');
        }
        String nextSignature = sigBuilder.toString();
        if (nextSignature.equals(lastSignature) && getChildCount() == entries.size()) {
            setVisibility(View.VISIBLE);
            return;
        }

        this.alignEnd = alignEnd;
        this.lastSignature = nextSignature;
        clearAllChildAnimations();
        removeAllViews();
        setVisibility(View.VISIBLE);

        Map<String, Integer> nextCounts = new HashMap<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            ReactionCatalog.ActiveReactionEntry entry = entries.get(i);
            VybReaction reaction = entry.getReaction();
            int count = entry.getCount();
            nextCounts.put(reaction.getKey(), count);

            Integer oldCount = previousCounts.get(reaction.getKey());
            boolean shouldBounce = oldCount == null || count != oldCount;

            View figureView = createPureFigureView(reaction);
            addView(figureView);

            if (shouldBounce) {
                long flightDelayMs = ReactionAnimatorHelper.consumeParabolicFlightDelayMs(reaction.getKey());
                if (flightDelayMs > 0L) {
                    figureView.setAlpha(0f);
                    figureView.setScaleX(0.65f);
                    figureView.setScaleY(0.65f);
                    figureView.postDelayed(() -> {
                        figureView.setAlpha(1f);
                        figureView.setScaleX(1f);
                        figureView.setScaleY(1f);
                        ReactionAnimatorHelper.playSignatureMicroAnimation(figureView, reaction.getKey());
                    }, flightDelayMs);
                } else {
                    figureView.setScaleX(0.45f);
                    figureView.setScaleY(0.45f);
                    figureView.setTranslationY(-Math.round(5f * density));
                    figureView.setAlpha(0f);
                    figureView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(220L)
                            .setInterpolator(new OvershootInterpolator(1.8f))
                            .start();
                    figureView.post(() -> ReactionAnimatorHelper.playSignatureMicroAnimation(figureView, reaction.getKey()));
                }
            }

            long idleDelayMs = shouldBounce
                    ? (ReactionAnimatorHelper.SIGNATURE_DURATION_MS + 420L)
                    : (i * 140L);
            ReactionAnimatorHelper.attachIdleAnimation(figureView, reaction.getKey(), idleDelayMs, 0.92f);
        }

        previousCounts.clear();
        previousCounts.putAll(nextCounts);
        requestLayout();
    }

    private void clearAllChildAnimations() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.animate().cancel();
            ReactionAnimatorHelper.clearAnimation(child);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        clearAllChildAnimations();
        super.onDetachedFromWindow();
    }

    @NonNull
    private View createPureFigureView(@NonNull VybReaction reaction) {
        ImageView figure = new ImageView(getContext());
        int figureSizePx = Math.round(31f * density);
        int padPx = Math.round(1.5f * density);
        figure.setLayoutParams(new LayoutParams(figureSizePx, figureSizePx));
        figure.setPadding(padPx, padPx, padPx, padPx);
        figure.setImageResource(reaction.getDrawableResId());
        figure.setScaleType(ImageView.ScaleType.FIT_CENTER);
        figure.setBackground(null);
        figure.setContentDescription(reaction.getLabel());
        figure.setClickable(true);
        figure.setFocusable(true);
        figure.setOnClickListener(v -> {
            ReactionFloatingBarController.performShortHaptic(getContext());
            ReactionAnimatorHelper.playSignatureMicroAnimation(v, reaction.getKey());
            if (currentClickListener != null) {
                currentClickListener.onBadgeClicked(reaction);
            }
        });
        return figure;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : MeasureSpec.getSize(widthMeasureSpec);

        int childCount = getChildCount();
        int currentRowWidth = 0;
        int currentRowHeight = 0;
        int totalWidth = 0;
        int totalHeight = 0;

        int figureSizePx = Math.round(31f * density);
        int childSpec = MeasureSpec.makeMeasureSpec(figureSizePx, MeasureSpec.EXACTLY);

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            child.measure(childSpec, childSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            int neededWidth = currentRowWidth == 0 ? cw : currentRowWidth + figureGapPx + cw;
            if (currentRowWidth > 0 && neededWidth > maxWidth) {
                totalWidth = Math.max(totalWidth, currentRowWidth);
                totalHeight += currentRowHeight + rowGapPx;
                currentRowWidth = cw;
                currentRowHeight = ch;
            } else {
                currentRowWidth = neededWidth;
                currentRowHeight = Math.max(currentRowHeight, ch);
            }
        }

        if (currentRowWidth > 0) {
            totalWidth = Math.max(totalWidth, currentRowWidth);
            totalHeight += currentRowHeight;
        }

        int resolvedWidth = resolveSize(totalWidth, widthMeasureSpec);
        int resolvedHeight = resolveSize(totalHeight, heightMeasureSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int availableWidth = r - l;
        int childCount = getChildCount();
        if (childCount == 0) return;

        List<List<View>> rows = new ArrayList<>();
        List<Integer> rowWidths = new ArrayList<>();
        List<Integer> rowHeights = new ArrayList<>();

        List<View> currentRow = new ArrayList<>();
        int currentRowWidth = 0;
        int currentRowHeight = 0;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int neededWidth = currentRow.isEmpty() ? cw : currentRowWidth + figureGapPx + cw;

            if (!currentRow.isEmpty() && neededWidth > availableWidth) {
                rows.add(currentRow);
                rowWidths.add(currentRowWidth);
                rowHeights.add(currentRowHeight);
                currentRow = new ArrayList<>();
                currentRowWidth = cw;
                currentRowHeight = ch;
            } else {
                currentRowWidth = neededWidth;
                currentRowHeight = Math.max(currentRowHeight, ch);
            }
            currentRow.add(child);
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
            rowWidths.add(currentRowWidth);
            rowHeights.add(currentRowHeight);
        }

        int y = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<View> row = rows.get(rowIndex);
            int rw = rowWidths.get(rowIndex);
            int rh = rowHeights.get(rowIndex);
            int x = alignEnd ? Math.max(0, availableWidth - rw) : 0;

            for (int col = 0; col < row.size(); col++) {
                View child = row.get(col);
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                int top = y + ((rh - ch) / 2);
                child.layout(x, top, x + cw, top + ch);
                x += cw + figureGapPx;
            }
            y += rh + rowGapPx;
        }
    }
}
