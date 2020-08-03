package org.osu;

import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.DependencyUpdateOuterClass;
import com.t2r.common.models.refactorings.FileDiffOuterClass;
import com.t2r.common.models.refactorings.JarInfoOuterClass;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.utilities.ProtoUtil.ReadWriteAt;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.main.parse.DownloadAllJarClient.downloadDependencies;
import static com.t2r.common.models.refactorings.JarInfoOuterClass.*;
import static com.t2r.common.utilities.FileUtils.parseCsv;
import static com.t2r.common.utilities.GitUtil.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.eclipse.jgit.revwalk.RevSort.COMMIT_TIME_DESC;
import static org.osu.ChangeMiner.getDepUpd;
import static org.osu.ChangeMiner.getDependenciesFromEffectivePom;

public class TypeFactMiner {
    private static final Logger LOGGER = Logger.getLogger("Runner");
    static FileHandler fh;
    public static Properties prop;
    public static Path pathToCorpus;
    public static Path projectList;
    public static Function<String,Path> projectPath;
    public static Date epochStart;
    public static Path outputFolder;
    public static ReadWriteAt readWriteInputProtos;
    public static Path pathToSetup;
    public static Path pathToInput;
    public static Path pathToDependencies;
    public static ReadWriteAt readWriteOutputProtos;
    public static ReadWriteAt readWriteCodeMappingProtos;
    public static ReadWriteAt readWriteVerificationProtos;
    public static ReadWriteAt readWriteMigrationProtos;
    public static GraphTraversalSource gr;
    public static String mavenHome;

    static{
        try {
            gr = traversal().withRemote(DriverRemoteConnection.using("localhost", 8182, "g"));
            fh = new FileHandler("./Runner.log");
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            prop = new Properties();
            InputStream input = new FileInputStream("paths.properties");
            prop.load(input);
            pathToSetup=Paths.get(prop.getProperty("PathToSetup")).resolve("TypeChangeMiner");
            pathToCorpus = pathToSetup.getParent().resolve(prop.getProperty("PathToCorpus"));
            projectList = pathToCorpus.resolve(prop.getProperty("InputProjects"));
            projectPath = p -> pathToCorpus.resolve("Project_"+p).resolve(p);
            epochStart = new SimpleDateFormat("YYYY-mm-dd").parse(prop.getProperty("epoch"));
            outputFolder = pathToSetup.resolve(prop.getProperty("PathToOutput"));
            pathToInput = pathToSetup.resolve(prop.getProperty("PathToInput"));
            readWriteInputProtos = new ReadWriteAt(pathToInput.resolve("ProtosOut"));
            readWriteOutputProtos = new ReadWriteAt(outputFolder);
            readWriteCodeMappingProtos = new ReadWriteAt(outputFolder.resolve("CodeMapping"));
            readWriteMigrationProtos = new ReadWriteAt(outputFolder.resolve("Migration"));
            readWriteVerificationProtos = new ReadWriteAt(outputFolder.resolve("Verification"));
            pathToDependencies = pathToInput.resolve("dependencies");
            mavenHome = prop.getProperty("mavenHome");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)  {
        List<Tuple2<String, String>> projects = parseCsv(projectList, x -> Tuple.of(x[0], x[1]));
        for(Tuple2<String, String> p : projects){
            AnalyzeProjects(p._1(), p._2());
        }
    }


    public static void AnalyzeProjects(String projectName, String cloneUrl){
        var p =Tuple.of(projectName, cloneUrl);
        Tuple2<Tuple2<String, String>, Git> project_git = p.map((x1, x2) -> Tuple.of(Tuple.of(x1, x2), tryToClone(x2, projectPath.apply(x1))))
                .map2(tg -> tg.getOrNull());
        if(project_git._2() != null){

            LOGGER.info("Analysing project : " + project_git._1()._1());

            List<RevCommit> commits = getCommits(project_git._2(), COMMIT_TIME_DESC, epochStart, new ArrayList<>(), new ArrayList<>());
            Project project = getProject(project_git._1(), commits.size());
            Set<JarInfo> jarsBefore = new HashSet<>();
            List<RevCommit> commitstoAnalyze = new ArrayList<>(filterAlreadyProcessed(commits, project_git._1()._1()));
            readWriteInputProtos.write(project, "projects", true);
            int counter = 0;

            for(var commit: commitstoAnalyze) {
                AnalyzeCommit(commit, project_git.map1(x -> project), jarsBefore);
            }
        }
    }


    public static void AnalyzeCommit(RevCommit commit,Tuple2<Project, Git> project_git, Set<JarInfo> jarsBefore){
        String sha = commit.toObjectId().getName();
        Git git = project_git._2();
        LOGGER.info("Analysing commit: " + sha);
        ChangeMiner ct = new ChangeMiner(project_git._1().getUrl());
        GitHistoryRefactoringMiner g = new GitHistoryRefactoringMinerImpl();
        try {
            g.detectAtCommit(git.getRepository(), sha, ct, 120);
            CommitInfo commitInfo = getCommitInfo(project_git._2(), sha,  jarsBefore, ct.getRefactoringsReported(), ct.getException());
            readWriteInputProtos.write(commitInfo, "commits_" + project_git._1().getName(), true);
            jarsBefore = new HashSet<>(commitInfo.getDependenciesList());
            GitHistoryRefactoringMinerImpl gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl(gr);
            final ChangeTypeMiner ctm = new ChangeTypeMiner(project_git._1());
            gitHistoryRefactoringMiner
                    .mineTypeChange(git, commitInfo, ctm, pathToDependencies, project_git._1().getUrl());
        }
        catch (Exception e){
            LOGGER.severe("Could not analyze commit: " + sha);
            LOGGER.severe(e.toString());
            LOGGER.severe(e.getStackTrace().toString());
            CommitInfo c = getCommitInfo(project_git._2(), sha, jarsBefore, new ArrayList<>(),e.toString());
            readWriteInputProtos.write(c, "commits_" + project_git._1().getName(), true);
        }
        catch (Throwable t) {
            LOGGER.severe("Could not analyze commit: " + sha);
            LOGGER.severe(t.toString());
            LOGGER.severe(t.getStackTrace().toString());
            CommitInfo c = getCommitInfo(project_git._2(), sha, jarsBefore, new ArrayList<>(),t.toString());
            readWriteInputProtos.write(c, "commits_" + project_git._1().getName(), true);
        }
        LOGGER.info("Finished analysing commit: " + sha);
        LOGGER.info("------------------------------------------------------------------");
    }


    public static Project getProject(Tuple2<String, String> pr, long n){
        return Project.newBuilder().setName(pr._1()).setUrl(pr._2()).setTotalCommits(n).build();
    }

    public static List<RevCommit> filterAlreadyProcessed(List<RevCommit> commits, String projectName){
        List<String> processedCommits = TypeFactMiner.readWriteInputProtos.<CommitInfo>readAll("commits_" + projectName, "CommitInfo").stream().map(x->x.getSha()).collect(toList());
        return commits.stream().filter(x -> !processedCommits.contains(x.getId().getName())).collect(toList());
    }


    private static CommitInfo getCommitInfo(Git git, String commit, Set<JarInfo> depsBefore, List<CommitInfo.Refactoring> refactorings, String exception){
        final Map<DiffEntry.ChangeType, List<Tuple2<String, String>>> diffs = filePathDiffAtCommit(git, commit);
        CommitInfo.Builder cmt = CommitInfo.newBuilder().setSha(commit);

        FileDiffOuterClass.FileDiff fileDiff = FileDiffOuterClass.FileDiff.newBuilder().setFilesAdded(Optional.ofNullable(diffs.get(DiffEntry.ChangeType.ADD)).map(List::size).orElse(0))
                .setFilesRemoved(Optional.ofNullable(diffs.get(DiffEntry.ChangeType.DELETE)).map(List::size).orElse(0))
                .setFilesRenamed(Optional.ofNullable(diffs.get(DiffEntry.ChangeType.RENAME)).map(List::size).orElse(0))
                .setFilesModified(Optional.ofNullable(diffs.get(DiffEntry.ChangeType.MODIFY)).map(List::size).orElse(0)).build();
        cmt.setFileDiff(fileDiff);

        if(isFileAffected(git,commit,fileName -> fileName.endsWith("pom.xml"))){
            LOGGER.info("POM Affected");
            Set<JarInfo> depsCurr = getDependenciesFromEffectivePom(commit, git.getRepository());
            Optional<DependencyUpdateOuterClass.DependencyUpdate> upd = getDepUpd(depsBefore, depsCurr);
            if(!upd.isPresent()) {
                cmt.addAllDependencies(depsBefore);
            }else{
                cmt.setDependencyUpdate(upd.get());
                cmt.addAllDependencies(depsCurr);
                downloadDependencies(pathToDependencies, depsCurr.stream().map(x->String.join(":", x.getArtifactID(),x.getGroupID(), x.getVersion())).collect(toSet()));
            }
        }else{
            cmt.addAllDependencies(depsBefore);
        }

        cmt.addAllRefactorings(refactorings);

        if(!exception.isEmpty()) {
            LOGGER.warning(exception);
            cmt.setException(exception);
        }

        cmt.setIsTypeChangeReported(refactorings.stream().anyMatch(r -> r.getName().contains("Type")));

        return cmt.build();

    }


}
