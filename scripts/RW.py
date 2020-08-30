
from os.path import join as join
from os.path import dirname as parent
from os.path import realpath as realPath

from Models.Models.TheWorld_pb2 import TheWorld
from Models.Models.TypeChangeCommit_pb2 import TypeChangeCommit
from Models.Models.CommitInfo_pb2 import CommitInfo
from Models.Models.Project_pb2 import Project
from Models.Models.ProcessedCodeMappings_pb2 import ProcessedCodeMappings
from Models.Models.MigrationData_pb2 import MigrationData
from Models.Models.Verification_pb2 import Verification


def readFile(filename):
    try:
        filehandle = open(filename)
        s = filehandle.read()
        filehandle.close()
        return s
    except Exception as e: 
        print(str(e))
        return ''



def readAll(fileName, kind, pathToProtos):
    print("reading from " + pathToProtos)
    sizeFile = readFile(join(pathToProtos, fileName + 'BinSize.txt'))
    if(sizeFile == ''):
        print("ow!", fileName)
        return []

    print(sizeFile.split(" "))
    sizes = list(map(lambda s: int(s), filter(lambda s: s != '', sizeFile.split(" "))))
    buf = join(pathToProtos, fileName + '.txt')
    print(len(sizes))
    l = []
    with open(buf, 'rb') as f:
        buf = f.read()
        n = 0
        for s in sizes:
            msg_buf = buf[n:n+s]
            n += s
            c = None
            if kind == "Commit":
                c = CommitInfo()
            if kind == "Project":
                c = Project()
            if kind == "TypeChangeCommit":
                c = TypeChangeCommit()
            if kind == "TheWorld":
                c = TheWorld()
            if kind == "ProcessedCodeMapping":
                c = ProcessedCodeMappings()
            if kind == "Migration":
                c = MigrationData()
            if kind == "Verification":
                c = Verification()
            if c is not None:
                c.ParseFromString(msg_buf)
                l.append(c)
        return l





