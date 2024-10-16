package probe;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;

import java.util.function.Predicate;

public class FailedAssertInfoFactory {
    public FailedAssertInfoFactory() {
    }

    public FailedAssertInfo create(String assertLine, Object actual, String path, String testName, int line) {
        //parse statement
        Statement assertStmt = StaticJavaParser.parseStatement(assertLine);
        MethodCallExpr methodCallExpr = new MethodCallExpr();

        methodCallExpr = assertStmt.findAll(methodCallExpr.getClass()).get(0);
        String methodName = methodCallExpr.getName().getIdentifier();
        NodeList<Expression> args = methodCallExpr.getArguments();

        switch(methodName) {
            case "assertEquals":
                return createFailedAssertEqualInfo(args, actual, path, testName, line);
            case "assertTrue":
                BinaryExpr  binaryExpr = args.get(0).asBinaryExpr();
                return createFailedAssertTrueInfo(binaryExpr, path, testName, line);

            default:
                throw new IllegalArgumentException("Unsupported assertType: " + methodName);
        }
    }

    // void assertEquals(Object expected, Object actual) のみ想定　
    private FailedAssertEqualInfo createFailedAssertEqualInfo(NodeList<Expression> args, Object actual, String path, String testName, int line){
        String variableName = getVariableNameFromArgs(args);
        Object expected = getLiteralFromArgs(args);
        return new FailedAssertEqualInfo(variableName, expected, expected.getClass().cast(actual), path, testName, line);
    }

    // void assertTrue(Boolean condition) のみ想定
    // conditionに含まれる変数は1つであることを想定　
    // 変数と整数値を比較する単純なものを想定 ex.) sum >= 10 非対応: 10 <= sum
    // literalはintにのみ対応
    // TODO: ほかの型にも対応させる
    // genericsでなんとかしようとするも断念
    // 現状、型ごとに対応するAssertInfoを作る必要がある

    private FailedAssertTrueInfo createFailedAssertTrueInfo(BinaryExpr binaryExpr, String path, String testName, int line){
        boolean expected = true;
        boolean actual = false;
        String variableName;
        int literal;
        Predicate<Integer> cond;
        BinaryExpr.Operator operator = binaryExpr.getOperator();

        //変数が左辺
        if(binaryExpr.getLeft().isNameExpr()) {
            variableName = binaryExpr.getLeft().asNameExpr().getNameAsString();
            literal = (int) getNumberLiteralFromLiteralExpr(binaryExpr.getRight());

            cond = getIntPredicate(literal, operator, true);
        }
        //変数が右辺
        else{
            variableName = binaryExpr.getRight().asNameExpr().getNameAsString();
            literal = (int) getNumberLiteralFromLiteralExpr(binaryExpr.getLeft());

            cond = getIntPredicate(literal, operator, true);
        }

        return new FailedAssertTrueInfo(variableName, path, testName, line, cond);
    }

    private String getVariableNameFromArgs(NodeList<Expression> args){
        for(Expression arg : args){
            if(arg.isNameExpr()){
                return arg.asNameExpr().getNameAsString();
            }
        }
        throw new IllegalArgumentException("Invalid assert args (No variable).");
    }

    //double, int, long対応
    //argの中にliteralは1つしかない想定
    private Object getLiteralFromArgs(NodeList<Expression> args){
        for(Expression arg : args){
            if(arg.isLiteralExpr()){
                return getNumberLiteralFromLiteralExpr(arg);
            }
        }
        throw new IllegalArgumentException("There is no literal in args.");
    }

    private Number getNumberLiteralFromLiteralExpr(Expression expr){

        if(expr.isDoubleLiteralExpr()){
            return expr.asDoubleLiteralExpr().asDouble();
        }

        if(expr.isIntegerLiteralExpr()){
            return (int) expr.asIntegerLiteralExpr().asNumber();
        }

        if(expr.isLongLiteralExpr()){
            return (Long) expr.asLongLiteralExpr().asNumber();
        }

        throw new IllegalArgumentException("Unsupported LiteralExpression.");
    }

    //TはComparableインターフェースを実装
    private Predicate<Integer> getIntPredicate(int literal, BinaryExpr.Operator operator, boolean isVariableLeft) {

        if (isVariableLeft) {
            switch (operator) {
                case EQUALS:
                    return x -> x.equals(literal);
                case NOT_EQUALS:
                    return x -> !x.equals(literal);
                case GREATER:
                    return x -> x.compareTo(literal) > 0;
                case GREATER_EQUALS:
                    return x -> x.compareTo(literal) >= 0;
                case LESS:
                    return x -> x.compareTo(literal) < 0;
                case LESS_EQUALS:
                    return x -> x.compareTo(literal) <= 0;
            }
        }
        else {
            switch (operator) {
                case EQUALS:
                    return x -> x.equals(literal);
                case NOT_EQUALS:
                    return x -> !x.equals(literal);
                case GREATER:
                    return x -> x.compareTo(literal) < 0;
                case GREATER_EQUALS:
                    return x ->x.compareTo(literal) <= 0;
                case LESS:
                    return x -> x.compareTo(literal) > 0;
                case LESS_EQUALS:
                    return x -> x.compareTo(literal) >= 0;
            }
        }

        throw new IllegalArgumentException("Unsupported operator of BinaryExpression.");
    }
}
