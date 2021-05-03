from postprocessing.versionrelations import NextVersionRelationCreator

import os

ecosystem = os.getenv('ECOSYSTEM', 'nuget')

c = NextVersionRelationCreator(ecosystem)

c.process_all_libs()