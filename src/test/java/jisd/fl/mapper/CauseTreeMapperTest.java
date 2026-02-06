package jisd.fl.mapper;

import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;
import jisd.fl.core.entity.susp.CauseTreeNode;
import jisd.fl.core.entity.susp.ExpressionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CauseTreeMapperTest {

    @Test
    void toJson_singleNode() {
        CauseTreeNode node = new CauseTreeNode(
                ExpressionType.ASSIGNMENT,
                new LineElementName(new MethodElementName("com.example.Foo#bar(int)"), 42),
                "x = y + 1",
                "13"
        );

        String json = CauseTreeMapper.toJson(node);

        assertTrue(json.contains("\"location\":"), "location field missing");
        assertTrue(json.contains("com.example.Foo#bar(int):42"), "location value wrong");
        assertTrue(json.contains("\"stmtString\":"), "stmtString field missing");
        assertTrue(json.contains("\"actualValue\":"), "actualValue field missing");
        assertTrue(json.contains("\"type\":"), "type field missing");
        assertTrue(json.contains("\"assignment\""), "type value wrong");
    }

    @Test
    void toJson_withChildren() {
        CauseTreeNode root = new CauseTreeNode(
                ExpressionType.ASSIGNMENT,
                new LineElementName(new MethodElementName("com.example.Foo#bar()"), 10),
                "x = calc()",
                "42"
        );
        CauseTreeNode child = new CauseTreeNode(
                ExpressionType.RETURN,
                new LineElementName(new MethodElementName("com.example.Foo#calc()"), 20),
                "return result",
                "42"
        );
        root.addChildNode(child);

        String json = CauseTreeMapper.toJson(root);

        assertTrue(json.contains("\"children\""));
        assertTrue(json.contains("\"type\": \"return\""));
    }

    @Test
    void fromJson_singleNode() {
        String json = """
                {
                  "location": "com.example.Foo#bar(int):42",
                  "stmtString": "x = y + 1",
                  "actualValue": "13",
                  "type": "assignment"
                }
                """;

        CauseTreeNode node = CauseTreeMapper.fromJson(json);

        assertEquals(ExpressionType.ASSIGNMENT, node.type());
        assertEquals(42, node.locateLine());
        assertEquals("com.example.Foo#bar(int)", node.locateMethod().fullyQualifiedName());
        assertEquals("x = y + 1", node.stmtString());
        assertEquals("13", node.actualValue());
        assertTrue(node.children().isEmpty());
    }

    @Test
    void fromJson_withChildren() {
        String json = """
                {
                  "location": "com.example.Foo#bar():10",
                  "stmtString": "x = calc()",
                  "actualValue": "42",
                  "children": [
                    {
                      "location": "com.example.Foo#calc():20",
                      "stmtString": "return result",
                      "actualValue": "42",
                      "type": "return"
                    }
                  ],
                  "type": "assignment"
                }
                """;

        CauseTreeNode root = CauseTreeMapper.fromJson(json);

        assertEquals(ExpressionType.ASSIGNMENT, root.type());
        assertEquals(1, root.children().size());

        CauseTreeNode child = root.children().getFirst();
        assertEquals(ExpressionType.RETURN, child.type());
        assertEquals(20, child.locateLine());
    }

    @Test
    void roundTrip() {
        CauseTreeNode original = new CauseTreeNode(
                ExpressionType.ARGUMENT,
                new LineElementName(new MethodElementName("com.example.Test#test()"), 100),
                "foo(x)",
                "5"
        );
        CauseTreeNode child1 = new CauseTreeNode(
                ExpressionType.ASSIGNMENT,
                new LineElementName(new MethodElementName("com.example.Foo#bar()"), 50),
                "y = x * 2",
                "10"
        );
        CauseTreeNode child2 = new CauseTreeNode(
                ExpressionType.RETURN,
                new LineElementName(new MethodElementName("com.example.Foo#baz()"), 60),
                "return y",
                "10"
        );
        original.addChildNode(child1);
        original.addChildNode(child2);

        String json = CauseTreeMapper.toJson(original);
        CauseTreeNode restored = CauseTreeMapper.fromJson(json);

        assertEquals(original.type(), restored.type());
        assertEquals(original.locateLine(), restored.locateLine());
        assertEquals(original.stmtString(), restored.stmtString());
        assertEquals(original.actualValue(), restored.actualValue());
        assertEquals(original.children().size(), restored.children().size());
    }
}
