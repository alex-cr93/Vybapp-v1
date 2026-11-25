package com.example.reactions;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.R;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReactionFirestoreHelper {

    public interface ReactionCallback {
        void onSuccess(boolean added);
        void onError(@NonNull String errorMessage);
    }

    private final FirebaseFirestore firestore;

    public ReactionFirestoreHelper(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        String databaseId = appContext.getString(R.string.firestore_database_id);
        this.firestore = FirebaseFirestore.getInstance(databaseId);
    }

    public void toggleReaction(
            @NonNull String chatId,
            @NonNull String messageId,
            @NonNull String currentUid,
            @NonNull String reactionKey,
            @Nullable Map<String, List<String>> currentReactions,
            @Nullable ReactionCallback callback
    ) {
        if (chatId.trim().isEmpty() || messageId.trim().isEmpty() || currentUid.trim().isEmpty()) {
            if (callback != null) {
                callback.onError("No se pudo identificar el mensaje o la sesión.");
            }
            return;
        }
        if (!ReactionCatalog.isValidKey(reactionKey)) {
            if (callback != null) {
                callback.onError("Reacción no válida.");
            }
            return;
        }

        boolean alreadyReacted = false;
        String previousKeyForUser = null;
        if (currentReactions != null) {
            List<String> existingUids = currentReactions.get(reactionKey);
            if (existingUids != null && existingUids.contains(currentUid)) {
                alreadyReacted = true;
            }
            for (Map.Entry<String, List<String>> entry : currentReactions.entrySet()) {
                String key = entry.getKey();
                List<String> uids = entry.getValue();
                if (!reactionKey.equals(key)
                        && ReactionCatalog.isValidKey(key)
                        && uids != null
                        && uids.contains(currentUid)) {
                    previousKeyForUser = key;
                    break;
                }
            }
        }

        final boolean adding = !alreadyReacted;
        DocumentReference msgRef = firestore
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId);

        Map<String, Object> updates = new HashMap<>(4);
        if (adding) {
            if (previousKeyForUser != null) {
                updates.put("reactions." + previousKeyForUser, FieldValue.arrayRemove(currentUid));
            }
            updates.put("reactions." + reactionKey, FieldValue.arrayUnion(currentUid));
        } else {
            updates.put("reactions." + reactionKey, FieldValue.arrayRemove(currentUid));
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        msgRef.update(updates)
                .addOnSuccessListener(unused -> {
                    if (callback != null) {
                        callback.onSuccess(adding);
                    }
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        String message = error.getLocalizedMessage();
                        callback.onError(
                                (message != null && !message.trim().isEmpty())
                                        ? message
                                        : "No se pudo guardar la reacción."
                        );
                    }
                });
    }
}
