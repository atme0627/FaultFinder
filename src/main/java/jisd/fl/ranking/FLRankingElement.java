package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;

import java.util.Objects;

public class FLRankingElement implements Comparable<FLRankingElement> {
    public final CodeElementName e;
    public double suspScore;

    FLRankingElement(CodeElementName e, double suspScore) {
        this.e = e;
        this.suspScore = suspScore;
    }

    @Override
    public int compareTo(FLRankingElement o) {
        return isSameScore(o) ? e.compareTo(o.e) : -Double.compare(this.suspScore, o.suspScore);
    }

    //小数点以下4桁までで比較
    public boolean isSameScore(FLRankingElement e) {
        return String.format("%.4f", this.suspScore).equals(String.format("%.4f", e.suspScore));
    }

    public CodeElementName getCodeElementName() {
        return e;
    }

    public double getSuspiciousnessScore() {
        return suspScore;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FLRankingElement) obj;
        return Objects.equals(this.e, that.e) &&
                Double.doubleToLongBits(this.suspScore) == Double.doubleToLongBits(that.suspScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(e, suspScore);
    }

    @Override
    public String toString() {
        return "FLRankingElement[" +
                "e=" + e + ", " +
                "sbflScore=" + suspScore + ']';
    }


}
