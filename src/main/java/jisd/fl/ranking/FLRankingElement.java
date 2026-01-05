package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;

import java.util.Objects;

public class FLRankingElement implements Comparable<FLRankingElement> {
    public final CodeElementName element;
    public double suspScore;

    FLRankingElement(CodeElementName e, double suspScore) {
        this.element = e;
        this.suspScore = suspScore;
    }

    @Override
    public int compareTo(FLRankingElement o) {
        return isSameScore(o) ? element.compareTo(o.element) : -Double.compare(this.suspScore, o.suspScore);
    }

    //小数点以下4桁までで比較
    public boolean isSameScore(FLRankingElement e) {
        return String.format("%.4f", this.suspScore).equals(String.format("%.4f", e.suspScore));
    }

    public CodeElementName getCodeElementName() {
        return element;
    }

    public double getSuspiciousnessScore() {
        return suspScore;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FLRankingElement) obj;
        return Objects.equals(this.element, that.element) &&
                Double.doubleToLongBits(this.suspScore) == Double.doubleToLongBits(that.suspScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, suspScore);
    }

    @Override
    public String toString() {
        return "FLRankingElement[" +
                "e=" + element + ", " +
                "sbflScore=" + suspScore + ']';
    }


}
