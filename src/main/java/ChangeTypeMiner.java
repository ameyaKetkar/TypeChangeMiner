import com.t2r.common.models.ast.TypeGraphOuterClass.TypeGraph;
import com.t2r.common.models.ast.TypeNodeOuterClass.TypeNode;
import com.t2r.common.models.refactorings.CodeStatisticsOuterClass.CodeStatistics;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo;
import com.t2r.common.models.refactorings.CommitInfoOuterClass.CommitInfo.JarInfo;
import com.t2r.common.models.refactorings.NameSpaceOuterClass.NameSpace;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis;
import com.t2r.common.models.refactorings.TypeChangeAnalysisOuterClass.TypeChangeAnalysis.TypeChangeInstance;
import com.t2r.common.models.refactorings.TypeChangeCommitOuterClass.TypeChangeCommit;
import com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem;
import com.t2r.common.utilities.PrettyPrinter;
import gr.uom.java.xmi.TypeFactMiner.ExtractHierarchyPrimitiveCompositionInfo;
import gr.uom.java.xmi.TypeFactMiner.GlobalContext;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.TypeRelatedRefactoring;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import static com.t2r.common.models.refactorings.NameSpaceOuterClass.NameSpace.*;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.Enum;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.Object;
import static com.t2r.common.models.refactorings.TypeSemOuterClass.TypeSem.*;
import static gr.uom.java.xmi.TypeFactMiner.TypFct.getDependencyInfo;
import static java.util.stream.Collectors.*;

public class ChangeTypeMiner extends RefactoringHandler {

    Set<JarInfo> jarsRequired =  new HashSet<>();

    @Override
    public void handle(CommitInfo c, List<Refactoring> refactorings, Tuple2<GlobalContext, GlobalContext> globalContexts, CodeStatistics cs) {
        if (refactorings.isEmpty() || refactorings.stream().noneMatch(Refactoring::isTypeRelatedChange)) {
            System.out.println("No CTT");
            return;
        }


        List<TypeRelatedRefactoring> typeRelatedRefactorings = refactorings.stream()
                .filter(x -> x instanceof TypeRelatedRefactoring)
                .map(x -> (TypeRelatedRefactoring) x)
                .collect(toList());

        Map<Tuple2<TypeGraph, TypeGraph>, List<TypeChangeInstance>> groupedTypeChanges = typeRelatedRefactorings
                .stream()
                .flatMap(x -> {
                    if(x.getRealTypeChanges() == null)
                        return Stream.empty();
                    return x.getRealTypeChanges().stream().map(t -> Tuple.of(t, x.getTypeChangeInstance()));
                })
                .collect(groupingBy(x -> x._1()
                        , collectingAndThen(toList(), x -> x.stream().map(t -> t._2()).collect(toList()))));

        List<TypeChangeAnalysis> typeChangeAnalysisList = groupedTypeChanges.entrySet().stream()
                .map(x -> performAndGetTypeChangeAnalysis(x, globalContexts))
                .collect(toList());


        TypeChangeCommit typeChangeCommit = TypeChangeCommit.newBuilder()
                .setCommit(c)
                .addAllTypeChanges(typeChangeAnalysisList)
                .setCodeStatistics(cs).build();


        System.out.println(PrettyPrinter.prettyCommit(c,typeChangeAnalysisList,cs));

        typeRelatedRefactorings.stream().filter(x->!x.isResolved()).forEach(x -> {
            System.out.println("UNRESOLVED");
            System.out.println(c + ((Refactoring) x).getName() + "  "
                    + x.getTypeB4().getTypeStr() + "  ---->   " + x.getTypeAfter().getTypeStr() + "\n");
        });

        Runner.readWriteProtos.write(typeChangeCommit,"TypeChangeCommit", true);

        System.out.println("-------------------------------");
        System.out.println();
    }


    private TypeChangeAnalysis performAndGetTypeChangeAnalysis(Entry<Tuple2<TypeGraph, TypeGraph>, List<TypeChangeInstance>> entry, Tuple2<GlobalContext, GlobalContext> gc) {
        var v = entry.getKey().map(t -> getNameSpaceAndTypeSem(t, gc._1()),t -> getNameSpaceAndTypeSem(t, gc._2()));

        Set<JarInfo> allJars = Stream.concat(gc._1().getRequiredJars().stream(), gc._2().getRequiredJars().stream())
                .collect(toSet());

        var e = new ExtractHierarchyPrimitiveCompositionInfo(gc._2(), allJars, Runner.pathToDependencies);
        TypeChangeAnalysis hierarchyPrimitiveCompositionInfo = e.extract(entry.getKey()._1(), entry.getKey()._2());
        return TypeChangeAnalysis.newBuilder().setB4(entry.getKey()._1()).setAftr(entry.getKey()._2())
                .addAllTypeChangeInstances(entry.getValue())
                .setNameSpacesB4(v._1()._1()).setNameSpaceAfter(v._2()._1())
                .setTypeSemb4(v._1()._2()).setTypeSemAftr(v._2()._2())
                .mergeFrom(hierarchyPrimitiveCompositionInfo).build();
    }



    public Tuple2<NameSpace, TypeSem> getNameSpaceAndTypeSem(TypeGraph resolvedTypeGraph, GlobalContext gc){
        switch (resolvedTypeGraph.getRoot().getKind()){
            case Primitive:
            case Simple:
                return findNameSpaceTypeSemFor(resolvedTypeGraph.getRoot(), gc);

            case WildCard:{
                return (resolvedTypeGraph.getEdgesMap().get("extends")!=null)
                    ? getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("extends"), gc)
                    : ((resolvedTypeGraph.getEdgesMap().get("super")!=null)
                        ? getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("super"), gc)
                    : Tuple.of(TypeVariable, TypeSem.Object));
            }
            case Array: getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("of"), gc);
            case Parameterized:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("of"), gc);
            case Intersection:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("Intersection:"), gc);
            case Union:
                return getNameSpaceAndTypeSem(resolvedTypeGraph.getEdgesMap().get("Union:"), gc);
            default: return Tuple.of(DontKnow, Dont_Know);
        }

    }


    private Tuple2<NameSpace, TypeSem> findNameSpaceTypeSemFor(TypeNode tn, GlobalContext gc) {
        if(tn.getIsTypeVariable())
            return Tuple.of(TypeVariable,Object);
        if(tn.getKind().equals(TypeNode.TypeKind.Primitive))
            return Tuple.of(Jdk, PrimitiveType);
        if (foundAsInternalClass(tn.getName(), gc))
            return Tuple.of(Internal,Object);
        else if (foundAsInternalEnum(tn.getName(), gc))
            return Tuple.of(Internal,Enum);
        else if (foundAsJdkClass(tn.getName(), gc))
            return Tuple.of(Jdk,Object);
        else if (foundAsJdkEnum(tn.getName(), gc))
            return Tuple.of(Jdk,Enum);
        else if (foundAsExternalEnum(tn.getName(), gc))
            return Tuple.of(External,Enum);
        else if (foundAsExternalClass(tn.getName(), gc))
            return Tuple.of(External,Object);
        return Tuple.of(DontKnow, Dont_Know);
    }

    private boolean foundAsInternalClass(String name, GlobalContext gc) {
        return gc.getClassesInternal().stream().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsInternalEnum(String name, GlobalContext gc) {
        return gc.getEnumsInternal().stream().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsJdkClass(String name, GlobalContext gc) {
        return gc.getAllJavaClasses().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsJdkEnum(String name, GlobalContext gc) {
        return gc.getJavaEnums().anyMatch(c -> c.contains(name));
    }

    private boolean foundAsExternalEnum(String name, GlobalContext gc) {
        var t = gc.getRequiredJars().stream()
                .map(x -> getDependencyInfo(gc.getPathToJars(), x))
                .flatMap(d -> d.toJavaStream())
                .flatMap(d -> d._2().getEnums().getNamesList().stream().map(x -> Tuple.of(x, d._1())))
                .filter(c -> c._1().contains(name))
                .findFirst();
        t.ifPresent(stringJarInfoTuple2 -> jarsRequired.add(stringJarInfoTuple2._2()));
        return t.isPresent();
    }

    private boolean foundAsExternalClass(String name, GlobalContext gc) {
        var t =  gc.getRequiredJars().stream()
                .map(x -> getDependencyInfo(gc.getPathToJars(), x))
                .flatMap(d -> d.toJavaStream())
                .flatMap(d -> d._2().getHierarchicalInfoMap().keySet().stream().map(x -> Tuple.of(x,d._1())))
                .filter(c -> c._1().contains(name)).findFirst();

        t.ifPresent(stringJarInfoTuple2 -> jarsRequired.add(stringJarInfoTuple2._2()));
        return t.isPresent();
    }

}
