from dependencygraphutils.neo4jsupport import Neo4jAdapter
from dependencygraphutils.model.versions import VersionNumber

class NextVersionRelationCreator(object):

    def __init__(self, ecosystem:str):
        self.ecosystem = ecosystem

        if ecosystem == 'nuget':
            self.node_label = 'NugetPackage'
        elif ecosystem == 'npm':
            self.node_label = 'NpmPackage'
        else:
            raise Exception(f'Unknown ecosystem label {ecosystem}')

        self.graph_db_adapter = Neo4jAdapter.create_default_instance()
    
    def process_all_libs(self):

        iterator_query = self.__build_iterator_query()

        global_session = self.graph_db_adapter.create_session()

        count = 0

        # Store all lib ids in index
        index = []

        print('Starting to index library identifier...')

        for lib_record in global_session.run(iterator_query):
            count +=1

            lib_id = lib_record['lib']

            index.append(lib_id)

            if count % 1000 == 0:
                print(f'Indexed {count} libraries so far...')
        
        global_session.close()

        total = len(index)
        count = 0

        print(f'Done indexing, found {total} libraries.')

        for lib in index:
            count += 1

            print(f'Processing library {lib} ({count}/{total})')

            self.process_lib(lib)
    
    def process_lib(self, lib_id):

        local_session = self.graph_db_adapter.create_session()

        artifact_iterator = local_session.run(self.__build_artifact_iterator_query(lib_id))

        version_coords_dict = {}
        version_list = []

        for artifact_record in artifact_iterator:
            coords = artifact_record['coords']
            version = artifact_record['version']
            version_coords_dict[version] = coords
            version_list.append(VersionNumber(version, use_filling=False))

        version_list.sort()

        last_coordinates = None
         
        for version in version_list:
            current_coordinates = version_coords_dict[version.original_string_representation]

            if last_coordinates != None:
                merge_query = self.__build_merge_query(last_coordinates, current_coordinates)
                local_session.run(merge_query)

            last_coordinates = current_coordinates

        local_session.close()


    def __build_iterator_query(self):

        query = (f"MATCH (a:{self.node_label}) WHERE NOT (a)-[:NEXT]->(:{self.node_label}) "
            f"AND NOT (a)<-[:NEXT]-(:{self.node_label}) WITH a.package_id AS lib ")

        query = query + "RETURN DISTINCT lib"

        return query

    def __get_number_of_libs(self):

        query = (f"MATCH (a:{self.node_label}) WHERE NOT (a)-[:NEXT]->(:{self.node_label}) "
            f"AND NOT (a)<-[:NEXT]-(:{self.node_label}) WITH a.package_id AS lib ")

        query = query + "RETURN COUNT(DISTINCT lib) AS count"

        return int(self.graph_db_adapter.execute_cypher_query(query).single()['count'])

    
    def __build_artifact_iterator_query(self, lib_id):

        query = f"MATCH (a:{self.node_label} {{package_id: '{lib_id}'}}) "

        if self.ecosystem == 'nuget':
            query = query + "WITH a.package_version AS version, "
        else:
            query = query + "WITH a.version AS version, "
        
        query = query + "a.coordinates AS coords RETURN coords, version"

        return query
    
    def __build_merge_query(self, source_coords: str, target_coords: str)->str:

        return (f"MATCH (s: {self.node_label} {{coordinates: '{source_coords}'}}) "
            f"MATCH (t: {self.node_label} {{coordinates: '{target_coords}'}}) "
            "MERGE (s)-[:NEXT]->(t)")
    