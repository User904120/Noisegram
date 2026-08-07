package org.cleargram.integration;

import org.cleargram.api.NoiseAction;
import org.cleargram.api.NoiseBootstrap;
import org.cleargram.api.NoiseDecision;
import org.cleargram.api.NoiseEvaluation;
import org.cleargram.api.NoiseMessage;
import org.cleargram.api.NoisePipelineOutcome;

/**
 * Read-only gateway from Telegram integration code to Cleargram Core.
 */
public final class TelegramNoiseGateway {

    public NoiseDecision evaluate(NoiseMessage message) {
        NoiseEvaluation evaluation = evaluatePipeline(message);
        return evaluation != null ? evaluation.getDecision() : allow();
    }

    public NoiseEvaluation evaluatePipeline(NoiseMessage message) {
        try {
            if (!NoiseBootstrap.isInitialized()) {
                return null;
            }
            return NoiseBootstrap.getCore().evaluatePipeline(message);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private NoiseDecision allow() {
        return new NoiseDecision(NoiseAction.ALLOW);
    }
}
