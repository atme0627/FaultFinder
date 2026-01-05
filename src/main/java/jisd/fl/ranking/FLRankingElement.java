package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;

public class FLRankingElement implements Comparable<FLRankingElement> {
    public final CodeElementName element;
    public double suspScore;

    FLRankingElement(CodeElementName e, double suspScore) {
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

    public CodeElementName getCodeElementName() {
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
