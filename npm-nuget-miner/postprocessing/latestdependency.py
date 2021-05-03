from dependencygraphutils.neo4jsupport import Neo4jAdapter
import os

class LatestDependencyDetector(object):

    def __init__(self, ecosystem:str):
        self.ecosystem = ecosystem
        self.recursion_limit = int(os.getenv('MAX_DEPTH', '250'))
        self.print_skip = int(os.getenv('PRINT_SKIP', '50'))

        if ecosystem == 'nuget':
            self.ecosystem_label = 'Nuget'
            self.node_label = 'NugetPackage'
        elif ecosystem == 'npm':
            self.ecosystem_label = 'Npm'
            self.node_label = 'NpmPackage'
        
        self.graph_db_adapter = Neo4jAdapter.create_default_instance()
    
    def process_all_references(self):

        ref_index = []
        count = 0
        no_target_count = 0

        iterator_query = self.__build_iterator_query()

        print(f'Building ArtifactReference index for ecosystem {self.ecosystem}')

        global_session = self.graph_db_adapter.create_session()

        for record in global_session.run(iterator_query):
            ref_index.append(record['coords'])

            count += 1

            if count % 10000 == 0:
                print(f'Indexed {count} references so far...')

        global_session.close()

        print(f'Starting detection of latest reference targets...')
        count = 0
        total = len(ref_index)

        for ref_coords in ref_index:
            count += 1

            if count % self.print_skip == 0:
                percent = round(100.0 * count / total, 2)
                print(f'Processed {percent}% of all references ({count} / {total})')
                
                if self.print_skip == 1:
                    print(f'Current: {ref_coords}')
                


            local_session = self.graph_db_adapter.create_session()

            version_spec = ref_coords.split(':')[-1]

            include_prereleases = '-' in version_spec or '+' in version_spec

            result_without_next = self.__get_latest_affected_without_next(local_session, ref_coords,
                include_prereleases)

            found_any_target = False

            if result_without_next != None:
                # Without next is default if existent, if not use latest affected
                self.__merge_ref_and_node(local_session, ref_coords, result_without_next)
                #print(f'Found without next: {ref_coords} --> {result_without_next}')
                found_any_target = True
            else:
                result_with_next = self.__get_latest_affected_with_next(local_session,
                    ref_coords, include_prereleases)

                if result_with_next != None:
                    self.__merge_ref_and_node(local_session, ref_coords, result_with_next)
                    #print(f'Found with next: {ref_coords} --> {result_with_next}')
                    found_any_target = True
                
            # If no target found and we excluded prereleases -> Try finding target by including prereleases
            if not found_any_target and not include_prereleases:
                result_without_next = self.__get_latest_affected_without_next(local_session, ref_coords, True)

                if result_without_next != None:
                    self.__merge_ref_and_node(local_session, ref_coords, result_without_next)
                    found_any_target = True
                else:
                    result_with_next = self.__get_latest_affected_with_next(local_session, ref_coords, True)

                    if result_with_next != None:
                        self.__merge_ref_and_node(local_session, ref_coords, result_with_next)
                        found_any_target = True
            
            if not found_any_target:
                print(f'No current target found for {ref_coords}')
                no_target_count += 1

            local_session.close()
        
        print(f'Processed {count} references, {no_target_count} without current target.')


    
    def __build_iterator_query(self) -> str:
        return (f"MATCH (ref:ArtifactReference {{ecosystem: '{self.ecosystem_label}'}}) WHERE "
            f"(ref)-[:REFERENCES]->(:{self.node_label}) AND NOT (ref)-[:CURRENT_TARGET]->(:{self.node_label}) "
            "RETURN ref.coordinates AS coords")
    
    def __get_latest_affected_with_next(self, session, artifact_coords:str, include_prereleases:bool) -> str:
        
        if not include_prereleases:
            version_attribute_name = 'package_version' if self.ecosystem == 'nuget' else 'version'
            date_attribute_name = 'created_at' if self.ecosystem == 'nuget' else 'release_date'

            query = (f"MATCH (a:ArtifactReference {{coordinates:'{artifact_coords}'}})-[:REFERENCES]->"
                f"(t:{self.node_label})-[:NEXT*0..{self.recursion_limit}]->(o:{self.node_label}) "
                f"WHERE NOT (a)-[:REFERENCES]->(o) AND NOT t.{version_attribute_name} CONTAINS '-' "
                f"AND NOT t.{version_attribute_name} CONTAINS '+' "
                f"RETURN t.coordinates AS coords ORDER BY t.{date_attribute_name} DESC LIMIT 1")
        else:
            query = (f"MATCH (a:ArtifactReference {{coordinates:'{artifact_coords}'}})-[:REFERENCES]->"
                f"(t:{self.node_label})-[:NEXT]->(o:{self.node_label}) "
                f"WHERE NOT (a)-[:REFERENCES]->(o) "
                f"RETURN t.coordinates AS coords")
        
        result = session.run(query).single()

        if result != None:
            return result['coords']
        else:
            return None


    def __get_latest_affected_without_next(self, session, artifact_coords:str, include_prereleases:bool) -> str:

        if not include_prereleases:
            version_attribute_name = 'package_version' if self.ecosystem == 'nuget' else 'version'
            date_attribute_name = 'created_at' if self.ecosystem == 'nuget' else 'release_date'

            query = (f"MATCH (a:ArtifactReference {{coordinates:'{artifact_coords}'}})-[:REFERENCES]->"
                f"(t:{self.node_label})-[:NEXT*0..{self.recursion_limit}]->(o:{self.node_label}) "
                f"WHERE NOT (o)-[:NEXT]->(:{self.node_label}) AND NOT t.{version_attribute_name} CONTAINS '-' "
                f"AND NOT t.{version_attribute_name} CONTAINS '+' "
                f"RETURN t.coordinates AS coords ORDER BY t.{date_attribute_name} DESC LIMIT 1")
        else:
            query = (f"MATCH (a:ArtifactReference {{coordinates:'{artifact_coords}'}})-[:REFERENCES]->"
                f"(o:{self.node_label}) WHERE NOT (o)-[:NEXT]->(:{self.node_label}) "
                f"RETURN o.coordinates AS coords")
        
        result = session.run(query).single()

        if result != None:
            return result['coords']
        else:
            return None
    
    def __merge_ref_and_node(self, session, ref_coords, node_coords):

        query = (f"MATCH (a:ArtifactReference {{coordinates:'{ref_coords}'}}) "
                f"MATCH (t:{self.node_label} {{coordinates:'{node_coords}'}}) "
                "CREATE (a)-[:CURRENT_TARGET]->(t)")
        
        session.run(query)