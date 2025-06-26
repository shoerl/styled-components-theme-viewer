package com.example.themejsoncompletion;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

public class MuiThemeValueResolver {

    /**
     * Resolves a value from a nested map structure (like an MUI theme object)
     * based on a list of path segments.
     *
     * @param themeMap The theme map (typically Map<String, Object>).
     * @param pathSegments The list of keys representing the path to the desired value.
     * @return The resolved value, or null if the path is invalid or the value is not found.
     */
    @Nullable
    public static Object resolveValue(@Nullable Map<String, Object> themeMap, @Nullable List<String> pathSegments) {
        if (themeMap == null || pathSegments == null || pathSegments.isEmpty()) {
            return null;
        }

        Map<String, Object> currentLevel = themeMap;
        Object resolvedValue = null;

        for (int i = 0; i < pathSegments.size(); i++) {
            String segment = pathSegments.get(i);
            Object valueAtSegment = currentLevel.get(segment);

            if (valueAtSegment == null) {
                return null; // Path segment not found
            }

            if (i == pathSegments.size() - 1) {
                // This is the last segment, so this is our resolved value
                resolvedValue = valueAtSegment;
            } else {
                // This is an intermediate segment, it must be a map to continue
                if (valueAtSegment instanceof Map) {
                    // Double-check casting for safety, though ThemeDataManager aims for Map<String, Object>
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nextLevelMap = (Map<String, Object>) valueAtSegment;
                        currentLevel = nextLevelMap;
                    } catch (ClassCastException e) {
                        // Log or handle if a sub-object is not Map<String, Object> as expected
                        // For now, consider path invalid if structure is not as expected
                        return null;
                    }
                } else {
                    // Intermediate segment is not a map, so cannot traverse further
                    return null;
                }
            }
        }
        return resolvedValue;
    }
}
