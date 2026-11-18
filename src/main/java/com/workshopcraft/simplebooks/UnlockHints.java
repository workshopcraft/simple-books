package com.workshopcraft.simplebooks;

import java.util.Map;
import java.util.Set;

/** Readable default phrases for tag unlock hints when the tag has no lang name. */
public final class UnlockHints {
    private UnlockHints() {}

    private static final Map<String, String> IRREGULAR = Map.of(
            "leaves", "leaf",
            "shelves", "shelf",
            "knives", "knife",
            "potatoes", "potato",
            "tomatoes", "tomato"
    );

    private static final Set<String> UNCOUNTABLE = Set.of(
            "glass", "grass", "wheat", "dirt", "sand", "gravel", "ice", "stone",
            "iron", "gold", "copper", "netherite", "shears", "boots", "leggings",
            "redstone", "glowstone", "prismarine", "obsidian", "andesite", "diorite",
            "granite", "wool", "leather", "paper", "sugar", "tuff", "calcite",
            "cactus"
    );

    /** Path of a tag such as logs or seeds/garlic, as a short noun phrase (log, garlic seed). */
    public static String tagPhrase(String path) {
        if (path == null || path.isEmpty()) return "item";
        String[] parts = path.split("/");
        if (parts.length == 2) return humanise(parts[1]) + " " + singularise(humanise(parts[0]));
        return singularise(humanise(path.replace('/', ' ')));
    }

    private static String humanise(String s) {
        return s.replace('_', ' ').trim();
    }

    static String singularise(String phrase) {
        if (phrase == null || phrase.isEmpty()) return phrase;
        int sp = phrase.lastIndexOf(' ');
        String head = sp < 0 ? "" : phrase.substring(0, sp + 1);
        String w = sp < 0 ? phrase : phrase.substring(sp + 1);
        return head + singularWord(w);
    }

    private static String singularWord(String w) {
        if (w.length() <= 2) return w;
        String lower = w.toLowerCase();
        if (UNCOUNTABLE.contains(lower)) return w;
        for (var e : IRREGULAR.entrySet()) {
            String pl = e.getKey();
            if (lower.equals(pl) || (lower.endsWith(pl) && lower.length() > pl.length()))
                return w.substring(0, w.length() - pl.length()) + e.getValue();
        }
        if (lower.endsWith("ies") && lower.length() > 4 && isConsonant(lower.charAt(lower.length() - 4)))
            return w.substring(0, w.length() - 3) + "y";
        if (lower.length() > 4 && (lower.endsWith("sses") || lower.endsWith("shes") || lower.endsWith("ches")
                || lower.endsWith("xes") || lower.endsWith("zes")))
            return w.substring(0, w.length() - 2);
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us")
                && !lower.endsWith("is") && !lower.endsWith("os"))
            return w.substring(0, w.length() - 1);
        return w;
    }

    private static boolean isConsonant(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) < 0;
    }
}
