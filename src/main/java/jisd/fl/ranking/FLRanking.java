package jisd.fl.ranking;
import jisd.fl.core.entity.CodeElementIdentifier;
import jisd.fl.core.entity.FLRankingElement;

import java.util.*;
import java.util.stream.Collectors;

public class FLRanking {
    List<FLRankingElement> ranking = new ArrayList<>();

    public void add(CodeElementIdentifier element, double suspScore){
        ranking.add(new FLRankingElement(element, suspScore));
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

    public Optional<FLRankingElement> searchElement(CodeElementIdentifier target){
        for(FLRankingElement element : ranking){
            if(element.getCodeElementName().equals(target)) return Optional.of(element);
        }
        return Optional.empty();
    }


    public Set<CodeElementIdentifier> getAllElements(){
        return ranking.stream().map(FLRankingElement::getCodeElementName).collect(Collectors.toSet());
    }


    public void updateSuspiciousnessScore(CodeElementIdentifier target, double newScore){
        FLRankingElement e = searchElement(target).get();
        e.suspScore = newScore;
        sort();
    }
}
