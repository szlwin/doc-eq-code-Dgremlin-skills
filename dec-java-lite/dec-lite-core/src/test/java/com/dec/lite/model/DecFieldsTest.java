package com.dec.lite.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecFieldsTest {
    @Test void rejectsNonStringKeysAndMissingRequiredValues() {
        IllegalArgumentException key = assertThrows(IllegalArgumentException.class,
                () -> DecFields.map(Map.of(1, "x"), "Information", IllegalArgumentException::new));
        assertEquals("Information keys must be strings", key.getMessage());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> DecFields.requiredText(Map.of("name", " "), "name", "Directory", IllegalArgumentException::new));
        assertEquals("Directory requires name", missing.getMessage());
    }
}
