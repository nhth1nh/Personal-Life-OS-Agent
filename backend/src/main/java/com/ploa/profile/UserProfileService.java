package com.ploa.profile;

import com.ploa.assistant.memory.UserMemory;
import com.ploa.assistant.memory.UserMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing user base profile targets and operating settings (budget, weight goal, sleep target).
 * Persists dynamically to the PostgreSQL user_memories table so user goals do not live in static environment files.
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    public static final String CAT_HEALTH = "HEALTH";
    public static final String CAT_FINANCE = "FINANCE";
    public static final String CAT_PROFILE = "PROFILE";

    public static final String KEY_TARGET_WEIGHT = "target_weight_kg";
    public static final String KEY_SLEEP_TARGET = "sleep_target_hours";
    public static final String KEY_BASELINE_DAYS = "baseline_window_days";
    public static final String KEY_MONTHLY_BUDGET = "monthly_budget_vnd";
    public static final String KEY_EMERGENCY_RESERVE_FLOOR = "emergency_reserve_floor_vnd";
    public static final String KEY_USER_NAME = "name";

    public static final double DEFAULT_TARGET_WEIGHT_KG = 68.0;
    public static final double DEFAULT_SLEEP_TARGET_HOURS = 7.5;
    public static final int DEFAULT_BASELINE_WINDOW_DAYS = 30;
    public static final long DEFAULT_MONTHLY_BUDGET_VND = 10_000_000L;
    public static final long DEFAULT_EMERGENCY_RESERVE_FLOOR_VND = 0L;

    private final UserMemoryRepository memoryRepo;

    public UserProfileService(UserMemoryRepository memoryRepo) {
        this.memoryRepo = memoryRepo;
    }

    public record UserProfileData(
            double targetWeightKg,
            double sleepTargetHours,
            int baselineWindowDays,
            long monthlyBudgetVnd,
            long emergencyReserveFloorVnd,
            String userName
    ) {
        public UserProfileData(double targetWeightKg, double sleepTargetHours, int baselineWindowDays, long monthlyBudgetVnd, String userName) {
            this(targetWeightKg, sleepTargetHours, baselineWindowDays, monthlyBudgetVnd, DEFAULT_EMERGENCY_RESERVE_FLOOR_VND, userName);
        }
    }

    public record ParsedProfileUpdate(
            Long monthlyBudgetVnd,
            Long emergencyReserveFloorVnd,
            Double targetWeightKg,
            Double sleepTargetHours,
            Integer baselineWindowDays,
            String userName
    ) {
        public ParsedProfileUpdate(Long monthlyBudgetVnd, Double targetWeightKg, Double sleepTargetHours, Integer baselineWindowDays, String userName) {
            this(monthlyBudgetVnd, null, targetWeightKg, sleepTargetHours, baselineWindowDays, userName);
        }

        public boolean hasAny() {
            return monthlyBudgetVnd != null
                    || emergencyReserveFloorVnd != null
                    || targetWeightKg != null
                    || sleepTargetHours != null
                    || baselineWindowDays != null
                    || (userName != null && !userName.isBlank());
        }
    }

    @Transactional(readOnly = true)
    public UserProfileData getProfile() {
        return new UserProfileData(
                getTargetWeightKg(),
                getSleepTargetHours(),
                getBaselineWindowDays(),
                getMonthlyBudgetVnd(),
                getEmergencyReserveFloorVnd(),
                getUserName().orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public double getTargetWeightKg() {
        return getDoublePreference(CAT_HEALTH, KEY_TARGET_WEIGHT, DEFAULT_TARGET_WEIGHT_KG);
    }

    @Transactional(readOnly = true)
    public double getSleepTargetHours() {
        return getDoublePreference(CAT_HEALTH, KEY_SLEEP_TARGET, DEFAULT_SLEEP_TARGET_HOURS);
    }

    @Transactional(readOnly = true)
    public int getBaselineWindowDays() {
        return getIntPreference(CAT_HEALTH, KEY_BASELINE_DAYS, DEFAULT_BASELINE_WINDOW_DAYS);
    }

    @Transactional(readOnly = true)
    public long getMonthlyBudgetVnd() {
        return getLongPreference(CAT_FINANCE, KEY_MONTHLY_BUDGET, DEFAULT_MONTHLY_BUDGET_VND);
    }

    @Transactional(readOnly = true)
    public long getEmergencyReserveFloorVnd() {
        return getLongPreference(CAT_FINANCE, KEY_EMERGENCY_RESERVE_FLOOR, DEFAULT_EMERGENCY_RESERVE_FLOOR_VND);
    }

    @Transactional(readOnly = true)
    public Optional<String> getUserName() {
        return memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(CAT_PROFILE, KEY_USER_NAME)
                .map(UserMemory::getMemoryValue);
    }

    @Transactional
    public void setTargetWeightKg(double kg, String source) {
        saveOrUpdate(CAT_HEALTH, KEY_TARGET_WEIGHT, String.valueOf(kg), source);
    }

    @Transactional
    public void setSleepTargetHours(double hours, String source) {
        saveOrUpdate(CAT_HEALTH, KEY_SLEEP_TARGET, String.valueOf(hours), source);
    }

    @Transactional
    public void setBaselineWindowDays(int days, String source) {
        saveOrUpdate(CAT_HEALTH, KEY_BASELINE_DAYS, String.valueOf(days), source);
    }

    @Transactional
    public void setMonthlyBudgetVnd(long budgetVnd, String source) {
        saveOrUpdate(CAT_FINANCE, KEY_MONTHLY_BUDGET, String.valueOf(budgetVnd), source);
    }

    @Transactional
    public void setEmergencyReserveFloorVnd(long amountVnd, String source) {
        saveOrUpdate(CAT_FINANCE, KEY_EMERGENCY_RESERVE_FLOOR, String.valueOf(amountVnd), source);
    }

    @Transactional
    public void setUserName(String name, String source) {
        saveOrUpdate(CAT_PROFILE, KEY_USER_NAME, name, source);
    }

    @Transactional
    public UserProfileData applyUpdate(ParsedProfileUpdate update, String source) {
        if (update.monthlyBudgetVnd() != null) {
            setMonthlyBudgetVnd(update.monthlyBudgetVnd(), source);
        }
        if (update.emergencyReserveFloorVnd() != null) {
            setEmergencyReserveFloorVnd(update.emergencyReserveFloorVnd(), source);
        }
        if (update.targetWeightKg() != null) {
            setTargetWeightKg(update.targetWeightKg(), source);
        }
        if (update.sleepTargetHours() != null) {
            setSleepTargetHours(update.sleepTargetHours(), source);
        }
        if (update.baselineWindowDays() != null) {
            setBaselineWindowDays(update.baselineWindowDays(), source);
        }
        if (update.userName() != null && !update.userName().isBlank()) {
            setUserName(update.userName(), source);
        }
        return getProfile();
    }

    @Transactional
    public void saveOrUpdate(String category, String key, String value, String source) {
        String cat = category.toUpperCase().trim();
        String k = key.trim();
        String v = value.trim();
        String src = (source != null && !source.isBlank()) ? source : "USER_EXPLICIT";

        memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(cat, k)
                .map(existing -> {
                    existing.setMemoryValue(v);
                    existing.setSource(src);
                    log.info("Updated profile setting [{} / {}] = {} (source={})", cat, k, v, src);
                    return memoryRepo.save(existing);
                })
                .orElseGet(() -> {
                    UserMemory m = new UserMemory(cat, k, v, src);
                    log.info("Created profile setting [{} / {}] = {} (source={})", cat, k, v, src);
                    return memoryRepo.save(m);
                });
    }

    /**
     * Parses free-form arguments from Telegram /init command or chat updates.
     */
    public ParsedProfileUpdate parseArgs(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ParsedProfileUpdate(null, null, null, null, null, null);
        }

        Long budget = null;
        Long reserve = null;
        Double weight = null;
        Double sleep = null;
        Integer baseline = null;
        String name = null;

        String text = rawText.trim();

        // 1. Explicit keyword matching
        Matcher mBudget = Pattern.compile("(?i)(?:budget|ngân\\s*sách|ngansach|tien)\\s*[:=]?\\s*([0-9.,]+(?:[mk]|tr|triệu|trieu)?)").matcher(text);
        if (mBudget.find()) {
            budget = parseBudgetAmount(mBudget.group(1));
        }

        Matcher mReserve = Pattern.compile("(?i)(?:reserve|emergency|dự\\s*phòng|duphong)\\s*[:=]?\\s*([0-9.,]+(?:[mk]|tr|triệu|trieu)?)").matcher(text);
        if (mReserve.find()) {
            reserve = parseBudgetAmount(mReserve.group(1));
        }

        Matcher mWeight = Pattern.compile("(?i)(?:weight|cân\\s*nặng|cannang|can)\\s*[:=]?\\s*([0-9]+(?:[.,][0-9]+)?)(?:\\s*kg)?").matcher(text);
        if (mWeight.find()) {
            weight = parseDoubleSafe(mWeight.group(1));
        }

        Matcher mSleep = Pattern.compile("(?i)(?:sleep|ngủ|ngu|giấc\\s*ngủ)\\s*[:=]?\\s*([0-9]+(?:[.,][0-9]+)?)(?:\\s*h|\\s*hrs|\\s*tiếng)?").matcher(text);
        if (mSleep.find()) {
            sleep = parseDoubleSafe(mSleep.group(1));
        }

        Matcher mBaseline = Pattern.compile("(?i)(?:baseline|window|lookback)\\s*[:=]?\\s*([0-9]+)(?:\\s*d|\\s*days|\\s*ngày)?").matcher(text);
        if (mBaseline.find()) {
            try {
                baseline = Integer.parseInt(mBaseline.group(1));
            } catch (Exception ignored) {}
        }

        Matcher mName = Pattern.compile("(?i)(?:name|tên|ten)\\s*[:=]?\\s*([a-zA-Z0-9_\\-\\s]+?)(?:\\s+(?:budget|weight|sleep|baseline|reserve)|$)").matcher(text);
        if (mName.find()) {
            name = mName.group(1).trim();
        }

        // 2. Token-based heuristic matching if keyword matching didn't catch everything
        String[] tokens = text.split("[,;\\s]+");
        for (String tok : tokens) {
            String clean = tok.trim();
            if (clean.isBlank()) continue;

            if (weight == null && clean.matches("(?i)^[0-9]+(?:[.,][0-9]+)?kg$")) {
                weight = parseDoubleSafe(clean.replaceAll("(?i)kg", ""));
            } else if (sleep == null && clean.matches("(?i)^[0-9]+(?:[.,][0-9]+)?(?:h|hrs)$")) {
                sleep = parseDoubleSafe(clean.replaceAll("(?i)(?:hrs|h)", ""));
            } else if (sleep == null && clean.matches("(?i)^(\\d+)h(\\d+)$")) {
                Matcher hm = Pattern.compile("(?i)^(\\d+)h(\\d+)$").matcher(clean);
                if (hm.matches()) {
                    double h = Double.parseDouble(hm.group(1));
                    double m = Double.parseDouble(hm.group(2));
                    sleep = Math.round((h + m / 60.0) * 10.0) / 10.0;
                }
            } else if (budget == null && clean.matches("(?i)^[0-9.,]+(?:[mk]|tr|triệu|trieu)$")) {
                budget = parseBudgetAmount(clean);
            }
        }

        return new ParsedProfileUpdate(budget, reserve, weight, sleep, baseline, name);
    }

    public static Long parseBudgetAmount(String input) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim().toLowerCase();
        try {
            if (s.endsWith("m") || s.endsWith("tr") || s.endsWith("triệu") || s.endsWith("trieu")) {
                String numStr = s.replaceAll("(?i)(m|tr|triệu|trieu)", "").replace(",", ".");
                double val = Double.parseDouble(numStr);
                return (long) (val * 1_000_000L);
            } else if (s.endsWith("k")) {
                String numStr = s.replaceAll("(?i)k", "").replace(",", ".");
                double val = Double.parseDouble(numStr);
                return (long) (val * 1_000L);
            } else {
                String clean = s.replace(".", "").replace(",", "");
                return Long.parseLong(clean);
            }
        } catch (Exception e) {
            log.warn("Failed to parse budget amount: '{}'", input);
            return null;
        }
    }

    public static Double parseDoubleSafe(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            return Double.parseDouble(input.trim().replace(",", "."));
        } catch (Exception e) {
            log.warn("Failed to parse double: '{}'", input);
            return null;
        }
    }

    private double getDoublePreference(String category, String key, double defaultValue) {
        return memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(category, key)
                .map(m -> {
                    try {
                        return Double.parseDouble(m.getMemoryValue().trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid double in user_memories [{} / {}]: '{}', falling back to {}",
                                category, key, m.getMemoryValue(), defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private int getIntPreference(String category, String key, int defaultValue) {
        return memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(category, key)
                .map(m -> {
                    try {
                        return Integer.parseInt(m.getMemoryValue().trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid int in user_memories [{} / {}]: '{}', falling back to {}",
                                category, key, m.getMemoryValue(), defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private long getLongPreference(String category, String key, long defaultValue) {
        return memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(category, key)
                .map(m -> {
                    try {
                        return Long.parseLong(m.getMemoryValue().trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid long in user_memories [{} / {}]: '{}', falling back to {}",
                                category, key, m.getMemoryValue(), defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
