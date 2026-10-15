package com.dailyrupi.prreview.tools;

import java.util.ArrayList;
import java.util.List;

/** Turns a JVM method descriptor such as {@code (Ljava/lang/String;[I)V} into {@code String, int[]}. */
final class JvmDescriptors {

    private JvmDescriptors() {
    }

    static String readableParameters(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return "";
        }
        int end = descriptor.indexOf(')');
        String params = end < 0 ? descriptor.substring(1) : descriptor.substring(1, end);
        List<String> names = new ArrayList<>();
        int i = 0;
        while (i < params.length()) {
            int dims = 0;
            while (i < params.length() && params.charAt(i) == '[') {
                dims++;
                i++;
            }
            if (i >= params.length()) {
                break;
            }
            String name;
            char c = params.charAt(i);
            if (c == 'L') {
                int semi = params.indexOf(';', i);
                if (semi < 0) {
                    break;
                }
                String qualified = params.substring(i + 1, semi);
                name = qualified.substring(qualified.lastIndexOf('/') + 1).replace('$', '.');
                i = semi + 1;
            } else {
                name = switch (c) {
                    case 'Z' -> "boolean";
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'S' -> "short";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'F' -> "float";
                    case 'D' -> "double";
                    default -> String.valueOf(c);
                };
                i++;
            }
            names.add(name + "[]".repeat(dims));
        }
        return String.join(", ", names);
    }
}
