package org.osu;

import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.diff.ChangeAttributeTypeRefactoring;
import gr.uom.java.xmi.diff.ChangeReturnTypeRefactoring;
import gr.uom.java.xmi.diff.ChangeVariableTypeRefactoring;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.maven.shared.invoker.*;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;

import com.main.parse.DownloadAllJarClient;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.Builder;
import com.t2r.common.models.refactorings.DependencyUpdateOuterClass.DependencyPair;
import com.t2r.common.models.refactorings.DependencyUpdateOuterClass.DependencyUpdate;
import com.t2r.common.models.refactorings.FileDiffOuterClass.FileDiff;
import com.t2r.common.models.refactorings.JarInfoOuterClass.JarInfo;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.RefactoringUrl;
import com.t2r.common.models.refactorings.ProjectOuterClass.Project;
import com.t2r.common.utilities.FileUtils;
import com.t2r.common.utilities.GitUtil;

import java.io.File;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.t2r.common.utilities.FileUtils.deleteDirectory;
import static com.t2r.common.utilities.FileUtils.readFile;
import static java.util.stream.Collectors.*;
import static org.osu.TypeFactMiner.mavenHome;
import static org.osu.TypeFactMiner.pathToInput;

public class ChangeMiner extends RefactoringHandler {

    private static final Logger LOGGER = Logger.getLogger("ChangeMiner");

    private Map<String, List<Refactoring>> refactoringsReportedWithLocation = new HashMap<>();
    private List<CommitInfo.Refactoring> refactoringsReported = new ArrayList<>();

    private String exception = "";
    private String cloneLink;

    public ChangeMiner(String cloneLink){
        this.cloneLink = cloneLink;
    }


    public Map<String,RefactoringUrl> getRefactoringUrl(List<Refactoring> rs, String c){
        return rs.stream().collect(toMap(r->r.toString(), r -> {
            String url = cloneLink.replace(".git", "/commit/" + c + "?diff=split#diff-");
            var locUrl = getLocationFor(r, Tuple.of(url, url));
            if (locUrl.apply((l,rg) -> !l.isEmpty() && !rg.isEmpty())){
                return getLocationFor(r, Tuple.of(url, url))
                        .apply((lhs, rhs) -> (RefactoringUrl.newBuilder().setLhs(lhs).setRhs(rhs).build()));
            }
            return RefactoringUrl.getDefaultInstance();
        }, (a,b) -> a));
    }


    private static Tuple2<String, String>  getLocationFor(Refactoring r, Tuple2<String, String> commitUrl){
        if(r instanceof ChangeAttributeTypeRefactoring)
            return commitUrl.map(b4 -> generateUrl(((ChangeAttributeTypeRefactoring) r).getOriginalAttribute().getLocationInfo(), b4, "L")
                    , after -> generateUrl(((ChangeAttributeTypeRefactoring) r).getChangedTypeAttribute().getLocationInfo(), after, "R"));
        if(r instanceof ChangeReturnTypeRefactoring)
            return commitUrl.map(b4 -> generateUrl(((ChangeReturnTypeRefactoring) r).getOperationBefore().getLocationInfo(), b4, "L")
                    , after -> generateUrl(((ChangeReturnTypeRefactoring) r).getOperationAfter().getLocationInfo(), after, "R"));
        if(r instanceof ChangeVariableTypeRefactoring)
            return commitUrl.map(b4 -> generateUrl(((ChangeVariableTypeRefactoring) r).getOriginalVariable().getLocationInfo(), b4, "L")
                    , after -> generateUrl(((ChangeVariableTypeRefactoring) r).getChangedTypeVariable().getLocationInfo(), after, "R"));
        return Tuple.of("", "");
    }

    private static String generateUrl(LocationInfo locationInfo, String url, String lOrR)  {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(locationInfo.getFilePath().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            String val = sb.toString();
            return url + val + lOrR + locationInfo.getStartLine();
        }catch (Exception e){
            e.printStackTrace();
        }
        return "";

    }



    public List<CommitInfo.Refactoring> getRefactoring(String c){
        return refactoringsReportedWithLocation.entrySet()
                .stream().map(x-> CommitInfo.Refactoring.newBuilder().setName(x.getKey()).setOccurences(x.getValue().size())
                        .putAllDescriptionAndurl(getRefactoringUrl(x.getValue(),c )).build()).collect(toList());
    }
    @Override
    public void handle(String commit, List<Refactoring> refactorings) {
        refactoringsReportedWithLocation = refactorings.stream().collect(groupingBy(x->x.getName()));
        refactoringsReported = getRefactoring(commit);
        LOGGER.info(refactoringsReported.stream().map(x -> x.getName() + "\t" + x.getOccurences()).collect(Collectors.joining("\n")));

    }

    @Override
    public void handleException(String commitId, Exception e) {
        LOGGER.warning(e.toString());
        exception = e.toString();
    }

    public List<CommitInfo.Refactoring> getRefactoringsReported() {
        return refactoringsReported;
    }

    public String getException() {
        return exception;
    }

    public static Set<JarInfo> getDependenciesFromEffectivePom(String commit, Repository repo){

        Set<String> deps = generateEffectivePom(commit, repo)
                .map(x -> DownloadAllJarClient.getDependencies(x)).orElse(new HashSet<>());
        LOGGER.info("Generated Effective pom");

        return deps.stream().map(x->x.split(":"))
                .filter(x->x.length == 3)
                .map(dep->JarInfo.newBuilder()
                        .setArtifactID(dep[0]).setGroupID(dep[1])
                        .setVersion(dep[2]).build())
                .collect(toSet());
    }

    private static Optional<String> generateEffectivePom(String commitID, Repository repo) {

        Map<Path, String> poms = GitUtil.populateFileContents(repo, commitID, x -> x.endsWith("pom.xml"));
        Path p = pathToInput.resolve("tmp").resolve(commitID);

        FileUtils.materializeAtBase(p, poms);

        Path effectivePomPath = p.resolve("effectivePom.xml");

        if(!effectivePomPath.toFile().exists()) {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(p.resolve("pom.xml").toAbsolutePath().toString()));
            request.setGoals(Arrays.asList("help:effective-pom", "-Doutput=" + effectivePomPath.toAbsolutePath().toString()));
            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(new File(mavenHome));
            try {
                InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    System.out.println("Build Failed");
                    System.out.println("Could not generate effective pom");
                    return Optional.empty();
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        String effectivePomPathContent = readFile(effectivePomPath);
        deleteDirectory(p);
        return Optional.of(effectivePomPathContent);
    }




    public static Optional<DependencyUpdate> getDepUpd(Set<JarInfo> before, Set<JarInfo> after){
        Set<JarInfo> beforeMinusAfter = before.stream().filter(x -> after.stream().noneMatch(z->isSameDependency(z,x))).collect(toSet());
        Set<JarInfo> afterMinusBefore = after.stream().filter(x -> before.stream().noneMatch(z->isSameDependency(z,x))).collect(toSet());
        Set<DependencyPair> updates = versionUpdates(before, after);
        if(beforeMinusAfter.isEmpty() && afterMinusBefore.isEmpty())
            return Optional.empty();
        return Optional.of(DependencyUpdate.newBuilder()
                .addAllUpdate(updates)
                .addAllRemoved(beforeMinusAfter.stream()
                        .filter(x->updates.stream().map(DependencyPair::getBefore).noneMatch(d -> isSameDependency(x,d))).collect(toSet()))
                .addAllAdded(afterMinusBefore.stream()
                        .filter(x->updates.stream().map(DependencyPair::getAfter).noneMatch(d -> isSameDependency(x,d))).collect(toSet()))
                .build());
    }

    public static Set<DependencyPair> versionUpdates(Set<JarInfo> before, Set<JarInfo> after){
        return before.stream()
                .flatMap(x-> after.stream()
                        .filter(a -> a.getArtifactID().equals(x.getArtifactID()) && a.getGroupID().equals(x.getGroupID()) && !a.getVersion().equals(x.getVersion())).findFirst()
                        .map(d -> DependencyPair.newBuilder().setBefore(x).setAfter(d).build()).stream())
                .collect(toSet());
    }

    public static boolean isSameDependency(JarInfo b4, JarInfo after){
        return b4.getArtifactID().equals(after.getArtifactID()) && b4.getGroupID().equals(after.getGroupID())
                && b4.getVersion().equals(after.getVersion());
    }
}