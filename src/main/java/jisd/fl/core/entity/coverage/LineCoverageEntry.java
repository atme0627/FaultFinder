package jisd.fl.core.entity.coverage;

import jisd.fl.core.entity.element.LineElementName;

public record LineCoverageEntry(LineElementName e, SbflCounts counts) {
}
