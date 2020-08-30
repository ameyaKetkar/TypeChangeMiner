import os.path as osp
import boto3
from botocore import UNSIGNED
from botocore.client import Config
import os


def setupAt(pathToSetup):
    print('Setup started!!!')

    addDirectoryIfNotExists(pathToSetup)

    corpus = osp.join(pathToSetup, 'Corpus')



    if not osp.isdir(corpus):
        os.mkdir(corpus)

    defaultInputProject = 'guice,https://github.com/google/guice.git'

    with open(os.path.join(corpus,'InputProjects.csv'), 'w+') as f:
        f.write(defaultInputProject)


    output = osp.join(pathToSetup, "Output")
    inpt = osp.join(pathToSetup, "Input")
    addDirectoryIfNotExists(inpt)
    addDirectoryIfNotExists(output)
    addDirectoryIfNotExists(osp.join(inpt, "ProtosOut"))
    addDirectoryIfNotExists(osp.join(inpt, "tmp"))
    addDirectoryIfNotExists(osp.join(inpt, "dependencies"))

    code_mapping = osp.join(output, "CodeMapping")

    addDirectoryIfNotExists(code_mapping)

    os.chdir(pathToSetup)
    downloadGremlinServer(pathToSetup)
    print("Setup complete!")
    print()
    print('To run the tinkerpop server: \n \t ON WINDOWS: '
          '..\\apache-tinkerpop-gremlin-server-3.4.4\\bin\\gremlin-server.bat \n \t ON LINUX OR MAC: '
          './apache-tinkerpop-gremlin-server-3.4.4/bin/gremlin-server.sh console')
    print("Update the ..\\TypeChangeMiner\\path.properties")
    print("\t Set the value of PathToSetup to " + str(pathToSetup))
    print("\t Set the value of mavenHome appropriately. \n \t \t In windows the path looks like "
          "C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.6.3\\")


def addDirectoryIfNotExists(dir):
    if not osp.isdir(dir):
        os.mkdir(dir)
        print('Created the directory: ', str(dir))


# https://changetype.s3.us-east-2.amazonaws.com/docs/apache-tinkerpop-gremlin-server-3.4.4.zip

def downloadGremlinServer(typechangestudy):
    s3 = boto3.client('s3', config=Config(signature_version=UNSIGNED))
    if not osp.isfile(osp.join(typechangestudy, 'apache-tinkerpop-gremlin-server-3.4.4.zip')):
        print('Downloading ', 'apache-tinkerpop-gremlin-server-3.4.4.zip')
        s3.download_file('changetype', 'docs/apache-tinkerpop-gremlin-server-3.4.4.zip',
                         'apache-tinkerpop-gremlin-server-3.4.4.zip')
        print('Download complete!!!')
    if not osp.isdir(osp.join(typechangestudy, 'apache-tinkerpop-gremlin-server-3.4.4')):
        import zipfile
        print('Unzipping ', 'apache-tinkerpop-gremlin-server-3.4.4.zip')
        with zipfile.ZipFile(osp.join(typechangestudy, 'apache-tinkerpop-gremlin-server-3.4.4.zip'), 'r') as zip_ref:
            tinkerPopDir = os.mkdir(osp.join(typechangestudy, 'apache-tinkerpop-gremlin-server-3.4.4'))
            zip_ref.extractall(tinkerPopDir)
            print('Unzip complete')




setupAt('C:\\Users\\t-amketk\\TypeChangeStudy')
