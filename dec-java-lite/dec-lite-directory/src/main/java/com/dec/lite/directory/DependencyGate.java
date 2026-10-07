package com.dec.lite.directory;

import com.dec.lite.information.InformationEngine;
import com.dec.lite.information.InformationKey;
import com.dec.lite.information.ModelContext;
import com.dec.lite.information.RecognitionResult;
import java.util.List;

/** Dependency checks never materialize Information implicitly. */
public final class DependencyGate {
    public void verify(List<InformationKey> dependencies, InformationEngine information, ModelContext model) {
        for (InformationKey key : dependencies) {
            RecognitionResult result = information.evaluate(key, model);
            if (result.status() != RecognitionResult.Status.TRUE) {
                throw new DirectoryExecutionException("Dependency is not TRUE: " + key + " (" + result.status() + ")");
            }
        }
    }
}
