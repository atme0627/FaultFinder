package jisd.fl.core.entity;

import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FLRanking {
    List<FLRankingElement> ranking = new ArrayList<>();

    public void add(CodeElementIdentifier element, double suspScore){
        ranking.add(new jisd.fl.core.entity.FLRankingElement(element, suspScore));
    }

    /** スコア降順、同スコア内は要素の自然順（line昇順） */
    public void sort(){
        ranking.sort(Comparator.comparingDouble(FLRankingElement::getSuspScore).reversed()
                .thenComparing(Comparator.naturalOrder()));
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
