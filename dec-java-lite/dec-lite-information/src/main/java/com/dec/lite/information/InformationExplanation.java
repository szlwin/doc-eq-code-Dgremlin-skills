package com.dec.lite.information;

import java.util.Set;

public record InformationExplanation(InformationKey key, InformationKind kind, Set<InformationKey> dependencies, Set<String> readPaths, String graphDigest) {
    public InformationExplanation { dependencies = Set.copyOf(dependencies); readPaths = Set.copyOf(readPaths); }
}
