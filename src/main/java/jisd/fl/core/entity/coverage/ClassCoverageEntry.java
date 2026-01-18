package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.ClassElementName;

public record ClassCoverageEntry(ClassElementName e, SbflCounts counts) {
}
