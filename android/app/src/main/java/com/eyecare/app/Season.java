package com.eyecare.app;

/** North India seasons with their daily water goal (glasses of 250 ml) and tip. */
public enum Season {
    SUMMER("summer", 12, "Nimbu pani, chaas or ORS after sweating. Avoid ice-cold water right after coming in from the heat."),
    MONSOON("monsoon", 10, "Humidity hides sweat loss. Drink boiled or filtered water."),
    TRANSITION("transition", 10, "Sip regularly, even if you're not thirsty."),
    WINTER("winter", 8, "Less thirst, same need. Warm water helps.");

    final String id;
    final int goal;
    final String tip;

    Season(String id, int goal, String tip) {
        this.id = id;
        this.goal = goal;
        this.tip = tip;
    }

    static Season of(int month) {
        if (month >= 4 && month <= 6) return SUMMER;
        if (month >= 7 && month <= 9) return MONSOON;
        if (month == 3 || month == 10) return TRANSITION;
        return WINTER;
    }
}
