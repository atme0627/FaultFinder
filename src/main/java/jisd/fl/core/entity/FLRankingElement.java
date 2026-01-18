package jisd.fl.core.entity;

import jisd.fl.core.entity.element.CodeElementIdentifier;

public class FLRankingElement implements Comparable<FLRankingElement> {
    final CodeElementIdentifier element;
    public double suspScore;

    public FLRankingElement(CodeElementIdentifier element, double suspScore) {
        this.element = element;
        this.suspScore = suspScore;
    }

    public CodeElementIdentifier getCodeElementName() {
        return element;
    }

    public double getSuspScore() {
        return suspScore;
    }

    @Override
    public int compareTo(FLRankingElement o) {
        return (this.suspScore == o.suspScore) ?
                this.element.toString().compareTo(o.element.toString())
                : Double.compare(this.suspScore, o.suspScore);
    }

    @Override
    public String toString() {
        return "FLRankingElement[" +
                "e=" + element + ", " +
                "sbflScore=" + suspScore + ']';
    }
}
