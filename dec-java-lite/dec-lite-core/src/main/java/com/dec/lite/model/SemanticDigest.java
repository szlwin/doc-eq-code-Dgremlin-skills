package com.dec.lite.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/** Stable semantic digest, independent of source path, map order and presentation prose. */
public final class SemanticDigest {
    private SemanticDigest() { }
    public static String project(DecProject project) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            project.getDocuments().stream().map(SemanticDigest::document).sorted()
                    .forEach(part -> write(digest, part));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static String document(DecDocument document) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            write(digest, document.kind().value());
            write(digest, document.version());
            value(digest, document.root(), null);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static void value(MessageDigest digest, Object raw, String field) {
        if (raw instanceof Map<?, ?> map) {
            write(digest, "M");
            map.keySet().stream().map(String::valueOf)
                    .filter(key -> !List.of("desc", "description", "notes", "metadata").contains(key))
                    .sorted().forEach(key -> { write(digest, key); value(digest, map.get(key), key); });
        } else if (raw instanceof List<?> list) {
            write(digest, "L" + list.size());
            for (Object item : list) value(digest, item, field);
        } else if (raw instanceof Number number) {
            write(digest, "N" + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
        } else if (raw == null) write(digest, "Z");
        else if (raw instanceof Boolean bool) write(digest, "B" + bool);
        else {
            String text = String.valueOf(raw);
            if ("process".equals(field) || "changeData".equals(field)) text = text.stripTrailing();
            write(digest, "S" + text);
        }
    }
    private static void write(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
