package com.mtx.rdp.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 显式确认键解析
 * <pre>
 * 1. 解析两个显式确认键的原始文本。只表达确认是否完整，不识别生产环境，也不许可创建 DEV_ONLY 对象。
 * 2. ACKNOWLEDGED 只表示操作者显式确认完整，不是可信非生产证明。
 * 3. PRODUCTION_SELF_DECLARED 只是自我声明，不是可信生产识别。
 * 4. 结果不得用来创建模拟存储或演示清单，也不得作为 C1-02、C1-08 运行时对象的创建条件。
 * </pre>
 */
public final class RuntimeModeConfirmationParser {

    private static final String LOCAL_SIMULATION_TEXT = "local-simulation";
    private static final String PRODUCTION_TEXT = "production";
    private static final String TRUE_TEXT = "true";
    private static final String FALSE_TEXT = "false";

    private RuntimeModeConfirmationParser() {
    }

    /**
     * 原始文本解析
     * <pre>
     * 1. 按键是否存在和未经转换的原始文本解析。空 Optional 表示键不存在，{@code Optional.of("")} 表示键存在且原文为空。
     * 2. 比较前不 trim、不忽略大小写、不做布尔转换。
     * </pre>
     */
    public static Result parse(Optional<String> runtimeMode, Optional<String> devOnlyAcknowledged) {
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        Objects.requireNonNull(devOnlyAcknowledged, "devOnlyAcknowledged");
        SingleKey<RuntimeMode> mode = parseRuntimeMode(runtimeMode);
        SingleKey<Acknowledgement> acknowledgement = parseAcknowledgement(devOnlyAcknowledged);
        List<InvalidReason> reasons = new ArrayList<>();
        if (mode.reason() != null) {
            reasons.add(mode.reason());
        }
        if (acknowledgement.reason() != null) {
            reasons.add(acknowledgement.reason());
        }
        return new Result(
                mode.outcome(),
                acknowledgement.outcome(),
                combine(mode.outcome(), acknowledgement.outcome()),
                reasons);
    }

    private static SingleKey<RuntimeMode> parseRuntimeMode(Optional<String> raw) {
        if (raw.isEmpty()) {
            return new SingleKey<>(RuntimeMode.NOT_SET, null);
        }
        String text = raw.get();
        if (LOCAL_SIMULATION_TEXT.equals(text)) {
            return new SingleKey<>(RuntimeMode.LOCAL_SIMULATION, null);
        }
        if (PRODUCTION_TEXT.equals(text)) {
            return new SingleKey<>(RuntimeMode.PRODUCTION, null);
        }
        return new SingleKey<>(RuntimeMode.INVALID, invalidReason(Key.RUNTIME_MODE, text));
    }

    private static SingleKey<Acknowledgement> parseAcknowledgement(Optional<String> raw) {
        if (raw.isEmpty()) {
            return new SingleKey<>(Acknowledgement.UNCONFIRMED, null);
        }
        String text = raw.get();
        if (TRUE_TEXT.equals(text)) {
            return new SingleKey<>(Acknowledgement.TRUE, null);
        }
        if (FALSE_TEXT.equals(text)) {
            return new SingleKey<>(Acknowledgement.FALSE, null);
        }
        return new SingleKey<>(Acknowledgement.INVALID, invalidReason(Key.DEV_ONLY_ACKNOWLEDGED, text));
    }

    private static InvalidReason invalidReason(Key key, String rawText) {
        InvalidText kind;
        if (rawText.isEmpty()) {
            kind = InvalidText.EMPTY;
        } else if (rawText.isBlank()) {
            kind = InvalidText.BLANK;
        } else {
            kind = InvalidText.NOT_EXACT;
        }
        return new InvalidReason(key, rawText, kind);
    }

    private static Combination combine(RuntimeMode runtimeMode, Acknowledgement acknowledgement) {
        if (runtimeMode == RuntimeMode.INVALID || acknowledgement == Acknowledgement.INVALID) {
            return Combination.INVALID;
        }
        return switch (runtimeMode) {
            case NOT_SET -> acknowledgement == Acknowledgement.TRUE
                    ? Combination.INCOMPLETE
                    : Combination.NOT_CONFIRMED;
            case LOCAL_SIMULATION -> acknowledgement == Acknowledgement.TRUE
                    ? Combination.ACKNOWLEDGED
                    : Combination.INCOMPLETE;
            case PRODUCTION -> acknowledgement == Acknowledgement.TRUE
                    ? Combination.CONFLICT
                    : Combination.PRODUCTION_SELF_DECLARED;
            case INVALID -> throw new IllegalStateException("非法单键已在组合前返回");
        };
    }

    /**
     * 两个键的配置名。原因里用它区分是哪一个键非法。
     */
    public enum Key {

        RUNTIME_MODE("mtx.rdp.runtime-mode"),
        DEV_ONLY_ACKNOWLEDGED("mtx.rdp.dev-only-acknowledged");

        private final String configKey;

        Key(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    /**
     * {@code mtx.rdp.runtime-mode} 的单键结果。只有与原文精确相等才离开 INVALID。
     */
    public enum RuntimeMode {
        NOT_SET,
        LOCAL_SIMULATION,
        PRODUCTION,
        INVALID
    }

    /**
     * {@code mtx.rdp.dev-only-acknowledged} 的单键结果。TRUE 只对应原文 {@code true}，不对应 {@code TRUE}。
     */
    public enum Acknowledgement {
        UNCONFIRMED,
        TRUE,
        FALSE,
        INVALID
    }

    /**
     * 两个单键都不是 INVALID 时的组合。INVALID 不能降级成其他组合。
     */
    public enum Combination {
        NOT_CONFIRMED,
        INCOMPLETE,
        /**
         * 操作者显式确认完整。不是可信非生产证明，也不是 DEV_ONLY 创建许可。
         */
        ACKNOWLEDGED,
        /**
         * 自我声明为 production。不是可信生产识别。
         */
        PRODUCTION_SELF_DECLARED,
        CONFLICT,
        INVALID
    }

    /**
     * 非法原文的稳定分类。保留原文，不把原因收成一句可被另一键覆盖的消息。
     */
    public enum InvalidText {
        EMPTY,
        BLANK,
        NOT_EXACT
    }

    /**
     * 一个非法键的原因。两个键都非法时，结果里各有一条，顺序固定为 runtime-mode 然后 dev-only-acknowledged。
     */
    public record InvalidReason(Key key, String rawText, InvalidText text) {

        public InvalidReason {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(rawText, "rawText");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * 单键结果、组合结果，以及每个非法键的原因。合法组合的原因列表为空。
     */
    public record Result(
            RuntimeMode runtimeMode,
            Acknowledgement devOnlyAcknowledged,
            Combination combination,
            List<InvalidReason> invalidReasons) {

        public Result {
            Objects.requireNonNull(runtimeMode, "runtimeMode");
            Objects.requireNonNull(devOnlyAcknowledged, "devOnlyAcknowledged");
            Objects.requireNonNull(combination, "combination");
            Objects.requireNonNull(invalidReasons, "invalidReasons");
            invalidReasons = List.copyOf(invalidReasons);
            int invalidKeyCount = (runtimeMode == RuntimeMode.INVALID ? 1 : 0)
                    + (devOnlyAcknowledged == Acknowledgement.INVALID ? 1 : 0);
            if (combination == Combination.INVALID) {
                if (invalidReasons.size() != invalidKeyCount || invalidKeyCount == 0) {
                    throw new IllegalArgumentException("非法组合必须保留每个非法键的原因");
                }
            } else if (invalidKeyCount != 0 || !invalidReasons.isEmpty()) {
                throw new IllegalArgumentException("合法组合不能带有非法原因");
            }
        }
    }

    private record SingleKey<T>(T outcome, InvalidReason reason) {
    }
}
