from dependencygraphutils.neo4jsupport import Neo4jAdapter

class IndexHelper(object):

    @staticmethod
    def create_indices(graph_db_adapter: Neo4jAdapter):
        index_queries = []
        index_queries.append("CREATE INDEX FOR (npm: NpmPackage) ON (npm.package_id)")
        index_queries.append("CREATE INDEX FOR (nuget: NugetPackage) ON (nuget.package_id)")
        index_queries.append("CREATE INDEX FOR (ref:ArtifactReference) ON (ref.library_id)")
        index_queries.append("CALL db.index.fulltext.createNodeIndex('package_ids', ['NpmPackage', 'NugetPackage'], ['package_id'])")
        index_queries.append("CREATE CONSTRAINT ON (npm: NpmPackage) ASSERT npm.coordinates IS UNIQUE")
        index_queries.append("CREATE CONSTRAINT ON (nuget: NugetPackage) ASSERT nuget.coordinates IS UNIQUE")
        index_queries.append("CREATE CONSTRAINT ON (ref: ArtifactReference) ASSERT ref.coordinates IS UNIQUE")

        graph_db_adapter.execute_cypher_command_batch(index_queries)
        