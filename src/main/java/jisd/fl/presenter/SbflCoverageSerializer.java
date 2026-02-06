package jisd.fl.presenter;

import jisd.fl.core.entity.coverage.LineCoverageEntry;
import jisd.fl.core.entity.coverage.RestoredSbflCoverage;
import jisd.fl.core.entity.coverage.SbflCounts;
import jisd.fl.core.entity.coverage.SbflCoverageProvider;
import jisd.fl.core.entity.element.LineElementName;
import jisd.fl.core.entity.element.MethodElementName;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SBFL カバレッジデータの CSV シリアライズ / デシリアライズを行うユーティリティクラス。
 *
 * <p>CSV フォーマット:</p>
 * <pre>
 * # FaultFinder SBFL Coverage v1
 * # totalPass=5,totalFail=2
 * "com.example.Foo#bar(int, String)",12,3,1
 * "com.example.Foo#bar(int, String)",13,5,2
 * </pre>
 */
public final class SbflCoverageSerializer {

    private static final String HEADER = "# FaultFinder SBFL Coverage v1";
    private static final String META_PREFIX = "# totalPass=";
    private static final Pattern META_PATTERN = Pattern.compile("# totalPass=(\\d+),totalFail=(\\d+)");
    private static final Pattern DATA_PATTERN = Pattern.compile("\"([^\"]+)\",(\\d+),(\\d+),(\\d+)");

    private SbflCoverageSerializer() {
    }

    // ========== Writer ==========

    /**
     * カバレッジデータを CSV ファイルに書き出す。
     *
     * @param coverage   書き出すカバレッジデータ
     * @param outputPath 出力先のパス
     * @throws IOException 書き出し失敗時
     */
    public static void write(SbflCoverageProvider coverage, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            write(coverage, writer);
        }
    }

    /**
     * カバレッジデータを Writer に書き出す。
     *
     * @param coverage カバレッジデータ
     * @param writer   出力先
     * @throws IOException 書き出し失敗時
     */
    public static void write(SbflCoverageProvider coverage, Writer writer) throws IOException {
        List<LineCoverageEntry> entries = coverage.lineCoverageEntries(false).toList();

        int totalPass = 0;
        int totalFail = 0;
        if (!entries.isEmpty()) {
            SbflCounts first = entries.get(0).counts();
            totalPass = first.ep() + first.np();
            totalFail = first.ef() + first.nf();
        }

        writer.write(HEADER);
        writer.write("\n");
        writer.write(META_PREFIX + totalPass + ",totalFail=" + totalFail);
        writer.write("\n");

        for (LineCoverageEntry entry : entries) {
            String methodFqn = entry.e().methodElementName.fullyQualifiedName();
            int line = entry.e().line;
            SbflCounts c = entry.counts();
            writer.write("\"" + methodFqn + "\"," + line + "," + c.ep() + "," + c.ef());
            writer.write("\n");
        }
    }

    // ========== Reader ==========

    /**
     * CSV ファイルからカバレッジデータを読み込む。
     *
     * @param inputPath 入力ファイルのパス
     * @return 復元したカバレッジデータ
     * @throws IOException 読み込み失敗時または不正なフォーマット時
     */
    public static RestoredSbflCoverage read(Path inputPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * BufferedReader からカバレッジデータを読み込む。
     *
     * @param reader 入力元
     * @return 復元したカバレッジデータ
     * @throws IOException 読み込み失敗時または不正なフォーマット時
     */
    public static RestoredSbflCoverage read(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null || !headerLine.equals(HEADER)) {
            throw new IOException("Invalid header: expected '" + HEADER + "', got '" + headerLine + "'");
        }

        String metaLine = reader.readLine();
        if (metaLine == null) {
            throw new IOException("Missing metadata line");
        }
        Matcher metaMatcher = META_PATTERN.matcher(metaLine);
        if (!metaMatcher.matches()) {
            throw new IOException("Invalid metadata format: " + metaLine);
        }
        int totalPass = Integer.parseInt(metaMatcher.group(1));
        int totalFail = Integer.parseInt(metaMatcher.group(2));

        List<LineCoverageEntry> entries = new ArrayList<>();
        String line;
        int lineNumber = 2;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;

            Matcher dataMatcher = DATA_PATTERN.matcher(line);
            if (!dataMatcher.matches()) {
                throw new IOException("Invalid data format at line " + lineNumber + ": " + line);
            }

            String methodFqn = dataMatcher.group(1);
            int lineNo = Integer.parseInt(dataMatcher.group(2));
            int ep = Integer.parseInt(dataMatcher.group(3));
            int ef = Integer.parseInt(dataMatcher.group(4));
            int np = totalPass - ep;
            int nf = totalFail - ef;

            MethodElementName methodName = new MethodElementName(methodFqn);
            LineElementName lineElementName = new LineElementName(methodName, lineNo);
            SbflCounts counts = new SbflCounts(ep, ef, np, nf);
            entries.add(new LineCoverageEntry(lineElementName, counts));
        }

        return new RestoredSbflCoverage(totalPass, totalFail, entries);
    }
}
