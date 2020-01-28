import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.utilities.ProtoUtil.ReadWriteAt;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static com.t2r.common.utilities.FileUtils.parseCsv;
import static com.t2r.common.utilities.GitUtil.findCommit;
import static com.t2r.common.utilities.GitUtil.tryToClone;

public class Runner {
    private static final Logger LOGGER = Logger.getLogger("Runner");
    static FileHandler fh;
    public static Properties prop;
    public static Path pathToCorpus;
    public static Path projectList;
    public static Function<String,Path> projectPath;
    public static Date epochStart;
    public static Path outputFolder;
    public static ReadWriteAt readWriteSimpleTypeChangeProtos;
    public static String mavenHome;
    public static Path pathToSimpleTypeChangeMinerOutput;
    public static Path pathToDependencies;
    public static ReadWriteAt readWriteProtos;

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
            pathToSimpleTypeChangeMinerOutput = Paths.get(prop.getProperty("PathToSimpleTypeChangeMinerOutput"));
            readWriteSimpleTypeChangeProtos = new ReadWriteAt(pathToSimpleTypeChangeMinerOutput.resolve("ProtosOut"));
            readWriteProtos = new ReadWriteAt(outputFolder);
            pathToDependencies = pathToSimpleTypeChangeMinerOutput.resolve("dependencies");
            mavenHome = prop.getProperty("mavenHome");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)  {
        List<Tuple2<String, String>> projects = parseCsv(projectList, x -> Tuple.of(x[0], x[1]));
        projects.stream()
            .map(prc -> Tuple.of(prc, readWriteSimpleTypeChangeProtos.<CommitInfo>readAll("commits_" + prc._1(), "CommitInfo")))
            .map(x -> Tuple.of(x,tryToClone(x._1()._2(), projectPath.apply(x._1()._1()).toAbsolutePath())))
                .filter( p -> p._1()._1()._1().contains("guacamole"))
            .forEach(p -> {
                if (p._2().isSuccess()){
                    System.out.println("Analysing " + p._1()._1());
                    p._1()._2().stream().filter(x->x.getRefactoringsCount() > 0 && x.getIsTypeChangeReported() )
                            .map(x -> Tuple.of(x,findCommit(x.getSha(), p._2.get().getRepository())))
                            .filter(x->x._2().isPresent())
//                            .filter(x -> x._2().map(r -> r.getId().getName().equals("6307ba09212c80dddb68d8b516bab80af4ef6bb0")).orElse(false))
                            .forEach(c -> {
                                GitHistoryRefactoringMinerImpl gitHistoryRefactoringMiner = new GitHistoryRefactoringMinerImpl();
                                final ChangeTypeMiner ctm = new ChangeTypeMiner();
                                gitHistoryRefactoringMiner
                                        .mineTypeChange(p._2.get().getRepository(), c._1(), ctm, pathToDependencies);
                            });
                }
            });


    }
}
