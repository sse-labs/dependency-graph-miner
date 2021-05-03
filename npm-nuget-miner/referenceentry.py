from referenceresolving.artifactreferenceresolver import ArtifactDependencyResolver
import os

label = os.getenv("LABEL", "NugetPackage")
db_url = os.getenv("NEO4J_URL", "None")

print(f'Processing nodes of type {label}. Database set via NEO4J_URL: {db_url}')

resolver = ArtifactDependencyResolver()
resolver.process_artifact_dependencies(label)