package com.example.reactions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReactionCatalog {

    public static final String KEY_PURA_VIDA = "pura_vida";
    public static final String KEY_BUENA_VIBRA = "buena_vibra";
    public static final String KEY_QUE_RAJADO = "que_rajado";
    public static final String KEY_QUE_CHIVA = "que_chiva";
    public static final String KEY_DE_UNA = "de_una";
    public static final String KEY_QUE_AGUEVADO = "que_aguevado";
    public static final String KEY_QUE_CHICHA = "que_chicha";

    private static final List<VybReaction> ALL_REACTIONS;
    private static final Map<String, VybReaction> BY_KEY;

    static {
        List<VybReaction> list = new ArrayList<>(7);
        list.add(new VybReaction(KEY_PURA_VIDA, R.drawable.pura_vida, "Pura vida"));
        list.add(new VybReaction(KEY_BUENA_VIBRA, R.drawable.buena_vibra, "Buena vibra"));
        list.add(new VybReaction(KEY_QUE_RAJADO, R.drawable.que_rajado, "¡Qué rajado!"));
        list.add(new VybReaction(KEY_QUE_CHIVA, R.drawable.que_chiva, "¡Qué chiva!"));
        list.add(new VybReaction(KEY_DE_UNA, R.drawable.de_una, "¡De una!"));
        list.add(new VybReaction(KEY_QUE_AGUEVADO, R.drawable.que_aguevado, "¡Qué agüevado!"));
        list.add(new VybReaction(KEY_QUE_CHICHA, R.drawable.que_chicha, "¡Qué chicha!"));
        ALL_REACTIONS = Collections.unmodifiableList(list);

        Map<String, VybReaction> map = new LinkedHashMap<>(7);
        for (VybReaction item : list) {
            map.put(item.getKey(), item);
        }
        BY_KEY = Collections.unmodifiableMap(map);
    }

    private ReactionCatalog() {
    }

    @NonNull
    public static List<VybReaction> getAll() {
        return ALL_REACTIONS;
    }

    @Nullable
    public static VybReaction findByKey(@Nullable String key) {
        if (key == null) return null;
        return BY_KEY.get(key);
    }

    public static boolean isValidKey(@Nullable String key) {
        return key != null && BY_KEY.containsKey(key);
    }

    @NonNull
    public static List<ActiveReactionEntry> getOrderedActiveEntries(
            @Nullable Map<String, List<String>> reactionsMap,
            @Nullable String currentUid
    ) {
        if (reactionsMap == null || reactionsMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<ActiveReactionEntry> result = new ArrayList<>();
        for (VybReaction reaction : ALL_REACTIONS) {
            List<String> uids = reactionsMap.get(reaction.getKey());
            if (uids != null && !uids.isEmpty()) {
                boolean selectedByMe = currentUid != null && !currentUid.isEmpty() && uids.contains(currentUid);
                result.add(new ActiveReactionEntry(reaction, uids.size(), selectedByMe));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static final class ActiveReactionEntry {
        private final VybReaction reaction;
        private final int count;
        private final boolean selectedByCurrentUser;

        public ActiveReactionEntry(
                @NonNull VybReaction reaction,
                int count,
                boolean selectedByCurrentUser
        ) {
            this.reaction = reaction;
            this.count = count;
            this.selectedByCurrentUser = selectedByCurrentUser;
        }

        @NonNull
        public VybReaction getReaction() {
            return reaction;
        }

        public int getCount() {
            return count;
        }

        public boolean isSelectedByCurrentUser() {
            return selectedByCurrentUser;
        }
    }
}
