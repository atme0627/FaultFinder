package jisd.fl.core.entity;

import jisd.fl.core.entity.element.CodeElementIdentifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * 複数要素のスコアを一括で設定する。
     * @param newScores 要素と新しいスコアのマップ
     */
    public void setScores(Map<CodeElementIdentifier<?>, Double> newScores) {
        for (var entry : newScores.entrySet()) {
            searchElement(entry.getKey()).ifPresent(e -> e.suspScore = entry.getValue());
        }
        sort();
    }

    /**
     * 指定した要素のスコアを取得する。
     * @param target 対象要素
     * @return スコア（要素が存在しない場合は0.0）
     */
    public double getScore(CodeElementIdentifier<?> target) {
        return searchElement(target).map(e -> e.suspScore).orElse(0.0);
    }

    /**
     * 指定した要素の隣接要素を取得する。
     * @param target 対象要素
     * @return 隣接要素のセット（対象自身は含まない）
     */
    @SuppressWarnings("unchecked")
    public Set<CodeElementIdentifier<?>> getNeighborsOf(CodeElementIdentifier<?> target) {
        return ranking.stream()
                .map(e -> e.element)
                .filter(e -> isNeighborUnchecked(e, target) && !e.equals(target))
                .map(e -> (CodeElementIdentifier<?>) e)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean isNeighborUnchecked(CodeElementIdentifier a, CodeElementIdentifier b) {
        return a.isNeighbor(b);
    }
}
