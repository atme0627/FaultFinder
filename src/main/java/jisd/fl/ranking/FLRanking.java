package jisd.fl.ranking;

import jisd.fl.sbfl.Formula;
import jisd.fl.sbfl.SbflStatus;
import jisd.fl.core.entity.CodeElementName;

import java.util.*;
import java.util.stream.Collectors;

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

    public Optional<FLRankingElement> searchElement(CodeElementName target){
        for(FLRankingElement element : ranking){
            if(element.getCodeElementName().equals(target)) return Optional.of(element);
        }
        return Optional.empty();
    }


    public Set<CodeElementName> getAllElements(){
        return ranking.stream().map(FLRankingElement::getCodeElementName).collect(Collectors.toSet());
    }


    public void updateSuspiciousnessScore(CodeElementName target, double newScore){
        FLRankingElement e = searchElement(target).get();
        e.suspScore = newScore;
        sort();
    }
}
