package org.cleargram.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.cleargram.api.DuplicateVideoBatch;
import org.cleargram.api.DuplicateVideoItem;
import org.cleargram.api.DuplicateVideoMatchMode;

/** Stateless synchronous builder of Duplicate Video matching batches. */
public final class DuplicateVideoHashingComponent {

    private static final int DIGEST_LENGTH = 32;
    private static final byte ZERO = 0;
    private static final byte[] LEGACY_HASH_DOMAIN_PREFIX = "Noisegram".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DUPLICATE_VIDEO = "DuplicateVideo".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VIDEO_AND_TEXT = "VIDEO_AND_TEXT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VERSION = "v1".getBytes(StandardCharsets.US_ASCII);

    interface DigestFactory {
        MessageDigest create();
    }

    private final DigestFactory digestFactory;

    public DuplicateVideoHashingComponent() {
        this(() -> {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        });
    }

    DuplicateVideoHashingComponent(DigestFactory digestFactory) {
        this.digestFactory = Objects.requireNonNull(digestFactory, "digestFactory");
    }

    public DuplicateVideoHashingResult hash(DuplicateVideoHashingRequest request) {
        Objects.requireNonNull(request, "request");
        byte[] textDigest = null;
        if (request.getMatchMode() == DuplicateVideoMatchMode.VIDEO_AND_TEXT) {
            try {
                textDigest = digest(request.getLogicalText().getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                return allFailed(request);
            }
        }

        List<DuplicateVideoItem> successfulItems = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        for (DuplicateVideoHashingItem item : request.getItems()) {
            try {
                byte[] videoDigest = digestStream(item);
                byte[] matchKey = request.getMatchMode() == DuplicateVideoMatchMode.VIDEO
                        ? videoDigest : digestEnvelope(videoDigest, textDigest);
                successfulItems.add(new DuplicateVideoItem(item.getPresentationItemId(), matchKey));
            } catch (IOException | RuntimeException exception) {
                failedIds.add(item.getPresentationItemId());
            }
        }
        if (successfulItems.isEmpty()) {
            return new DuplicateVideoHashingResult(null, failedIds);
        }
        return new DuplicateVideoHashingResult(new DuplicateVideoBatch(
                request.getMatchMode(), request.getKeyVersion(), successfulItems,
                request.hasOtherVisibleMedia() || !failedIds.isEmpty()), failedIds);
    }

    private DuplicateVideoHashingResult allFailed(DuplicateVideoHashingRequest request) {
        List<Long> failedIds = new ArrayList<>(request.getItems().size());
        for (DuplicateVideoHashingItem item : request.getItems()) {
            failedIds.add(item.getPresentationItemId());
        }
        return new DuplicateVideoHashingResult(null, failedIds);
    }

    private byte[] digestStream(DuplicateVideoHashingItem item) throws IOException {
        MessageDigest digest = createDigest();
        try (InputStream stream = item.getSource().openStream()) {
            if (stream == null) {
                throw new IOException("source returned null stream");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return completedDigest(digest);
    }

    private byte[] digestEnvelope(byte[] videoDigest, byte[] textDigest) {
        MessageDigest digest = createDigest();
        digest.update(LEGACY_HASH_DOMAIN_PREFIX);
        digest.update(ZERO);
        digest.update(DUPLICATE_VIDEO);
        digest.update(ZERO);
        digest.update(VIDEO_AND_TEXT);
        digest.update(ZERO);
        digest.update(VERSION);
        digest.update(ZERO);
        digest.update(videoDigest);
        digest.update(textDigest);
        return completedDigest(digest);
    }

    private byte[] digest(byte[] bytes) {
        MessageDigest digest = createDigest();
        digest.update(bytes);
        return completedDigest(digest);
    }

    private MessageDigest createDigest() {
        MessageDigest digest = digestFactory.create();
        if (digest == null) {
            throw new IllegalStateException("digestFactory returned null");
        }
        return digest;
    }

    private static byte[] completedDigest(MessageDigest digest) {
        byte[] result = digest.digest();
        if (result == null || result.length != DIGEST_LENGTH) {
            throw new IllegalStateException("invalid SHA-256 digest");
        }
        return result;
    }
}
