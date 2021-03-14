package com.virjar.echo.nat.bootstrap;

public class TextUtil {
    public static boolean isEmpty(String input) {
        if (input == null) {
            return true;
        }
        return input.trim().isEmpty();
    }

    public static boolean isNotEmpty(String input) {
        return !isEmpty(input);
    }
}
