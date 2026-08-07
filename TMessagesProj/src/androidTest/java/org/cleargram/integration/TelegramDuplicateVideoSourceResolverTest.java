package org.cleargram.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.TLRPC;

@RunWith(AndroidJUnit4.class)
public final class TelegramDuplicateVideoSourceResolverTest {

    @Test
    public void resolvesCompleteVideoAndPreservesExplicitAccount() throws Exception {
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "cleargram_resolver_" + System.nanoTime());
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[] { 1, 2, 3 }); }
        AtomicInteger calls = new AtomicInteger();
        TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((account, object) -> {
            assertEquals(7, account);
            calls.incrementAndGet();
            return file;
        });
        TelegramDuplicateVideoSourceResolution resolution = resolver.resolve(7, videoMessage(3));
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_AVAILABLE, resolution.getStatus());
        assertNotNull(resolution.getSourceOrNull());
        assertEquals(1, calls.get());
        try (InputStream input = resolution.getSourceOrNull().openStream()) { assertEquals(1, input.read()); }
        file.delete();
    }

    @Test
    public void resolvesCaptionedVideoAndVideoBackedGifButNotLegacyGif() throws Exception {
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "cleargram_resolver_gif_" + System.nanoTime());
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[] { 1, 2, 3 }); }
        TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((account, object) -> file);
        MessageObject captioned = videoMessage(3);
        captioned.messageOwner.message = "caption";
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_AVAILABLE,
                resolver.resolve(0, captioned).getStatus());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_AVAILABLE,
                resolver.resolve(0, gifMessage(3)).getStatus());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                resolver.resolve(0, legacyGifMessage(3)).getStatus());
        file.delete();
    }

    @Test
    public void rejectsUnsupportedAndUnavailableWithoutAttachPathFallback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((account, object) -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                resolver.resolve(0, genericDocumentMessage()).getStatus());
        assertEquals(0, calls.get());
        MessageObject video = videoMessage(3);
        video.messageOwner.attachPath = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "ignored_attach_path").getAbsolutePath();
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                resolver.resolve(0, video).getStatus());
        assertEquals(1, calls.get());
    }

    @Test
    public void validatesFailureBoundaryAndResolutionDto() {
        try { new TelegramDuplicateVideoSourceResolver(null); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
        try { new TelegramDuplicateVideoSourceResolver().resolve(0, null); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
        TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((a, object) -> {
            throw new IllegalStateException("lookup");
        });
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                resolver.resolve(0, videoMessage(3)).getStatus());
        AtomicInteger calls = new AtomicInteger();
        TelegramDuplicateVideoSourceResolver sizeResolver = new TelegramDuplicateVideoSourceResolver((a, object) -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                sizeResolver.resolve(0, videoMessage(0)).getStatus());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                sizeResolver.resolve(0, videoMessage(-1)).getStatus());
        assertEquals(0, calls.get());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                TelegramDuplicateVideoSourceResolution.unsupported().getStatus());
        assertTrue(TelegramDuplicateVideoSourceResolution.unsupported().getSourceOrNull() == null);
        try { TelegramDuplicateVideoSourceResolution.localAvailable(null); fail("Expected NullPointerException"); }
        catch (NullPointerException expected) { }
        assertTrue(!java.lang.reflect.Modifier.isPublic(TelegramDuplicateVideoSourceResolution.class.getModifiers()));
        assertTrue(!java.lang.reflect.Modifier.isPublic(TelegramDuplicateVideoSourceResolution.Status.class.getModifiers()));
    }

    @Test
    public void selectsCanonicalDirectoriesAndRejectsUnsupportedWithoutLookup() {
        TLRPC.TL_messageMediaDocument normalMedia = media(videoDocument(3, false));
        TLRPC.TL_messageMediaDocument gifMedia = media(videoDocument(3, true));
        assertEquals(FileLoader.MEDIA_DIR_VIDEO, TelegramDuplicateVideoSourceResolver.canonicalDirectoryType(
                normalMedia, normalMedia.document, TelegramDuplicateVideoSourceResolver.SupportedMediaKind.NORMAL_VIDEO));
        assertEquals(FileLoader.MEDIA_DIR_DOCUMENT, TelegramDuplicateVideoSourceResolver.canonicalDirectoryType(
                gifMedia, gifMedia.document, TelegramDuplicateVideoSourceResolver.SupportedMediaKind.VIDEO_BACKED_GIF));
        normalMedia.document.key = new byte[] { 1 };
        assertEquals(FileLoader.MEDIA_DIR_CACHE, TelegramDuplicateVideoSourceResolver.canonicalDirectoryType(
                normalMedia, normalMedia.document, TelegramDuplicateVideoSourceResolver.SupportedMediaKind.NORMAL_VIDEO));
        gifMedia.ttl_seconds = 1;
        assertEquals(FileLoader.MEDIA_DIR_CACHE, TelegramDuplicateVideoSourceResolver.canonicalDirectoryType(
                gifMedia, gifMedia.document, TelegramDuplicateVideoSourceResolver.SupportedMediaKind.VIDEO_BACKED_GIF));

        AtomicInteger calls = new AtomicInteger();
        TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((a, object) -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                resolver.resolve(0, genericDocumentMessage()).getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    public void rejectsDirectUnsupportedMediaWithoutLookupAndChecksAllFileStates() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TelegramDuplicateVideoSourceResolver unsupportedResolver = new TelegramDuplicateVideoSourceResolver((a, object) -> {
            calls.incrementAndGet(); return null;
        });
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                unsupportedResolver.resolve(0, roundVideoMessage()).getStatus());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                unsupportedResolver.resolve(0, photoMessage()).getStatus());
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.UNSUPPORTED,
                unsupportedResolver.resolve(0, noMediaMessage()).getStatus());
        assertEquals(0, calls.get());

        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File exact = new File(root, "cleargram_exact_" + System.nanoTime());
        try (FileOutputStream output = new FileOutputStream(exact)) { output.write(new byte[] { 1, 2, 3 }); }
        File shortFile = new File(root, exact.getName() + "_short");
        try (FileOutputStream output = new FileOutputStream(shortFile)) { output.write(new byte[] { 1, 2 }); }
        File longFile = new File(root, exact.getName() + "_long");
        try (FileOutputStream output = new FileOutputStream(longFile)) { output.write(new byte[] { 1, 2, 3, 4 }); }
        File directory = new File(root, exact.getName() + "_dir"); directory.mkdir();
        File[] paths = { null, new File(root, "missing_" + System.nanoTime()), directory, shortFile, longFile, exact };
        for (File path : paths) {
            AtomicInteger perCaseCalls = new AtomicInteger();
            TelegramDuplicateVideoSourceResolver resolver = new TelegramDuplicateVideoSourceResolver((a, object) -> {
                perCaseCalls.incrementAndGet(); return path;
            });
            TelegramDuplicateVideoSourceResolution.Status expected = path == exact
                    ? TelegramDuplicateVideoSourceResolution.Status.LOCAL_AVAILABLE
                    : TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE;
            assertEquals(expected, resolver.resolve(0, videoMessage(3)).getStatus());
            assertEquals(1, perCaseCalls.get());
        }
        MessageObject localPath = videoMessage(3);
        localPath.getDocument().localPath = exact.getAbsolutePath();
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                new TelegramDuplicateVideoSourceResolver((a, object) -> null).resolve(0, localPath).getStatus());
        exact.delete(); shortFile.delete(); longFile.delete(); directory.delete();
    }

    @Test
    public void lookupRuntimeFailsOpenButErrorEscapesWithoutRetry() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        assertEquals(TelegramDuplicateVideoSourceResolution.Status.LOCAL_UNAVAILABLE,
                new TelegramDuplicateVideoSourceResolver((a, object) -> {
                    runtimeCalls.incrementAndGet(); throw new IllegalStateException();
                }).resolve(0, videoMessage(3)).getStatus());
        assertEquals(1, runtimeCalls.get());
        AtomicInteger errorCalls = new AtomicInteger();
        try {
            new TelegramDuplicateVideoSourceResolver((a, object) -> {
                errorCalls.incrementAndGet(); throw new AssertionError();
            }).resolve(0, videoMessage(3));
            fail("Expected Error");
        } catch (AssertionError expected) { }
        assertEquals(1, errorCalls.get());
    }

    private static MessageObject videoMessage(long size) {
        return messageWithDocument(videoDocument(size, false));
    }

    private static TLRPC.TL_document videoDocument(long size, boolean animated) {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.size = size;
        document.mime_type = "video/mp4";
        TLRPC.TL_documentAttributeVideo attribute = new TLRPC.TL_documentAttributeVideo();
        attribute.w = 100;
        attribute.h = 100;
        document.attributes.add(attribute);
        if (animated) document.attributes.add(new TLRPC.TL_documentAttributeAnimated());
        return document;
    }

    private static MessageObject genericDocumentMessage() {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.size = 3;
        return messageWithDocument(document);
    }

    private static MessageObject gifMessage(long size) {
        return messageWithDocument(videoDocument(size, true));
    }

    private static MessageObject legacyGifMessage(long size) {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.size = size;
        document.mime_type = "image/gif";
        return messageWithDocument(document);
    }

    private static MessageObject roundVideoMessage() {
        TLRPC.TL_document document = videoDocument(3, false);
        ((TLRPC.TL_documentAttributeVideo) document.attributes.get(0)).round_message = true;
        return messageWithDocument(document);
    }
    private static MessageObject photoMessage() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.media = new TLRPC.TL_messageMediaPhoto();
        return new MessageObject(0, message, false, false);
    }
    private static MessageObject noMediaMessage() {
        return new MessageObject(0, new TLRPC.TL_message(), false, false);
    }

    private static MessageObject messageWithDocument(TLRPC.Document document) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.media = media(document);
        return new MessageObject(0, message, false, false);
    }

    private static TLRPC.TL_messageMediaDocument media(TLRPC.Document document) {
        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.document = document;
        return media;
    }
}
