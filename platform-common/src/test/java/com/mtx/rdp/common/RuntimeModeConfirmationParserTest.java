package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mtx.rdp.common.RuntimeModeConfirmationParser.Acknowledgement;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.Combination;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.InvalidReason;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.InvalidText;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.Key;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.Result;
import com.mtx.rdp.common.RuntimeModeConfirmationParser.RuntimeMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 确认键原始文本解析。不是生产识别，也不是 DEV_ONLY 创建许可。
 * ACKNOWLEDGED 只表示显式确认完整。PRODUCTION_SELF_DECLARED 只是自我声明。
 */
class RuntimeModeConfirmationParserTest {

    @Test
    void absentRuntimeModeIsNotSetAndDoesNotIdentifyProduction() {
        Result result = parse(Optional.empty(), Optional.empty());

        assertEquals(RuntimeMode.NOT_SET, result.runtimeMode());
        assertEquals(Acknowledgement.UNCONFIRMED, result.devOnlyAcknowledged());
        assertEquals(Combination.NOT_CONFIRMED, result.combination());
        assertEquals(List.of(), result.invalidReasons());
    }

    @Test
    void exactLocalSimulationAndProductionAreConfirmationValuesNotEnvironmentProof() {
        Result localSimulation = parse(Optional.of("local-simulation"), Optional.empty());
        Result production = parse(Optional.of("production"), Optional.empty());

        assertEquals(RuntimeMode.LOCAL_SIMULATION, localSimulation.runtimeMode());
        assertEquals(Combination.INCOMPLETE, localSimulation.combination());
        assertEquals(RuntimeMode.PRODUCTION, production.runtimeMode());
        assertEquals(Combination.PRODUCTION_SELF_DECLARED, production.combination());
        assertEquals(List.of(), production.invalidReasons());
    }

    @Test
    void runtimeModeRejectsEmptyBlankPaddingCaseAndAliasesWithoutPreprocessing() {
        assertRuntimeModeInvalid("", InvalidText.EMPTY);
        assertRuntimeModeInvalid(" \t", InvalidText.BLANK);
        assertRuntimeModeInvalid(" local-simulation", InvalidText.NOT_EXACT);
        assertRuntimeModeInvalid("production ", InvalidText.NOT_EXACT);
        assertRuntimeModeInvalid("Local-simulation", InvalidText.NOT_EXACT);
        assertRuntimeModeInvalid("PRODUCTION", InvalidText.NOT_EXACT);
        assertRuntimeModeInvalid("local", InvalidText.NOT_EXACT);
        assertRuntimeModeInvalid("prod", InvalidText.NOT_EXACT);
    }

    @Test
    void absentAcknowledgementIsUnconfirmedAndExactFalseIsNotConfirmed() {
        Result absent = parse(Optional.empty(), Optional.empty());
        Result explicitFalse = parse(Optional.empty(), Optional.of("false"));

        assertEquals(Acknowledgement.UNCONFIRMED, absent.devOnlyAcknowledged());
        assertEquals(Acknowledgement.FALSE, explicitFalse.devOnlyAcknowledged());
        assertEquals(Combination.NOT_CONFIRMED, explicitFalse.combination());
    }

    @Test
    void exactTrueIsDistinctFromUpperCaseAndIsNotADevOnlyCreationLicense() {
        Result exactTrue = parse(Optional.empty(), Optional.of("true"));
        Result upperCase = parse(Optional.empty(), Optional.of("TRUE"));

        assertEquals(Acknowledgement.TRUE, exactTrue.devOnlyAcknowledged());
        assertEquals(Combination.INCOMPLETE, exactTrue.combination());
        assertEquals(Acknowledgement.INVALID, upperCase.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, upperCase.combination());
        assertEquals(
                List.of(reason(Key.DEV_ONLY_ACKNOWLEDGED, "TRUE", InvalidText.NOT_EXACT)),
                upperCase.invalidReasons());
    }

    @Test
    void acknowledgementRejectsEmptyBlankDigitsWordsAndPaddingWithoutBooleanConversion() {
        assertAcknowledgementInvalid("", InvalidText.EMPTY);
        assertAcknowledgementInvalid("  ", InvalidText.BLANK);
        assertAcknowledgementInvalid("FALSE", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("1", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("0", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("yes", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("no", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid(" true", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("false ", InvalidText.NOT_EXACT);
        assertAcknowledgementInvalid("on", InvalidText.NOT_EXACT);
    }

    @Test
    void legalCombinationsDescribeConfirmationOnlyAndDoNotGrantDevOnlyCreation() {
        assertLegal(Optional.empty(), Optional.empty(), Combination.NOT_CONFIRMED);
        assertLegal(Optional.empty(), Optional.of("false"), Combination.NOT_CONFIRMED);
        assertLegal(Optional.empty(), Optional.of("true"), Combination.INCOMPLETE);
        assertLegal(Optional.of("local-simulation"), Optional.empty(), Combination.INCOMPLETE);
        assertLegal(Optional.of("local-simulation"), Optional.of("false"), Combination.INCOMPLETE);
        assertLegal(Optional.of("local-simulation"), Optional.of("true"), Combination.ACKNOWLEDGED);
        assertLegal(Optional.of("production"), Optional.empty(), Combination.PRODUCTION_SELF_DECLARED);
        assertLegal(Optional.of("production"), Optional.of("false"), Combination.PRODUCTION_SELF_DECLARED);
        assertLegal(Optional.of("production"), Optional.of("true"), Combination.CONFLICT);
    }

    @Test
    void onlyInvalidRuntimeModeKeepsItsReasonAndIsNotOverriddenByExactTrue() {
        Result result = parse(Optional.of(" local-simulation"), Optional.of("true"));

        assertEquals(RuntimeMode.INVALID, result.runtimeMode());
        assertEquals(Acknowledgement.TRUE, result.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, result.combination());
        assertEquals(
                List.of(reason(Key.RUNTIME_MODE, " local-simulation", InvalidText.NOT_EXACT)),
                result.invalidReasons());
    }

    @Test
    void onlyInvalidAcknowledgementKeepsItsReasonAndIsNotOverriddenByProduction() {
        Result result = parse(Optional.of("production"), Optional.of("TRUE"));

        assertEquals(RuntimeMode.PRODUCTION, result.runtimeMode());
        assertEquals(Acknowledgement.INVALID, result.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, result.combination());
        assertEquals(
                List.of(reason(Key.DEV_ONLY_ACKNOWLEDGED, "TRUE", InvalidText.NOT_EXACT)),
                result.invalidReasons());
    }

    @Test
    void bothInvalidKeysKeepBothReasonsAndDoNotKeepOnlyTheFirst() {
        Result result = parse(Optional.of(" "), Optional.of(""));

        assertEquals(RuntimeMode.INVALID, result.runtimeMode());
        assertEquals(Acknowledgement.INVALID, result.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, result.combination());
        assertEquals(
                List.of(
                        reason(Key.RUNTIME_MODE, " ", InvalidText.BLANK),
                        reason(Key.DEV_ONLY_ACKNOWLEDGED, "", InvalidText.EMPTY)),
                result.invalidReasons());
    }

    @Test
    void nullOptionalIsNotTreatedAsAnAbsentConfirmationKey() {
        assertThrows(
                NullPointerException.class,
                () -> RuntimeModeConfirmationParser.parse(null, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> RuntimeModeConfirmationParser.parse(Optional.empty(), null));
    }

    private static void assertRuntimeModeInvalid(String rawText, InvalidText text) {
        Result result = parse(Optional.of(rawText), Optional.empty());

        assertEquals(RuntimeMode.INVALID, result.runtimeMode());
        assertEquals(Acknowledgement.UNCONFIRMED, result.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, result.combination());
        assertEquals(List.of(reason(Key.RUNTIME_MODE, rawText, text)), result.invalidReasons());
    }

    private static void assertAcknowledgementInvalid(String rawText, InvalidText text) {
        Result result = parse(Optional.empty(), Optional.of(rawText));

        assertEquals(RuntimeMode.NOT_SET, result.runtimeMode());
        assertEquals(Acknowledgement.INVALID, result.devOnlyAcknowledged());
        assertEquals(Combination.INVALID, result.combination());
        assertEquals(List.of(reason(Key.DEV_ONLY_ACKNOWLEDGED, rawText, text)), result.invalidReasons());
    }

    private static void assertLegal(Optional<String> runtimeMode, Optional<String> acknowledgement, Combination combination) {
        Result result = parse(runtimeMode, acknowledgement);

        assertEquals(combination, result.combination());
        assertEquals(List.of(), result.invalidReasons());
    }

    private static Result parse(Optional<String> runtimeMode, Optional<String> acknowledgement) {
        return RuntimeModeConfirmationParser.parse(runtimeMode, acknowledgement);
    }

    private static InvalidReason reason(Key key, String rawText, InvalidText text) {
        return new InvalidReason(key, rawText, text);
    }
}
