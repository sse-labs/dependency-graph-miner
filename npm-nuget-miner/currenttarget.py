from postprocessing.latestdependency import LatestDependencyDetector
import os

ecosystem = os.getenv('ECOSYSTEM', 'nuget')
recursion_limit = int(os.getenv('MAX_DEPTH', '250'))
print_skip = int(os.getenv('PRINT_SKIP', '50'))

c = LatestDependencyDetector(ecosystem, recursion_limit, print_skip)

c.process_all_references()