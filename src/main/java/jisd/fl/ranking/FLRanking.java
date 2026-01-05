package jisd.fl.ranking;

import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.sbfl.coverage.Granularity;
import jisd.fl.util.analyze.CodeElementName;

import java.util.*;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

import static java.lang.Math.min;

public class FLRanking {
    List<FLRankingElement> ranking = new ArrayList<>();

    public void add(CodeElementName element, SbflStatus status, Formula f){
        ranking.add(new FLRankingElement(element, status.getSuspiciousness(f)));
    }

    public void sort(){
        ranking.sort(FLRankingElement::compareTo);
    }

    public FLRankingElement at(int i){
        if(i < getSize()) return ranking.get(i);
        return null;
    }
    public int getSize(){
        return ranking.size();
    }

    Optional<FLRankingElement> searchElement(CodeElementName target){
        for(FLRankingElement element : ranking){
            if(element.getCodeElementName().equals(target)) return Optional.of(element);
        }
        return Optional.empty();
    }


    public Set<CodeElementName> getAllElements(){
        return ranking.stream().map(FLRankingElement::getCodeElementName).collect(Collectors.toSet());
    }


    public void updateSuspiciousnessScore(CodeElementName target, DoubleFunction<Double> f){
        FLRankingElement e = searchElement(target).get();
        e.sbflScore = f.apply(e.sbflScore);
    }


    /**
     * ランキングの要素を再計算
     * @param adjustments
     */
    public void adjustAll(Map<CodeElementName, Double> adjustments) {
        ScoreUpdateReport report = new ScoreUpdateReport();
        for ( Map.Entry<CodeElementName, Double> adj : adjustments.entrySet()) {
            Optional<FLRankingElement> target = searchElement(adj.getKey());
            if (target.isEmpty()) continue;
            report.recordChange(target.get());
            target.get().sbflScore *= adj.getValue();
        }
        report.print();
        sort();
    }

}
