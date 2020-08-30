
from Models.Models.TypeGraph_pb2 import TypeGraph



def pretty(t: TypeGraph) -> str:
    if t.root.kind == t.root.Primitive or t.root.kind == t.root.Simple:
        return str(t.root.name)
    if t.root.kind == t.root.Parameterized:
        params = []
        for k, v in t.edges.items():
            if "Param" in k:
                params.append(pretty(v))
        if len(params) > 0:
            return pretty(t.edges['of']) + "<" + ', '.join(params) + ">"
        return pretty(t.edges['of'])
    if t.root.kind == t.root.WildCard:
        bound = ''
        for k, v in t.edges.items():
            if "extends" in k:
                bound = ("extends" + pretty(v))
            if "super" in k:
                bound = ("super" + pretty(v))
            return "? " + bound
        if len(t.edges.items()) == 0:
            return "?"
    if t.root.kind == t.root.Array:
        return pretty(t.edges['of']) + "[]"
    if t.root.kind == t.root.Union or t.root.kind == t.root.Intersection:
        params = []
        for k, v in t.edges.items():
            params.append(pretty(v))
        return '|'.join(params)
    return ''


def prettyNameSpace(n: int) -> str:
    if n == 0:
        return "TypeVariable"
    if n == 2:
        return "Jdk"
    if n == 3:
        return "External"
    if n == 4:
        return "Internal"
    if n == 5:
        return "DontKnow"


def prettyNameSpace1(n: int) -> str:
    if n == 0:
        return "TypeVariable"
    if n == 2:
        return "Jdk"
    if n == 3:
        return "External"
    if n == 4:
        return "Internal"
    if n == 5:
        return "External"


def prettyElementKind(n: int) -> str:
    if n == 0:
        return "Field"
    if n == 2:
        return "LocalVariable"
    if n == 3:
        return "Parameter"
    if n == 4:
        return "Method Return Type"


def prettyTypeKind(n: int) -> str:
    if n == 0:
        return "Simple"
    if n == 2:
        return "Parameterized"
    if n == 3:
        return "WildCard"
    if n == 4:
        return "Union"
    if n == 5:
        return "Primitive"
    if n == 6:
        return "Intersection"
    if n == 7:
        return "Array"


def prettyJarInfo(jarInfo):
    return "-".join([jarInfo.artifactID, jarInfo.groupID, jarInfo.version])

cleanMappingNames = {
    "\\percentModifyReveiver": "Modified Receiver",
    "\\percentAddRemoveMthdInvc": "Add or Remove Method invocation",
    "\\percentMthdRename": "Rename Method invocation",
    "\\percentIntroduceLiteral": "Introduce Literal",
    "\\percentClsInCrToMthdInvc": "Convert Class instance creation to method invocation",
    "\\percentUpdateAnonymCls": "Update Anonymous class",
    "\\percentMthdInvcArgsUpdate": "Update argument list (Method invocation)",
    "\\percentCascadingTypeDifferent": "Cascading Type Change (Different)",
    "\\percentCast": "Cast",
    "\\percentWrap": "Wrap or Un-wrap",
    "\\percentClsInstCr": "Update Class Instacne Creation",
    "Update Num Literal": "Update Number Literal",
    "Update String Literal": "Update String Literal",
    "\\percentClsInsCrArgsUpdate": "Update argument list (Class Instance Creation)",
    "\\percentCascadingType": "Cascading Type Change (Similar)",
    "\\percentVarRename": "Rename Variable",
    "\\Other": "Other"
}


def getCleanMappingNames(s: str) -> str:
    if s in cleanMappingNames:
        return cleanMappingNames[s]
    return s
