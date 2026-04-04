package com.example.appbackend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周次计算工具类
 */
public class WeekCalculator {

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)-(\\d+).$");
    private static final Pattern SINGLE_PATTERN = Pattern.compile("^(\\d+).$");

    /**
     * 计算今天是第几周
     * @param semesterStart 学期开始日期
     * @return 当前周次（从 1 开始），如果学期还没开始返回 0
     */
    public static int getCurrentWeek(LocalDate semesterStart) {
        if (semesterStart == null) {
            return 0;
        }

        LocalDate today = LocalDate.now();

        // 如果今天早于开学日期，返回 0
        if (today.isBefore(semesterStart)) {
            return 0;
        }

        // 计算相差的周数
        long daysBetween = ChronoUnit.DAYS.between(semesterStart, today);
        long weeksBetween = daysBetween / 7;

        // 周次从 1 开始
        return (int) weeksBetween + 1;
    }

    /**
     * 判断给定周次是否在课表周次范围内
     * @param weekRange 周次范围，如：1-5 周，3-5 周 (单),8-10 周 (双)
     * @param currentWeek 当前周次
     * @return 是否匹配
     */
    public static boolean isWeekInRange(String weekRange, int currentWeek) {
        if (weekRange == null || weekRange.trim().isEmpty() || currentWeek <= 0) {
            return false;
        }

        System.out.println("DEBUG isWeekInRange - weekRange: '" + weekRange + "', currentWeek: " + currentWeek);

        // 按逗号分割多个周次范围（支持中文逗号和英文逗号）
        String[] parts = weekRange.split("[，,]");
        System.out.println("DEBUG split parts count: " + parts.length);

        for (String rawPart : parts) {
            String part = normalize(rawPart);
            System.out.println("DEBUG rawPart: '" + rawPart + "' -> part: '" + part + "'");

            if (part.isEmpty()) {
                continue;
            }

            // 检查是否是单双周
            boolean oddOnly = isOddOnly(part);
            boolean evenOnly = isEvenOnly(part);
            System.out.println("DEBUG oddOnly: " + oddOnly + ", evenOnly: " + evenOnly);

            // 移除单双周标记
            String pure = removeOddEvenFlag(part);
            System.out.println("DEBUG pure: '" + pure + "'");

            // 尝试匹配范围格式：3-5 周
            Matcher rangeMatcher = RANGE_PATTERN.matcher(pure);
            System.out.println("DEBUG rangeMatcher.matches(): " + rangeMatcher.matches());

            if (rangeMatcher.matches()) {
                int start = Integer.parseInt(rangeMatcher.group(1));
                int end = Integer.parseInt(rangeMatcher.group(2));
                System.out.println("DEBUG 范围匹配 - start: " + start + ", end: " + end);

                if (currentWeek >= start && currentWeek <= end) {
                    if (oddOnly && currentWeek % 2 == 0) {
                        System.out.println("DEBUG 单周但当前是偶数周，跳过");
                        continue;
                    }
                    if (evenOnly && currentWeek % 2 != 0) {
                        System.out.println("DEBUG 双周但当前是奇数周，跳过");
                        continue;
                    }
                    System.out.println("DEBUG 匹配成功，返回 true");
                    return true;
                }
                continue;
            }

            // 尝试匹配单个周次：5 周
            Matcher singleMatcher = SINGLE_PATTERN.matcher(pure);
            System.out.println("DEBUG singleMatcher.matches(): " + singleMatcher.matches());

            if (singleMatcher.matches()) {
                int week = Integer.parseInt(singleMatcher.group(1));
                System.out.println("DEBUG 单个周次匹配 - week: " + week);
                if (currentWeek == week) {
                    if (oddOnly && currentWeek % 2 == 0) {
                        System.out.println("DEBUG 单周但当前是偶数周，跳过");
                        continue;
                    }
                    if (evenOnly && currentWeek % 2 != 0) {
                        System.out.println("DEBUG 双周但当前是奇数周，跳过");
                        continue;
                    }
                    System.out.println("DEBUG 匹配成功，返回 true");
                    return true;
                }
            }
        }

        System.out.println("DEBUG 没有匹配，返回 false");
        return false;
    }

    /**
     * 解析周次范围，返回所有有效的周次列表
     * @param weekRange 周次范围，如：1-5 周，3-5 周 (单),8-10 周 (双)
     * @return 有效的周次列表
     */
    public static List<Integer> parseWeekRange(String weekRange) {
        List<Integer> weeks = new ArrayList<>();

        if (weekRange == null || weekRange.trim().isEmpty()) {
            return weeks;
        }

        // 按逗号分割多个周次范围（支持中文逗号和英文逗号）
        String[] parts = weekRange.split("[，,]");

        for (String rawPart : parts) {
            String part = normalize(rawPart);
            if (part.isEmpty()) {
                continue;
            }

            boolean oddOnly = isOddOnly(part);
            boolean evenOnly = isEvenOnly(part);
            String pure = removeOddEvenFlag(part);

            Matcher rangeMatcher = RANGE_PATTERN.matcher(pure);
            if (rangeMatcher.matches()) {
                int start = Integer.parseInt(rangeMatcher.group(1));
                int end = Integer.parseInt(rangeMatcher.group(2));

                for (int week = start; week <= end; week++) {
                    if (oddOnly && week % 2 == 0) {
                        continue;
                    }
                    if (evenOnly && week % 2 != 0) {
                        continue;
                    }
                    weeks.add(week);
                }
                continue;
            }

            Matcher singleMatcher = SINGLE_PATTERN.matcher(pure);
            if (singleMatcher.matches()) {
                int week = Integer.parseInt(singleMatcher.group(1));
                weeks.add(week);
            }
        }

        return weeks;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private static boolean isOddOnly(String text) {
        return text.contains("(单)") || text.contains("(单周)") || text.contains("单周");
    }

    private static boolean isEvenOnly(String text) {
        return text.contains("(双)") || text.contains("(双周)") || text.contains("双周");
    }

    private static String removeOddEvenFlag(String text) {
        return text.replace("(单周)", "")
                .replace("(双周)", "")
                .replace("(单)", "")
                .replace("(双)", "")
                .replace("单周", "")
                .replace("双周", "")
                .trim();
    }

    // 中文字符"周"的 Unicode 编码
    private static final String ZHOU = "\u5468";
}