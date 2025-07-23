package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;
import java.util.Objects;

class ScoreAdjustment {
    private final CodeElementName element;
    private final double multiplier;

    /**
     * @param element the code element for which the score adjustment applies; must not be {@code null}
     * @param multiplier the multiplier value applied to the score adjustment
     */
    public ScoreAdjustment(CodeElementName element, double multiplier) {
        this.element = Objects.requireNonNull(element);
        this.multiplier = multiplier;
    }

    public CodeElementName getElement() {
        return element;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
