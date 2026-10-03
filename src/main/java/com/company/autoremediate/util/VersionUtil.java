package com.company.autoremediate.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lenient numeric version handling for Maven-style versions ("5.3.20.RELEASE", "2.0.0-beta"). */
public final class VersionUtil {
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)(.*)$");

    private VersionUtil() {}

    public static int[] parse(String version) {
        if (version == null) return new int[0];
        List<Integer> parts = new ArrayList<>();
        for (String segment : version.trim().split("\\.")) {
            Matcher m = LEADING_DIGITS.matcher(segment);
            if (!m.matches()) break;
            parts.add(Integer.parseInt(m.group(1)));
            if (!m.group(2).isEmpty()) break;
        }
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    public static boolean sameMajor(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        return x.length > 0 && y.length > 0 && x[0] == y[0];
    }

    public static boolean isUpgrade(String from, String to) {
        return parse(from).length > 0 && parse(to).length > 0 && compare(to, from) > 0;
    }
}
