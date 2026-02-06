package jisd.fl.presenter;

import jisd.fl.core.entity.coverage.*;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SbflCoverageSerializerTest {

    @Test
    void writesHeaderAndMetadata() throws IOException {
        SbflCoverageProvider coverage = createTestCoverage();
        StringWriter writer = new StringWriter();

        SbflCoverageSerializer.write(coverage, writer);

        String result = writer.toString();
        String[] lines = result.split("\n");
        assertEquals("# FaultFinder SBFL Coverage v1", lines[0]);
        assertEquals("# totalPass=5,totalFail=2", lines[1]);
    }

    @Test
    void writesDataRowsCorrectly() throws IOException {
        SbflCoverageProvider coverage = createTestCoverage();
        StringWriter writer = new StringWriter();

        SbflCoverageSerializer.write(coverage, writer);

        String result = writer.toString();
        String[] lines = result.split("\n");
        assertEquals(4, lines.length);
        assertEquals("\"com.example.Foo#bar(int, String)\",12,3,1", lines[2]);
        assertEquals("\"com.example.Foo#bar(int, String)\",13,5,2", lines[3]);
    }

    @Test
    void readsValidCsvCorrectly() throws IOException {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2
                "com.example.Foo#bar(int, String)",12,3,1
                "com.example.Foo#bar(int, String)",13,5,2
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        RestoredSbflCoverage restored = SbflCoverageSerializer.read(reader);

        assertEquals(5, restored.totalPass());
        assertEquals(2, restored.totalFail());

        List<LineCoverageEntry> entries = restored.lineCoverageEntries(false).toList();
        assertEquals(2, entries.size());

        LineCoverageEntry first = entries.get(0);
        assertEquals("com.example.Foo#bar(int, String)", first.e().methodElementName.fullyQualifiedName());
        assertEquals(12, first.e().line);
        assertEquals(3, first.counts().ep());
        assertEquals(1, first.counts().ef());
        assertEquals(2, first.counts().np());
        assertEquals(1, first.counts().nf());
    }

    @Test
    void roundTripPreservesData() throws IOException {
        SbflCoverageProvider original = createTestCoverage();
        StringWriter writer = new StringWriter();

        SbflCoverageSerializer.write(original, writer);
        RestoredSbflCoverage restored = SbflCoverageSerializer.read(
                new BufferedReader(new StringReader(writer.toString())));

        List<LineCoverageEntry> originalEntries = original.lineCoverageEntries(false).toList();
        List<LineCoverageEntry> restoredEntries = restored.lineCoverageEntries(false).toList();

        assertEquals(originalEntries.size(), restoredEntries.size());
        for (int i = 0; i < originalEntries.size(); i++) {
            LineCoverageEntry o = originalEntries.get(i);
            LineCoverageEntry r = restoredEntries.get(i);
            assertEquals(o.e().methodElementName.fullyQualifiedName(),
                    r.e().methodElementName.fullyQualifiedName());
            assertEquals(o.e().line, r.e().line);
            assertEquals(o.counts().ep(), r.counts().ep());
            assertEquals(o.counts().ef(), r.counts().ef());
            assertEquals(o.counts().np(), r.counts().np());
            assertEquals(o.counts().nf(), r.counts().nf());
        }
    }

    @Test
    void throwsOnInvalidHeader() {
        String csv = """
                # Invalid Header
                # totalPass=5,totalFail=2
                "com.example.Foo#bar()",12,3,1
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        IOException ex = assertThrows(IOException.class, () ->
                SbflCoverageSerializer.read(reader));
        assertTrue(ex.getMessage().contains("Invalid header"));
    }

    @Test
    void throwsOnMissingMetadata() {
        String csv = """
                # FaultFinder SBFL Coverage v1
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        IOException ex = assertThrows(IOException.class, () ->
                SbflCoverageSerializer.read(reader));
        assertTrue(ex.getMessage().contains("Missing metadata"));
    }

    @Test
    void throwsOnInvalidMetadataFormat() {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # invalid metadata
                "com.example.Foo#bar()",12,3,1
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        IOException ex = assertThrows(IOException.class, () ->
                SbflCoverageSerializer.read(reader));
        assertTrue(ex.getMessage().contains("Invalid metadata format"));
    }

    @Test
    void throwsOnInvalidDataFormat() {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2
                invalid,data,line
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        IOException ex = assertThrows(IOException.class, () ->
                SbflCoverageSerializer.read(reader));
        assertTrue(ex.getMessage().contains("Invalid data format"));
    }

    @Test
    void skipsBlankLines() throws IOException {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2

                "com.example.Foo#bar()",12,3,1

                "com.example.Foo#bar()",13,5,2

                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        RestoredSbflCoverage restored = SbflCoverageSerializer.read(reader);
        List<LineCoverageEntry> entries = restored.lineCoverageEntries(false).toList();
        assertEquals(2, entries.size());
    }

    @Test
    void methodCoverageEntriesAggregatesFromLineEntries() throws IOException {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2
                "com.example.Foo#bar()",10,3,1
                "com.example.Foo#bar()",11,5,2
                "com.example.Foo#baz()",20,2,0
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        RestoredSbflCoverage restored = SbflCoverageSerializer.read(reader);
        List<MethodCoverageEntry> methodEntries = restored.methodCoverageEntries(false).toList();

        assertEquals(2, methodEntries.size());

        MethodCoverageEntry bar = methodEntries.stream()
                .filter(e -> e.e().shortMethodName().equals("bar"))
                .findFirst().orElseThrow();
        assertEquals(5, bar.counts().ep());
        assertEquals(2, bar.counts().ef());
    }

    @Test
    void classCoverageEntriesAggregatesFromLineEntries() throws IOException {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2
                "com.example.Foo#bar()",10,3,1
                "com.example.Foo#baz()",20,5,2
                "com.example.Other#qux()",30,1,0
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        RestoredSbflCoverage restored = SbflCoverageSerializer.read(reader);
        List<ClassCoverageEntry> classEntries = restored.classCoverageEntries().toList();

        assertEquals(2, classEntries.size());

        ClassCoverageEntry foo = classEntries.stream()
                .filter(e -> e.e().fullyQualifiedName().equals("com.example.Foo"))
                .findFirst().orElseThrow();
        assertEquals(5, foo.counts().ep());
        assertEquals(2, foo.counts().ef());
    }

    @Test
    void hideZeroElementsFiltersCorrectly() throws IOException {
        String csv = """
                # FaultFinder SBFL Coverage v1
                # totalPass=5,totalFail=2
                "com.example.Foo#bar()",10,0,0
                "com.example.Foo#bar()",11,3,1
                """;
        BufferedReader reader = new BufferedReader(new StringReader(csv));

        RestoredSbflCoverage restored = SbflCoverageSerializer.read(reader);

        List<LineCoverageEntry> all = restored.lineCoverageEntries(false).toList();
        assertEquals(2, all.size());

        List<LineCoverageEntry> nonZero = restored.lineCoverageEntries(true).toList();
        assertEquals(1, nonZero.size());
        assertEquals(11, nonZero.get(0).e().line);
    }

    @Test
    void writesEmptyCoverageCorrectly() throws IOException {
        SbflCoverageProvider emptyCoverage = new SbflCoverageProvider() {
            @Override
            public Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements) {
                return Stream.empty();
            }

            @Override
            public Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements) {
                return Stream.empty();
            }

            @Override
            public Stream<ClassCoverageEntry> classCoverageEntries() {
                return Stream.empty();
            }
        };
        StringWriter writer = new StringWriter();

        SbflCoverageSerializer.write(emptyCoverage, writer);

        String result = writer.toString();
        String[] lines = result.split("\n");
        assertEquals(2, lines.length);
        assertEquals("# FaultFinder SBFL Coverage v1", lines[0]);
        assertEquals("# totalPass=0,totalFail=0", lines[1]);
    }

    private SbflCoverageProvider createTestCoverage() {
        MethodElementName method = new MethodElementName("com.example.Foo#bar(int, String)");
        List<LineCoverageEntry> entries = List.of(
                new LineCoverageEntry(new LineElementName(method, 12), new SbflCounts(3, 1, 2, 1)),
                new LineCoverageEntry(new LineElementName(method, 13), new SbflCounts(5, 2, 0, 0))
        );

        return new SbflCoverageProvider() {
            @Override
            public Stream<LineCoverageEntry> lineCoverageEntries(boolean hideZeroElements) {
                return entries.stream();
            }

            @Override
            public Stream<MethodCoverageEntry> methodCoverageEntries(boolean hideZeroElements) {
                return Stream.empty();
            }

            @Override
            public Stream<ClassCoverageEntry> classCoverageEntries() {
                return Stream.empty();
            }
        };
    }
}
