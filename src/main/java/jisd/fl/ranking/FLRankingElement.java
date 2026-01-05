package jisd.fl.ranking;
import jisd.fl.core.entity.CodeElementIdentifier;

public class FLRankingElement implements Comparable<FLRankingElement> {
    public final CodeElementIdentifier element;
    public double suspScore;

    FLRankingElement(CodeElementIdentifier e, double suspScore) {
        this.element = e;
        this.suspScore = suspScore;
    }

    @Override
    public int compareTo(FLRankingElement o) {
        if(Double.compare(this.suspScore, o.suspScore) != 0){
            return -Double.compare(this.suspScore, o.suspScore);
        }
        return element.compareTo(o.element);
    }

    public CodeElementIdentifier getCodeElementName() {
        return element;
    }

    public double getSuspScore() {
        return suspScore;
    }

    @Override
    public String toString() {
        return "FLRankingElement[" +
                "e=" + element + ", " +
                "sbflScore=" + suspScore + ']';
    }


}
