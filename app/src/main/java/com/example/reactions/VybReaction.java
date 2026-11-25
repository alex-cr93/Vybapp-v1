package com.example.reactions;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

public final class VybReaction {
    private final String key;
    @DrawableRes
    private final int drawableResId;
    private final String label;

    public VybReaction(@NonNull String key, @DrawableRes int drawableResId, @NonNull String label) {
        this.key = key;
        this.drawableResId = drawableResId;
        this.label = label;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @DrawableRes
    public int getDrawableResId() {
        return drawableResId;
    }

    @NonNull
    public String getLabel() {
        return label;
    }
}
