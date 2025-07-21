package jisd.fl.sbfl;

import jisd.fl.util.analyze.CodeElementName;

import java.util.Objects;

class FLRankingElement implements Comparable<FLRankingElement> {
    private final CodeElementName e;
    private double sbflScore;

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

    public void updateSuspiciousnessScore(double newScore){
        this.sbflScore = newScore;
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
