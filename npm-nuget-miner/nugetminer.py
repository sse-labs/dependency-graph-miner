from nugetanalysis.nugetnodecrawler import NugetNodeCrawler
from referenceresolving.artifactreferenceresolver import ArtifactDependencyResolver
from postprocessing.versionrelations import NextVersionRelationCreator
from postprocessing.latestdependency import LatestDependencyDetector


# Accumulate all nodes
crawler = NugetNodeCrawler()
crawler.do_crawling()

# Expand dependency information
resolver = ArtifactDependencyResolver()
resolver.process_artifact_dependencies("NugetPackage")

# Create NEXT and NEXT_RELEASE relations
creator = NextVersionRelationCreator("nuget")
creator.process_all_libs()


# Create CURRENT_TARGET relations
detector = LatestDependencyDetector("nuget")
detector.process_all_references()

