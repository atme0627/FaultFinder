package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.MethodElementName;

public record MethodCoverageEntry(MethodElementName e, SbflCounts counts) {
}
