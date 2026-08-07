package org.cleargram.integration;

/** Immutable runtime state for the independent NG-015 message CTA feature. */
public final class MessageCtaButtonSettingsState {

    public static final MessageCtaButtonSettingsState DEFAULT =
            new MessageCtaButtonSettingsState(false, MessageCtaButtonAction.COLLAPSE);

    private final boolean enabled;
    private final MessageCtaButtonAction action;

    public MessageCtaButtonSettingsState(boolean enabled, MessageCtaButtonAction action) {
        this.enabled = enabled;
        this.action = action != null ? action : MessageCtaButtonAction.COLLAPSE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public MessageCtaButtonAction getAction() {
        return action;
    }
}
