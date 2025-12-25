package jisd.fl.ranking;

import jisd.fl.util.analyze.CodeElementName;

import java.util.Objects;

public class FLRankingElement implements Comparable<FLRankingElement> {
    public final CodeElementName e;
    public double sbflScore;

    FLRankingElement(CodeElementName e, double sbflScore) {
        this.e = e;
        this.sbflScore = sbflScore;
    }

    @Override
    public int compareTo(FLRankingElement o) {
        return isSameScore(o) ? e.compareTo(o.e) : -Double.compare(this.sbflScore, o.sbflScore);
    }

    //小数点以下4桁までで比較
    boolean isSameScore(FLRankingElement e) {
        return String.format("%.4f", this.sbflScore).equals(String.format("%.4f", e.sbflScore));
    }

    public CodeElementName getCodeElementName() {
        return e;
    }

    public double getSuspiciousnessScore() {
        return sbflScore;
    }

    //与えられた要素の周辺要素であるかを判定
    public boolean isNeighbor(FLRankingElement target){
        return this.getCodeElementName().isNeighbor(target.getCodeElementName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FLRankingElement) obj;
        return Objects.equals(this.e, that.e) &&
                Double.doubleToLongBits(this.sbflScore) == Double.doubleToLongBits(that.sbflScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(e, sbflScore);
    }

    @Override
    public String toString() {
        return "FLRankingElement[" +
                "e=" + e + ", " +
                "sbflScore=" + sbflScore + ']';
    }


}
