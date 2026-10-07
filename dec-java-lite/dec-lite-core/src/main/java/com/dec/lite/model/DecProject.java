package com.dec.lite.model;

import java.nio.file.Path;
import java.util.*;

public class DecProject {
    private final List<DecDocument> documents = new ArrayList<>();
    private final Map<String, DecDesignNode> nodesById = new LinkedHashMap<>();
    private final List<DesignGap> designGaps = new ArrayList<>();
    private volatile boolean frozen;

    public void addDocument(DecDocument document) {
        if (frozen) throw new IllegalStateException("canonical project is frozen");
        documents.add(Objects.requireNonNull(document, "document"));
        for (DecDesignNode node : document.nodes()) {
            DecDesignNode previous = nodesById.putIfAbsent(node.designId(), node);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate designId " + node.designId()
                        + " in " + previous.source() + " and " + node.source());
            }
        }
    }

    public void addGap(DesignGap gap) {
        if (frozen) throw new IllegalStateException("canonical project is frozen");
        designGaps.add(Objects.requireNonNull(gap, "gap"));
    }
    public DecProject freeze() { frozen = true; return this; }

    public List<DecDocument> getDocuments() {
        return List.copyOf(documents);
    }

    public Map<String, DecDesignNode> getNodesById() {
        return Collections.unmodifiableMap(nodesById);
    }

    public Optional<DecDesignNode> findDesignNode(String designId) {
        return Optional.ofNullable(nodesById.get(designId));
    }

    public List<DesignGap> getDesignGaps() {
        return List.copyOf(designGaps);
    }

    public Set<String> getDesignIds() {
        return Collections.unmodifiableSet(nodesById.keySet());
    }
}
