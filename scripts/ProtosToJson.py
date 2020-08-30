from RW import readAll
import os
import json
from PrettyPrint import pretty, prettyJarInfo, prettyNameSpace1, prettyElementKind
import sys
#pathToRawData = r"C:\Users\t-amketk\RawData\RawData"


def get_all_projects(path):
    return readAll("Projects", "Project", pathToProtos=os.path.join(path, "ProtosOut"))


def get_details_of_commits_with_type_change(project, pathToTypeChangeCommit):
    return readAll("TypeChangeCommit_" + project.name, "TypeChangeCommit", pathToProtos=pathToTypeChangeCommit)


def get_dependency_affected(commitInfo):
    deps = {}
    dependencyUpdate = commitInfo.dependencyUpdate
    if len(dependencyUpdate.update) > 0:
        deps['Updated'] = list(map(lambda u: "->".join([prettyJarInfo(u.before), prettyJarInfo(u.after)]),
                                   dependencyUpdate.update))
    if len(dependencyUpdate.added) > 0:
        deps['Added'] = list(map(lambda u: prettyJarInfo(u), dependencyUpdate.added))
    if len(dependencyUpdate.removed) > 0:
        deps['Removed'] = list(map(lambda u: prettyJarInfo(u), dependencyUpdate.removed))

    return deps


def convert(pathToSetup):
    pathToJson = pathToSetup
    commits = {}
    typeChangeDict = {}

    for p in get_all_projects(os.path.join(pathToSetup, 'Input'))[:2]:

        commit_details = get_details_of_commits_with_type_change(p, os.path.join(pathToSetup, 'Output'))

        print()

        for cmt in commit_details:
            commit_Info = {'sha': cmt.sha, 'project': p.name,
                           'GitHub_URL': p.url,
                           'Dependencies': get_dependency_affected(cmt),
                           'Refactoring': cmt.refactorings._values
                           }
            commits[cmt.sha]= commit_Info

            for typeChange in cmt.typeChanges:
                    instances = []
                    for instance in typeChange.typeChangeInstances:
                        mappings = []
                        for mapping in instance.codeMapping:
                            replacements = []
                            for repl in mapping.replcementInferred:
                                repl_info = {"Before": repl.b4, "After": repl.aftr, "Replacement label": repl.replacementType}
                                replacements.append(repl_info)

                            mapping_info = {'IsSame': mapping.isSame, 'Prev Code Snippet': mapping.b4
                                , 'After Code Snippet': mapping.after
                                , 'Prev Code snippet url': mapping.urlbB4
                                , 'After Code snipper url': mapping.urlAftr
                                , 'Replacements': replacements}

                            mappings.append(mapping_info)

                        instance_info = {'From Type': pretty(instance.b4), 'To Type': pretty(instance.aftr)
                            , 'Element name before': instance.nameB4
                            , 'Element name after': instance.nameAfter
                            , 'Element kind affected': prettyElementKind(instance.elementKindAffected)
                            , 'Visibility of the element': instance.visibility
                            , 'Syntactic Transformation of type ast': instance.syntacticUpdate.transformation
                            , 'Github URL of element before': instance.urlB4
                            , 'Github URL of element after': instance.urlAfter
                            , 'Adaptations': mappings}
                        instances.append(instance_info)

                    typeChange_info = {'sha': cmt.sha, 'project': p.name
                        , 'From Type': pretty(typeChange.b4), 'To Type': pretty(typeChange.aftr)
                        , 'Number of Instances': len(typeChange.typeChangeInstances)
                        , 'Namespace of From Type': prettyNameSpace1(typeChange.nameSpacesB4)
                        , 'Namespace of To Type': prettyNameSpace1(typeChange.nameSpaceAfter)
                        , 'Hierarchy Relation': typeChange.hierarchyRelation
                        , 'Does from type composes To type': typeChange.b4ComposesAfter
                        , 'Primitive widening': typeChange.primitiveInfo.widening
                        , 'Primitive narrowing': typeChange.primitiveInfo.narrowing
                        , 'Primitive unboxing': typeChange.primitiveInfo.unboxing
                        , 'Primitive boxing': typeChange.primitiveInfo.boxing
                        , 'Instances': instances}

                    typeChangeDict.setdefault('->'.join([typeChange_info['From Type'], typeChange_info['To Type']]), [])\
                        .append(typeChange_info)

    with open(os.path.join(pathToJson, "commitInfo.json"), "w+") as outfile:
        json.dump(commits, outfile)

    with open(os.path.join(pathToJson, "typeChange.json"), "w+") as outfile:
        json.dump(typeChangeDict, outfile)


convert(sys.argv[1])