package jisd.fl.core.entity;

class FLRankingElement implements Comparable<FLRankingElement> {
    final CodeElementIdentifier element;
    double suspScore;

    FLRankingElement(CodeElementIdentifier element, double suspScore) {
        this.element = element;
        this.suspScore = suspScore;
    }


    @Override
    public int compareTo(FLRankingElement o) {
        return (this.suspScore == o.suspScore) ?
                this.element.toString().compareTo(o.element.toString())
                : Double.compare(this.suspScore, o.suspScore);
    }
}
