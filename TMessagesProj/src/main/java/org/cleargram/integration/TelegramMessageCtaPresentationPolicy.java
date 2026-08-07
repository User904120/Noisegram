package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoisePipelineOutcome;
import org.telegram.messenger.MessageObject;

/** Composes the independent CTA setting into the existing Telegram decision path. */
public final class TelegramMessageCtaPresentationPolicy {

    private TelegramMessageCtaPresentationPolicy() {
    }

    /**
     * Returns a presentation action only after the Core pipeline reached its
     * normal no-match continuation point. Any unsupported or invalid input is
     * deliberately left to the existing Core decision.
     */
    public static NoiseAction resolveAction(
            NoisePipelineOutcome outcome,
            MessageObject messageObject,
            MessageObject.GroupedMessages groupedMessages,
            MessageCtaButtonSettingsState settings
    ) {
        try {
            return resolveAction(outcome,
                    TelegramMessageCtaClassifier.createInput(messageObject, groupedMessages), settings);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static NoiseAction resolveAction(
            NoisePipelineOutcome outcome,
            MessageCtaClassificationInput input,
            MessageCtaButtonSettingsState settings
    ) {
        try {
            if (outcome != NoisePipelineOutcome.CONTINUE_DUPLICATE_VIDEO
                    || settings == null
                    || !settings.isEnabled()
                    || !TelegramMessageCtaClassifier.hasTargetCtaButton(
                    input)) {
                return null;
            }
            MessageCtaButtonAction action = settings.getAction();
            if (action == MessageCtaButtonAction.HIDE) {
                return NoiseAction.HIDE;
            }
            if (action == MessageCtaButtonAction.COLLAPSE) {
                return NoiseAction.COLLAPSE;
            }
        } catch (Throwable ignored) {
            // Fail-open: keep the original Core decision.
        }
        return null;
    }
}
