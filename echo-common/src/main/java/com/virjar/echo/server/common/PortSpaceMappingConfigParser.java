package com.virjar.echo.server.common;

import com.google.common.base.Splitter;

import java.util.HashSet;
import java.util.Set;

public class PortSpaceMappingConfigParser {
    public static Set<Integer> parseConfig(String portSpace) {
        Set<Integer> duplicateRemoveConfig = new HashSet<>();
        Iterable<String> pairs = Splitter.on(":").split(portSpace);
        for (String pair : pairs) {
            if (pair.contains("-")) {
                int index = pair.indexOf("-");
                String startStr = pair.substring(0, index);
                String endStr = pair.substring(index + 1);
                int start = Integer.parseInt(startStr);
                int end = Integer.parseInt(endStr);
                for (int i = start; i <= end; i++) {
                    duplicateRemoveConfig.add(i);
                }
            } else {
                duplicateRemoveConfig.add(Integer.parseInt(pair));
            }
        }
        return duplicateRemoveConfig;
    }
}
