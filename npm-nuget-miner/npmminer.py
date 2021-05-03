from npmanalysis.npmcrawling import NpmNodeProcessor
from npmanalysis.npmutils import NpmIndexDownloader
import os.path
from referenceresolving.artifactreferenceresolver import ArtifactDependencyResolver
from postprocessing.versionrelations import NextVersionRelationCreator
from postprocessing.latestdependency import LatestDependencyDetector

# Download NPM index file if required
if not os.path.isfile('./index.json'):
    download_success = NpmIndexDownloader.download_index_to_file('./index.json')

    if not download_success:
        raise Exception('Cannot process nodes, index could not be downloaded')
    else:
        print('Npm index successfully downloaded.')

# Accumulate all nodes
p = NpmNodeProcessor()
p.initialize("./index.json")
p.store_all_packages()

# Expand dependency information
resolver = ArtifactDependencyResolver()
resolver.process_artifact_dependencies("NpmPackage")

# Create NEXT relations
creator = NextVersionRelationCreator("npm")
creator.process_all_libs()
#TODO: NEXT_RELEASE

# Create CURRENT_TARGET relations
detector = LatestDependencyDetector("npm")
detector.process_all_references()
