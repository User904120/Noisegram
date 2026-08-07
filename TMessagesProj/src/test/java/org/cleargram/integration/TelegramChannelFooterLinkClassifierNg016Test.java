package org.cleargram.integration;

import org.junit.Test;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelegramChannelFooterLinkClassifierNg016Test {

    private static final TelegramChannelFooterLinkClassifier.PublicLinkMatcher JVM_MATCHER =
            TelegramChannelFooterLinkClassifierNg016Test::matchesPublicUsername;

    @Test
    public void matchesConfirmedHostsTextUrlPostsSubdomainsAndPreviewPaths() {
        assertMatch("body\nhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\nhttps://telegram.me/current/12", "https://telegram.me/current/12", channel("current"), null, 4);
        assertMatch("body\nhttps://telegram.dog/current", "https://telegram.dog/current", channel("current"), null, 4);
        assertMatch("body\nhttps://custom.example/current", "https://custom.example/current", channel("current"), "custom.example", 4);
        assertMatch("body\nhttps://current.t.me/12", "https://current.t.me/12", channel("current"), null, 4);
        assertMatch("body\nhttps://t.me/s/current/12", "https://t.me/s/current/12", channel("current"), null, 4);
    }

    @Test
    public void supportsTextUrlSchemeLessQueryFragmentCaseAndActiveUsername() {
        TLRPC.TL_channel chat = channel("current");
        TLRPC.TL_username secondary = new TLRPC.TL_username();
        secondary.active = true;
        secondary.username = "alias";
        chat.usernames.add(secondary);

        String text = "Subscribe\nclick";
        TLRPC.TL_messageEntityTextUrl textUrl = textUrl(text, "click");
        textUrl.url = "HTTPS://T.ME/ALIAS/?q=1#footer";
        assertMatch(text, textUrl, chat, null, text.indexOf('\n'));
        assertMatch("t.me/current", "t.me/current", channel("CURRENT"), null, 0);
    }

    @Test
    public void supportsSchemelessTelegramUrlsWithColonInQueryOrFragment() {
        assertMatch("t.me/current?url=https://example.com", "t.me/current?url=https://example.com", channel("current"), null, 0);
        assertMatch("t.me/current#section:details", "t.me/current#section:details", channel("current"), null, 0);

        String text = "display link";
        TLRPC.TL_messageEntityTextUrl textUrl = textUrl(text, "link");
        textUrl.url = "t.me/current?next=https://example.com";
        assertMatch(text, textUrl, channel("current"), null, 0);
    }

    @Test
    public void allowsExternalAndForeignLinksBesideOneSameChannelLink() {
        String text = "body\nhttps://example.com https://t.me/other https://t.me/current";
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        entities.add(url(text, "https://example.com"));
        entities.add(url(text, "https://t.me/other"));
        entities.add(url(text, "https://t.me/current"));
        assertRange(text, entities, channel("current"), null, text.indexOf('\n'), text.length());
    }

    @Test
    public void rejectsUnsupportedAndAmbiguousForms() {
        String[] urls = {
                "https://example.com", "https://t.me/other", "https://telegram.org/current",
                "https://telegra.ph/current", "https://graph.org/current", "https://telesco.pe/current",
                "https://t.me/c/1/2", "https://t.me/joinchat/hash", "https://t.me/+hash",
                "https://t.me/m/slug", "tg://resolve?domain=current", "t.me@evil.example/current",
                "https://t.me.evil.example/current", "https://t.me/share/url?url=x", "https://t.me/current/not-a-post"
        };
        for (String url : urls) {
            assertNoMatch(url, url(url, url), channel("current"), null);
        }
    }

    @Test
    public void validatesCandidateRangesButIgnoresFormattingAndOutsideCandidates() {
        String text = "https://t.me/current\nfooter";
        assertNoMatch(text, url(text, "https://t.me/current"), channel("current"), null);

        String footer = "body\nhttps://t.me/current";
        TLRPC.TL_messageEntityUrl crossing = new TLRPC.TL_messageEntityUrl();
        crossing.offset = footer.indexOf("body") + 2;
        crossing.length = footer.length() - crossing.offset;
        assertNoMatch(footer, crossing, channel("current"), null);

        TLRPC.TL_messageEntityUrl invalid = new TLRPC.TL_messageEntityUrl();
        invalid.offset = footer.length();
        invalid.length = 1;
        assertNoMatch(footer, invalid, channel("current"), null);

        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = 4;
        entities.add(bold);
        entities.add(url(footer, "https://t.me/current"));
        assertRange(footer, entities, channel("current"), null, 4, footer.length());
    }

    @Test
    public void computesWholeSuffixRangesForAllLineSeparatorsAndFooterOnly() {
        assertMatch("body\nhttps://t.me/current   \n\n", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\r\nhttps://t.me/current\t\r\n", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\rhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("https://t.me/current", "https://t.me/current", channel("current"), null, 0);
    }

    @Test
    public void normalizesAllWhitespaceBeforeFooterFromRetainedPrefix() {
        assertMatch("body\n\nhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\r\n\r\nhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body   \nhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\n \t\r\nhttps://t.me/current", "https://t.me/current", channel("current"), null, 4);
        assertMatch("body\nhttps://t.me/current \t\r\n", "https://t.me/current", channel("current"), null, 4);
    }

    @Test
    public void malformedUrlsFailOpenWhileValidExternalUrlDoesNotBlockMatch() {
        assertNoMatch("https://exa mple.com", url("https://exa mple.com", "https://exa mple.com"), channel("current"), null);
        assertNoMatch("https:///missing-host", url("https:///missing-host", "https:///missing-host"), channel("current"), null);
        assertNoMatch("https://t.me/%zz", url("https://t.me/%zz", "https://t.me/%zz"), channel("current"), null);

        String text = "https://t.me/current https://exa mple.com";
        ArrayList<TLRPC.MessageEntity> malformed = new ArrayList<>();
        malformed.add(url(text, "https://t.me/current"));
        malformed.add(url(text, "https://exa mple.com"));
        assertNoMatch(text, malformed, channel("current"), null);

        String external = "https://t.me/current https://example.com";
        ArrayList<TLRPC.MessageEntity> valid = new ArrayList<>();
        valid.add(url(external, "https://t.me/current"));
        valid.add(url(external, "https://example.com"));
        assertRange(external, valid, channel("current"), null, 0, external.length());
    }

    @Test
    public void unsafeSchemesAndHostlessUrlsBlockSameChannelFooterTrim() {
        assertSameChannelWithAdditionalUrlDoesNotMatch("https:///missing-host");
        assertSameChannelWithAdditionalUrlDoesNotMatch("mailto:user@example.com");
        assertSameChannelWithAdditionalUrlDoesNotMatch("ftp://example.com/file");
        assertSameChannelWithAdditionalUrlDoesNotMatch("https://exa mple.com");

        assertNoMatch("https:///missing-host", url("https:///missing-host", "https:///missing-host"), channel("current"), null);
        assertNoMatch("http:///missing-host", url("http:///missing-host", "http:///missing-host"), channel("current"), null);
        assertNoMatch("t.me/current:invalid", url("t.me/current:invalid", "t.me/current:invalid"), channel("current"), null);
        assertNoMatch("t.me/current?next=https://exa mple.com", url("t.me/current?next=https://exa mple.com", "t.me/current?next=https://exa mple.com"), channel("current"), null);
    }

    @Test
    public void validHttpAndHttpsExternalUrlsDoNotBlockSameChannelFooterTrim() {
        assertSameChannelWithAdditionalUrlMatches("https://example.com/page");
        assertSameChannelWithAdditionalUrlMatches("http://example.com/page");
    }

    @Test
    public void productionIdentityExceptionFailsOpenAndJvmSeamControlsIdentity() {
        String text = "https://t.me/current";
        assertNoMatchWithMatcher(text, singleton(url(text, text)), channel("current"), null,
                (chat, username) -> { throw new RuntimeException(); });

        TLRPC.TL_channel active = channel("primary");
        TLRPC.TL_username activeAlias = new TLRPC.TL_username();
        activeAlias.active = true;
        activeAlias.username = "current";
        active.usernames.add(activeAlias);
        assertRange(text, singleton(url(text, text)), active, null, 0, text.length());

        TLRPC.TL_channel inactive = channel("primary");
        TLRPC.TL_username inactiveAlias = new TLRPC.TL_username();
        inactiveAlias.active = false;
        inactiveAlias.username = "current";
        inactive.usernames.add(inactiveAlias);
        assertNoMatch(text, url(text, text), inactive, null);
    }

    @Test
    public void rejectsBrokenUtf16BoundariesAndIneligibleChatsWithoutMutation() {
        String text = "body\n🙂https://t.me/current";
        TLRPC.TL_messageEntityUrl broken = new TLRPC.TL_messageEntityUrl();
        broken.offset = text.indexOf("🙂") + 1;
        broken.length = "https://t.me/current".length();
        assertNoMatch(text, broken, channel("current"), null);

        TLRPC.TL_channel mega = channel("current");
        mega.megagroup = true;
        assertNoMatch("https://t.me/current", url("https://t.me/current", "https://t.me/current"), mega, null);
        TLRPC.TL_channel forum = channel("current");
        forum.forum = true;
        assertNoMatch("https://t.me/current", url("https://t.me/current", "https://t.me/current"), forum, null);
        assertNoMatch("https://t.me/current", url("https://t.me/current", "https://t.me/current"), new TLRPC.TL_chat(), null);
        assertNoMatch("https://t.me/current", url("https://t.me/current", "https://t.me/current"), channel(null), null);

        String source = "body\nhttps://t.me/current";
        TLRPC.TL_messageEntityUrl entity = url(source, "https://t.me/current");
        TLRPC.TL_channel chat = channel("current");
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        entities.add(entity);
        TelegramChannelFooterLinkClassifier.TrimResult result =
                TelegramChannelFooterLinkClassifier.classifyLastLine(source, entities, chat, null, JVM_MATCHER);
        assertTrue(result.isMatch());
        assertEquals("body\nhttps://t.me/current", source);
        assertEquals(1, entities.size());
        assertEquals("current", chat.username);
    }

    private static void assertMatch(String text, String visibleUrl, TLRPC.Chat chat, String runtimeHost, int start) {
        assertRange(text, singleton(url(text, visibleUrl)), chat, runtimeHost, start, text.length());
    }

    private static void assertSameChannelWithAdditionalUrlDoesNotMatch(String additionalUrl) {
        String text = "https://t.me/current " + additionalUrl;
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        entities.add(url(text, "https://t.me/current"));
        entities.add(url(text, additionalUrl));
        assertNoMatch(text, entities, channel("current"), null);
    }

    private static void assertSameChannelWithAdditionalUrlMatches(String additionalUrl) {
        String text = "https://t.me/current " + additionalUrl;
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        entities.add(url(text, "https://t.me/current"));
        entities.add(url(text, additionalUrl));
        assertRange(text, entities, channel("current"), null, 0, text.length());
    }

    private static void assertMatch(String text, TLRPC.MessageEntity entity, TLRPC.Chat chat, String runtimeHost, int start) {
        assertRange(text, singleton(entity), chat, runtimeHost, start, text.length());
    }

    private static void assertNoMatch(String text, TLRPC.MessageEntity entity, TLRPC.Chat chat, String runtimeHost) {
        assertNoMatch(text, singleton(entity), chat, runtimeHost);
    }

    private static void assertNoMatch(String text, List<TLRPC.MessageEntity> entities, TLRPC.Chat chat, String runtimeHost) {
        TelegramChannelFooterLinkClassifier.TrimResult result =
                TelegramChannelFooterLinkClassifier.classifyLastLine(text, entities, chat, runtimeHost, JVM_MATCHER);
        assertFalse(result.isMatch());
    }

    private static void assertNoMatchWithMatcher(String text, List<TLRPC.MessageEntity> entities, TLRPC.Chat chat, String runtimeHost, TelegramChannelFooterLinkClassifier.PublicLinkMatcher matcher) {
        TelegramChannelFooterLinkClassifier.TrimResult result =
                TelegramChannelFooterLinkClassifier.classifyLastLine(text, entities, chat, runtimeHost, matcher);
        assertFalse(result.isMatch());
    }

    private static void assertRange(String text, List<TLRPC.MessageEntity> entities, TLRPC.Chat chat, String runtimeHost, int start, int end) {
        TelegramChannelFooterLinkClassifier.TrimResult result =
                TelegramChannelFooterLinkClassifier.classifyLastLine(text, entities, chat, runtimeHost, JVM_MATCHER);
        assertTrue(result.isMatch());
        assertEquals(start, result.startInclusive);
        assertEquals(end, result.endExclusive);
    }

    private static ArrayList<TLRPC.MessageEntity> singleton(TLRPC.MessageEntity entity) {
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
        result.add(entity);
        return result;
    }

    private static TLRPC.TL_channel channel(String username) {
        TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.username = username;
        return channel;
    }

    private static TLRPC.TL_messageEntityUrl url(String text, String visibleUrl) {
        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
        entity.offset = text.indexOf(visibleUrl);
        entity.length = visibleUrl.length();
        return entity;
    }

    private static TLRPC.TL_messageEntityTextUrl textUrl(String text, String display) {
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = text.indexOf(display);
        entity.length = display.length();
        return entity;
    }

    private static boolean matchesPublicUsername(TLRPC.Chat chat, String username) {
        if (chat.username != null && chat.username.equalsIgnoreCase(username)) {
            return true;
        }
        if (chat.usernames != null) {
            for (TLRPC.TL_username candidate : chat.usernames) {
                if (candidate != null && candidate.active && candidate.username != null
                        && candidate.username.equalsIgnoreCase(username)) {
                    return true;
                }
            }
        }
        return false;
    }
}
