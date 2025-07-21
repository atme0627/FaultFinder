package jisd.fl.sbfl;

import jisd.fl.util.analyze.CodeElementName;

record FLRankingElement(CodeElementName e, double sbflScore) implements Comparable<FLRankingElement> {
    @Override
    public int compareTo(FLRankingElement o) {
        return isSameScore(o) ? e.compareTo(o.e) : -Double.compare(this.sbflScore, o.sbflScore);
    }

    //小数点以下4桁までで比較
    boolean isSameScore(FLRankingElement e) {
        return String.format("%.4f", this.sbflScore).equals(String.format("%.4f", e.sbflScore));
    }
}
