package org.osu;

import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings;
import com.t2r.common.models.refactorings.ProcessedCodeMappingsOuterClass.ProcessedCodeMappings.RelevantStmtMapping;
import org.osu.TypeFactMiner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class AnalyseProcessedCodeMappings {

    public static void main (String a[]){

        List<ProcessedCodeMappings> processedCodeMappings = TypeFactMiner.readWriteCodeMappingProtos.readAll("ProcessedCodeMapping", "CodeMapping");
        long count = processedCodeMappings.size();
//        List<Tuple2<Tuple2<TypeGraphOuterClass.TypeGraph, TypeGraphOuterClass.TypeGraph>, Map<String, Long>>> tciMappings =
        List<RelevantStmtMapping> tciMappings = processedCodeMappings.stream()
                .flatMap(pc -> pc.getRelevantStmtsList().stream())
                .collect(toList());
        System.out.println(tciMappings.size());

        tciMappings = tciMappings.stream().filter(x -> x.getMappingCount() > 0).collect(toList());
        System.out.println(tciMappings.size());

        List<Set<String>> tciReplacements = tciMappings.stream()
                .map(l -> Stream.concat(l.getMappingList().stream().map(x -> x.getReplacement()),
                        (l.getB4().equals(l.getAfter()) ? Stream.empty() : Stream.of("\\percentVarRename"))).collect(toSet()))
                .collect(toList());

      long c = tciReplacements.stream().filter(x->x.isEmpty()).count();

        Map<String, Long> zz = tciReplacements.stream().flatMap(x -> x.stream()).collect(groupingBy(x -> x, counting()));

        System.out.println(zz);

         zz.entrySet().forEach(x -> System.out.println("\\newcommand{" + x.getKey() + "TCI}{" + (double) x.getValue() / count + "\\%\\xspace"));
//
//        System.out.println(editPatGroupedByPattern);
    }


}
