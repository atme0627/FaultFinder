package jisd.fl.sbfl;

import jisd.fl.coverage.CoverageCollection;
import jisd.fl.coverage.Granularity;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

import static java.lang.Math.min;

public class FaultFinder {

    List<Pair<String, Double>> sbflResult;

    public FaultFinder(CoverageCollection covForTestSuite, Granularity granularity, Formula f){
        calcSuspiciousness(covForTestSuite, granularity, f);
    }

    private void calcSuspiciousness(CoverageCollection covForTestSuite, Granularity granularity, Formula f){
        //init
        sbflResult = new ArrayList<>();

        Set<String> targetClassNames = covForTestSuite.getTargetClassNames();
        for(String targetClassName : targetClassNames){
            Map<String, SbflStatus> covData = covForTestSuite.getCoverageOfTarget(targetClassName, granularity);
            calcSuspiciousnessOfTarget(targetClassName, covData, f, granularity);
        }

        sbflResult.sort((o1, o2)->{
            return o2.getRight().compareTo(o1.getRight());
        });
    }

    private void calcSuspiciousnessOfTarget(String targetClassName, Map<String, SbflStatus> covData, Formula f, Granularity granularity){
        covData.forEach((element, status) ->{
            if(granularity == Granularity.LINE) element = targetClassName + " --- " + element;
            Pair<String, Double> p = Pair.of(element, status.getSuspiciousness(f));
            if(!p.getRight().isNaN()){
                sbflResult.add(p);
            }
        });
    }


    public List<Pair<String, Double>> getFLResults(){
        return sbflResult;
    }


    public void printFLResults() {
        printFLResults(sbflResult.size());
    }

    public void printFLResults(int top){
        for(int i = 0; i < min(top, sbflResult.size()); i++){
            Pair<String, Double> element = sbflResult.get(i);
            System.out.println((i+1) + ": " + element.getLeft() + "  susp: " + element.getRight());
        }
    }
}
