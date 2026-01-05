package jisd.fl.core.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FLRanking {
    List<FLRankingElement> ranking = new ArrayList<>();

    public void add(CodeElementIdentifier element, double suspScore){
        ranking.add(new jisd.fl.core.entity.FLRankingElement(element, suspScore));
    }

    public void sort(){
        ranking.sort(null);
    }

    public FLRankingElement at(int i){
        if(i < getSize()) return ranking.get(i);
        return null;
    }
    public int getSize(){
        return ranking.size();
    }

    public Optional<FLRankingElement> searchElement(CodeElementIdentifier target){
        for(FLRankingElement e : ranking){
            if(e.element.equals(target)) return Optional.of(e);
        }
        return Optional.empty();
    }

    public Set<CodeElementIdentifier> getAllElements(){
        return ranking.stream().map(e -> e.element).collect(Collectors.toSet());
    }


    public void updateSuspiciousnessScore(CodeElementIdentifier target, double newScore){
        FLRankingElement e = searchElement(target).get();
        e.suspScore = newScore;
        sort();
    }
}
