package com.example.appbackend.domain;

import java.util.Set;

public final class LearningStatuses {
    public static final Set<String> PATH = Set.of("active", "completed", "archived");
    public static final Set<String> ITEM = Set.of("locked", "ready", "in_progress", "completed", "needs_review");
    public static final Set<String> MASTERY = Set.of("new", "weak", "learning", "mastered");

    private LearningStatuses() {
    }
}
