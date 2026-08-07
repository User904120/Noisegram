package org.cleargram.integration;

import org.junit.Test;
import org.cleargram.api.NoiseMessage;
import org.telegram.messenger.MessageObject;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TelegramMessageMapperTest {

    private final TelegramMessageMapper mapper = new TelegramMessageMapper();

    @Test
    public void singleMessageTextUsesExistingExtraction() {
        TelegramDecisionTestFixtures.TestMessage message = message(1);
        message.messageText = "message text";

        assertEquals("message text", mapper.map(message).getText());
    }

    @Test
    public void singleMediaCaptionUsesExistingExtraction() {
        TelegramDecisionTestFixtures.TestMessage message = message(1);
        message.caption = "media caption";
        message.messageText = "message text";

        assertEquals("media caption", mapper.map(message).getText());
    }

    @Test
    public void groupedMappingIncludesCaptionOnCanonicalPrimary() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        primary.caption = "primary caption";
        TelegramDecisionTestFixtures.TestMessage member = message(2);

        assertEquals("primary caption", mapper.mapGrouped(primary, Arrays.asList(primary, member)).getText());
    }

    @Test
    public void groupedMappingIncludesCaptionOnlyOnNonPrimaryMember() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        TelegramDecisionTestFixtures.TestMessage captionMember = message(2);
        captionMember.caption = "album caption";

        assertEquals("album caption", mapper.mapGrouped(primary, Arrays.asList(primary, captionMember)).getText());
    }

    @Test
    public void groupedFragmentsKeepMemberOrderAndUseNewlineSeparator() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        primary.messageText = "first";
        TelegramDecisionTestFixtures.TestMessage second = message(2);
        second.caption = "second";
        TelegramDecisionTestFixtures.TestMessage third = message(3);
        third.messageText = "third";

        assertEquals("first\nsecond\nthird", mapper.mapGrouped(primary, Arrays.asList(primary, second, third)).getText());
    }

    @Test
    public void groupedMappingSkipsNullAndEmptyMembers() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        TelegramDecisionTestFixtures.TestMessage empty = message(2);
        TelegramDecisionTestFixtures.TestMessage captionMember = message(3);
        captionMember.caption = "visible caption";

        assertEquals("visible caption", mapper.mapGrouped(primary, Arrays.<MessageObject>asList(primary, null, empty, captionMember)).getText());
    }

    @Test
    public void fullyEmptyGroupAndNullGroupMapToEmptyText() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);

        assertEquals("", mapper.mapGrouped(primary, Collections.<MessageObject>singletonList(primary)).getText());
        assertEquals("", mapper.mapGrouped(primary, null).getText());
    }

    @Test
    public void oneMemberGroupMatchesSingleMessageAndDoesNotMutateTelegramMessage() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        primary.caption = "caption";
        primary.messageText = "message";

        NoiseMessage grouped = mapper.mapGrouped(primary, Collections.<MessageObject>singletonList(primary));

        assertEquals(mapper.map(primary).getText(), grouped.getText());
        assertEquals("caption", primary.caption.toString());
        assertEquals("message", primary.messageText.toString());
        assertNull(primary.messageOwner);
    }

    @Test
    public void duplicateListElementsAreEvaluatedInStableListOrder() {
        TelegramDecisionTestFixtures.TestMessage primary = message(1);
        primary.caption = "caption";

        assertEquals("caption\ncaption", mapper.mapGrouped(primary, Arrays.<MessageObject>asList(primary, primary)).getText());
    }

    private static TelegramDecisionTestFixtures.TestMessage message(int messageId) {
        return TelegramDecisionTestFixtures.message(1, 100L, 0L, messageId);
    }
}
