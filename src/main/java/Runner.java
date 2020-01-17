import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.utilities.ProtoUtil.ReadWriteAt;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import io.vavr.Tuple3;
import io.vavr.control.Try;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.api.TypeRelatedRefactoring;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.t2r.common.utilities.FileUtils.createFolderIfAbsent;
import static com.t2r.common.utilities.FileUtils.parseCsv;
import static com.t2r.common.utilities.GitUtil.findCommit;
import static com.t2r.common.utilities.GitUtil.tryToClone;
import static com.t2r.common.utilities.TypeGraphUtil.pretty;
import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class Runner {
    private static final Logger LOGGER = Logger.getLogger("Runner");
    static FileHandler fh;
    public static Properties prop;
    public static Path pathToCorpus;
    public static Path projectList;
    public static Function<String,Path> projectPath;
    public static Date epochStart;
    public static Path outputFolder;
    public static ReadWriteAt readWriteProtos;
    public static String mavenHome;

    static{
        try {
            fh = new FileHandler("./Runner.log");
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            prop = new Properties();
            InputStream input = new FileInputStream("paths.properties");
            prop.load(input);
            pathToCorpus = Paths.get(prop.getProperty("PathToCorpus"));
            projectList = pathToCorpus.resolve(prop.getProperty("InputProjects"));
            projectPath = p -> pathToCorpus.resolve("Project_"+p).resolve(p);
            epochStart = new SimpleDateFormat("YYYY-mm-dd").parse(prop.getProperty("epoch"));
            outputFolder = Paths.get(".").toAbsolutePath().resolve(prop.getProperty("output"));
            readWriteProtos = new ReadWriteAt(Paths.get("/Users/ameya/Research/TypeChangeStudy/SimpleTypeChangeMiner/Output")
                    .resolve("ProtosOut"));
            mavenHome = prop.getProperty("mavenHome");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static void main(String a[])  {

        List<Tuple2<String, String>> projects = parseCsv(projectList, x -> Tuple.of(x[0], x[1]));
        GitHistoryRefactoringMinerImpl gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl();
        projects.stream()
            .map(prc -> Tuple.of(prc, readWriteProtos.<CommitInfo>readAll("commits_" + prc._1(), "CommitInfo")))
            .map(x -> Tuple.of(x,tryToClone(x._1()._2(), projectPath.apply(x._1()._1()).toAbsolutePath())))
            .forEach(p -> {
                if (p._2().isSuccess()){
                    p._1()._2().stream().filter(x->x.getRefactoringsCount() > 0 && x.getIsTypeChangeReported() )
                            .map(x -> findCommit(x.getSha(), p._2.get().getRepository()))
                            .filter(x->x.isPresent())
                            .forEach(c -> {
                                final ChangeTypeMiner ctm = new ChangeTypeMiner(p._2.get().getRepository(),c.get() );
                                gitHistoryRefactoringMiner
                                        .detectAtCommit(p._2.get().getRepository(), c.get().getId().getName(), ctm, 50);
                            });
                }
            });


    }


    public static class ChangeTypeMiner extends RefactoringHandler {

        private Repository repo;
        private RevCommit rc;

        public ChangeTypeMiner(Repository repo, RevCommit rc) {
            this.repo = repo;
            this.rc = rc;
        }

        @Override
        public void handle(String c, List<Refactoring> refactorings) {
            System.out.println(refactorings.size());
            System.out.println("Refactorings detected");
            System.out.println(c);
            if (refactorings.isEmpty() || refactorings.stream().noneMatch(r -> r.getRefactoringType().equals(RefactoringType.CHANGE_ATTRIBUTE_TYPE)
                    || r.getRefactoringType().equals(RefactoringType.CHANGE_PARAMETER_TYPE) || r.getRefactoringType().equals(RefactoringType.CHANGE_VARIABLE_TYPE)
                    || r.getRefactoringType().equals(RefactoringType.CHANGE_RETURN_TYPE))) {
                System.out.println("No CTT");
                return;
            }

            List<TypeRelatedRefactoring> typeRelatedRefactorings = refactorings.stream().filter(x -> x instanceof TypeRelatedRefactoring)
                    .map(x -> (TypeRelatedRefactoring) x)
                    .collect(toList());
            try {
                System.out.println("Resolved");
                typeRelatedRefactorings.stream().filter(x->x.isResolved()).forEach(x -> {

                    Path resolved = Paths.get("D:/MyProjects/resolved.txt");
                    if(!resolved.toFile().exists()){
                        Try.of(()->Files.createFile(resolved));
                    }

                    String tc = "\n" + ((Refactoring) x).getName() + "  " + x.getTypeB4().getTypeStr() + "  ---->   " + x.getTypeAfter().getTypeStr();
                    Try.of(() -> Files.write(resolved,tc.getBytes(), StandardOpenOption.APPEND));
                    System.out.print(tc);
                    if(x.getRealTypeChanges()!=null && x.getRealTypeChanges().get(0)!=null) {
                        Tuple3<TypeGraphOuterClass.TypeGraph, TypeGraphOuterClass.TypeGraph, List<String>> t = x.getRealTypeChanges().get(0);

                        if (t != null) {
                            String s = "\n" +  pretty(t._1()) + " to " + pretty(t._2()) + " Info: " + t._3().stream().collect(joining(","));
                            System.out.print(s);
                            Try.of(() -> Files.write(resolved,s.getBytes(), StandardOpenOption.APPEND));
                        }
                        x.getRealTypeChanges().stream().skip(1).forEach(tcc -> {
                            String x1 = "\n" +  "       " + pretty(tcc._1()) + " to " + pretty(tcc._2()) + " Info: " + tcc._3().stream().collect(joining(","));
                            Try.of(() -> Files.write(resolved,x1.getBytes(), StandardOpenOption.APPEND));
                            System.out.print(x1);
                        });
                    }
                });

                System.out.println();
                System.out.println("Un resolved");
                typeRelatedRefactorings.stream().filter(x->!x.isResolved()).forEach(x -> {
                    Path unResolved = Paths.get("D:/MyProjects/unresolved.txt");
                    if(!unResolved.toFile().exists()){
                        Try.of(()->Files.createFile(unResolved));
                    }
                    String x1 = c + ((Refactoring) x).getName() + "  "
                            + x.getTypeB4().getTypeStr() + "  ---->   " + x.getTypeAfter().getTypeStr() + "\n";
                    Try.of(() -> Files.write(unResolved,x1.getBytes(), StandardOpenOption.APPEND));
                });

            }catch (Exception e){
                System.out.println("Some exception!!!");
            }
            System.out.println("-------------------------------");

        }

    }

    public static Map<String, String> readProjects(String path){
        try {
            return Files.readAllLines(Paths.get(path)).parallelStream()
                    .map(e -> Tuple.of(e.split(",")[0], e.split(",")[1]))
                    .peek(e -> System.out.println(e._1()))
                    .collect(toMap(Tuple2::_1, Tuple2::_2, (a, b)->a));
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not read projects");
            throw new RuntimeException("Could not read projects");
        }
    }

    public static Try<Git>tryCloningRepo(String projectName, String cloneLink, String path) {
        createFolderIfAbsent(Paths.get(path));
        return Try.of(() -> Git.open(new File(path )))
                .onFailure(e -> System.out.println("Did not find " + projectName + " at" + path))
                .orElse(Try.of(() ->
                        Git.cloneRepository().setURI(cloneLink).setDirectory(new File(path + "/" + projectName)).call()))
                .onFailure(e -> System.out.println("Could not clone " + projectName));

    }

    public static List<RevCommit> getCommits(Git git, RevSort order) {
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        String input = "2015-01-01" ;
        return Try.of(() -> {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.markStart(walk.parseCommit(git.getRepository().resolve("HEAD")));
            walk.sort(order);
            walk.setRevFilter(RevFilter.NO_MERGES);
            walk.setRevFilter(CommitTimeRevFilter.after(ft.parse(input)));
            return walk;
        })
                .map(walk -> {
                    Iterator<RevCommit> iter = walk.iterator();
                    List<RevCommit> l = new ArrayList<>();
                    while(iter.hasNext()){
                        l.add(iter.next()); }
                    walk.dispose();
                    return l;
                })
                .onSuccess(l -> System.out.println("Total number of commits found : " + l.size()))
                .onFailure(Throwable::printStackTrace)

                .getOrElse(new ArrayList<>());
    }

}
