package jisd.fl.core.domain;

import jisd.fl.core.entity.FLRanking;
import jisd.fl.core.entity.element.LineElementName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RemoveScoreCalculatorTest {
    private FLRanking ranking;
    private static final double REMOVE_CONST = 0.8;

    @BeforeEach
    void setUp() {
        ranking = new FLRanking();
        // 同じメソッド内の複数行
        ranking.add(new LineElementName("pkg.Class#method(int)", 10), 1.0);
        ranking.add(new LineElementName("pkg.Class#method(int)", 20), 0.8);
        ranking.add(new LineElementName("pkg.Class#method(int)", 30), 0.6);
        // 異なるメソッド（隣接でない）
        ranking.add(new LineElementName("pkg.Class#other()", 15), 0.5);
        ranking.add(new LineElementName("pkg.Other#method()", 10), 0.4);
        ranking.sort();
    }

    @Test
    void setsTargetScoreToZero() {
        LineElementName target = new LineElementName("pkg.Class#method(int)", 10);
        RemoveScoreCalculator calc = new RemoveScoreCalculator(REMOVE_CONST);

        calc.apply(target, ranking);

        assertEquals(0.0, ranking.getScore(target), 0.0001);
    }

    @Test
    void multipliesNeighborScoresByRemoveConst() {
        LineElementName target = new LineElementName("pkg.Class#method(int)", 10);
        LineElementName neighbor1 = new LineElementName("pkg.Class#method(int)", 20);
        LineElementName neighbor2 = new LineElementName("pkg.Class#method(int)", 30);

        RemoveScoreCalculator calc = new RemoveScoreCalculator(REMOVE_CONST);
        calc.apply(target, ranking);

        assertEquals(0.8 * REMOVE_CONST, ranking.getScore(neighbor1), 0.0001);
        assertEquals(0.6 * REMOVE_CONST, ranking.getScore(neighbor2), 0.0001);
    }

    @Test
    void doesNotAffectNonNeighborElements() {
        LineElementName target = new LineElementName("pkg.Class#method(int)", 10);
        LineElementName otherMethod = new LineElementName("pkg.Class#other()", 15);
        LineElementName otherClass = new LineElementName("pkg.Other#method()", 10);

        RemoveScoreCalculator calc = new RemoveScoreCalculator(REMOVE_CONST);
        calc.apply(target, ranking);

        assertEquals(0.5, ranking.getScore(otherMethod), 0.0001);
        assertEquals(0.4, ranking.getScore(otherClass), 0.0001);
    }
}
