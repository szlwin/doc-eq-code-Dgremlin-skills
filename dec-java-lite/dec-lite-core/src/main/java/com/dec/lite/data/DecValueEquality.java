package com.dec.lite.data;

import java.math.BigDecimal;
import java.util.Objects;

/** DEC equality compares numeric values independently of their Java wrapper or decimal scale. */
public final class DecValueEquality {
    private DecValueEquality() { }

    public static boolean equal(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number b)
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        return Objects.equals(actual, expected);
    }
}
