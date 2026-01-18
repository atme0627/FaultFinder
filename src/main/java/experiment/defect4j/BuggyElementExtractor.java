package experiment.defect4j;

import jisd.fl.core.entity.LineElementName;
import jisd.fl.core.entity.MethodElementName;
import jisd.fl.infra.javaparser.JavaParserUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
修正前と修正後のコミットを比較して、修正前のうち、欠陥を含むプログラム要素を返す。
各ハンクについて行の削除がが伴う場合は、削除された行
追加のみの場合は、追加された行の直後の行を欠陥を含むとみなす。
 */
public class BuggyElementExtractor {
    // .git情報を持つオブジェクトを保持するフィールド
    private final Repository repository;

    public BuggyElementExtractor(Repository repository)  {
        this.repository = repository;
    }

    /* 二つのコミットのタグを受け取って、その差分から、欠陥を含む行を表すLineElementNameのlistを返す*/
    List<LineElementName> buggyLines(ObjectId buggyCommitId, ObjectId fixedCommitId) {
        try (Git git = new Git(repository)) {

            CanonicalTreeParser buggyTree = new CanonicalTreeParser();
            CanonicalTreeParser fixedTree = new CanonicalTreeParser();

            try (var reader = repository.newObjectReader()) {
                ObjectId buggyTreeId = repository.parseCommit(buggyCommitId).getTree().getId();
                ObjectId fixedTreeId = repository.parseCommit(fixedCommitId).getTree().getId();

                buggyTree.reset(reader, buggyTreeId);
                fixedTree.reset(reader, fixedTreeId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //ファイル単位の差分リストを取得
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(buggyTree)
                    .setNewTree(fixedTree)
                    .call();

            List<LineElementName> buggyLines = new ArrayList<>();

            //DiffFormatter: ファイル単位差分であるDiffEntryをハンク単位差分リストのEditListに変換するために使用
            // 出力先はダミーのByteArrayOutputStreamを使う。
            DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream());
            df.setRepository(repository);

            //DiffEntry: ハンク単位の変更差分
            //edit.getBeginA()などは0-indexedなので1を足す
            for (DiffEntry entry : diffs) {
                Optional<MethodElementName> fqcn = getFQCN(entry);
                if(fqcn.isEmpty()) continue;
                Map<Integer, MethodElementName> result = JavaParserUtils.getMethodNamesWithLine(fqcn.get());
                Map<Integer, MethodElementName> methodsWithLine = result;
                EditList edits = df.toFileHeader(entry).toEditList();
                for (Edit edit : edits) {
                    if (edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE) {
                        for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                            buggyLines.add(methodsWithLine.getOrDefault(i + 1, new MethodElementName(fqcn.get().fullyQualifiedClassName() + "#<ulinit>()")).toLineElementName(i + 1));
                        }
                    } else if (edit.getType() == Edit.Type.INSERT) {
                        int afterInsertedLine = edit.getBeginA();
                        buggyLines.add(methodsWithLine.getOrDefault(afterInsertedLine + 1, new MethodElementName(fqcn.get().fullyQualifiedClassName() + "#<ulinit>()")).toLineElementName(afterInsertedLine + 1));
                    }
                }
            }

            return buggyLines;


        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //ファイルパスからFQCNを推測
    private Optional<MethodElementName> getFQCN(DiffEntry entry){
        String path = entry.getOldPath();
        // "org/" の位置を探す
        int index = path.indexOf("org/");
        if (index != -1) {
            // "org/" 以降を取得
            return Optional.of(new MethodElementName(path.substring(index)
                    .replace('/', '.')
                    .replace(".java", "")));


        } else {
            System.err.println("Cannot get FQCN from path (not contain \"org/\"): " + path);
            return Optional.empty();
        }
    }
}
