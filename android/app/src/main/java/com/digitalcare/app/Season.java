package com.digitalcare.app;

/** North India seasons with their daily water goal (glasses of 250 ml). The water tip for each is in strings.xml. */
public enum Season {
    SUMMER("summer", 12),
    MONSOON("monsoon", 10),
    TRANSITION("transition", 10),
    WINTER("winter", 8);

    final String id;
    final int goal;

    Season(String id, int goal) {
        this.id = id;
        this.goal = goal;
    }

    static Season of(int month) {
        if (month >= 4 && month <= 6) return SUMMER;
        if (month >= 7 && month <= 9) return MONSOON;
        if (month == 3 || month == 10) return TRANSITION;
        return WINTER;
    }
}
