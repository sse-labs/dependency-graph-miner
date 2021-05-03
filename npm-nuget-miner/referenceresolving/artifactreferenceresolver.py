from dependencygraphutils.neo4jsupport import Neo4jAdapter
from dependencygraphutils.model.artifactreferences import ArtifactReference, VersionNumber, ArtifactEcosystem
from dependencygraphutils.model.exceptions import VersionReferenceUnparsableException
from neo4j import Driver, Record, Session, StatementResult
import os, json, time, threading, traceback

class ArtifactDependencyResolver(object):

    def __init__(self):
        self.graph_db_adapter = Neo4jAdapter.create_default_instance()

        self.thread_pool = []
        self.max_thread_count = int(os.getenv('THREAD_COUNT', '8'))
        self.batch_size = int(os.getenv('BATCH_SIZE', '100'))
        self.progress_output_skip = int(os.getenv('OUTPUT_SKIP', '4'))
        self.use_parallel = True if os.getenv('PARALLEL', 'True').lower() == 'true' else False
        self.use_streaming = True if os.getenv('STREAM_NODES', 'True').lower() == 'true' else False
        self.use_merge_operator = True if os.getenv('USE_MERGE_OP', 'True').lower() == 'true' else False

        self.references_reused = 0
        self.references_total = 0
        self.caught_references_parsing_errors = 0

        print(f'Maximum Worker Threads: {self.max_thread_count}')
        print(f'Artifact Batch Size: {self.batch_size}')
        print(f'Parallel Processing Enabled: {self.use_parallel}')
        print(f'Node Streaming Enabled: {self.use_streaming}')
        print(f'Using Merge Operator: {self.use_merge_operator}')
    
    def build_index(self, node_label:str):
        index = []

        total_node_count = self.get_total_node_count(node_label)
        percentil = total_node_count // 100
        current_index = 0

        print(f'Starting to build index for {total_node_count} nodes...')

        with self.graph_db_adapter.driver.session(fetch_size=1000) as session:
            index_result_handle = session.run(f'MATCH (a:{node_label}) RETURN a.coordinates, a.dependencies')

            for record in index_result_handle:
                index.append({"c":record['a.coordinates'], "d":record['a.dependencies']})

                current_index += 1

                if current_index % percentil == 0:
                    print(f'Indexed {current_index} nodes so far')
        
        print(f'Index successfully built, contains {len(index)} entries')

        return index

    def process_artifact_dependencies(self, node_label:str):
        total_node_count = self.get_total_node_count(node_label)

        if not self.use_streaming:
            print(f'Building index for node label {node_label}, this might take a while...')
            index = self.build_index(node_label)
            print(f'Index successfully created.')
        else:
            print(f'Streaming {total_node_count} nodes of label {node_label}...')

        batch = []
        batch_count = 0
        batches_total = (total_node_count // self.batch_size) + 1

        if not self.use_streaming:
            while len(index) > 0:
                batch = index[:self.batch_size]
                index = index[self.batch_size:]

                if self.use_parallel:
                    self.__schedule_batch_processing(batch, node_label)
                else:
                    self.process_artifact_batch(batch, node_label)

                batch_count += 1
                
                if batch_count % self.progress_output_skip == 0:
                    quota = 0 if self.references_total == 0 else (self.references_reused / self.references_total) * 100.0
                    print(f'Scheduled {(batch_count / batches_total) * 100.0} % of all nodes for processing (Parsing errors successfully caught: {self.caught_references_parsing_errors}, Reuse Quota: {quota})')
        else:
            query = f"MATCH (a:{node_label}) WITH a.dependencies AS deps, a.coordinates AS coord RETURN coord, deps"

            with self.graph_db_adapter.driver.session(fetch_size = self.batch_size * 10) as session:
                result = session.run(query)
                for record in result:
                    batch.append({"c": record['coord'],"d":record['deps']})

                    if len(batch) >= self.batch_size:
                        if self.use_parallel:
                            self.__schedule_batch_processing(batch, node_label)
                        else:
                            self.process_artifact_batch(batch, node_label)

                        batch_count += 1
                        batch = []

                        if batch_count % self.progress_output_skip == 0:
                            quota = 0 if self.references_total == 0 else (self.references_reused / self.references_total) * 100.0
                            print(f'Scheduled {(batch_count / batches_total) * 100.0} % of all nodes for processing (Parsing errors successfully caught: {self.caught_references_parsing_errors}, Reuse Quota: {quota})')

                # Process last batch
                self.process_artifact_batch(batch, node_label)
        

        

    def __schedule_batch_processing(self, batch, node_label):
        self.__cleanup_dead_threads()

        while len(self.thread_pool) >= self.max_thread_count:
            self.__cleanup_dead_threads()
            
            time.sleep(1)
 
        thread = threading.Thread(target=self.process_artifact_batch, args=(batch, node_label,), daemon=False)
        thread.start()
        self.thread_pool.append(thread)

    def __cleanup_dead_threads(self):
        for thread in self.thread_pool:
                if not thread.is_alive():
                    self.thread_pool.remove(thread)
    
    def process_artifact_batch(self, batch, node_label):

        error_list = []
        session: Session = self.graph_db_adapter.driver.session()


        for artifact in batch:
            try:
                coordinates = artifact['c']
                dependencies_string = artifact['d']
                 
                if dependencies_string == 'null' or dependencies_string == '[]':
                    # Skip empty dependencies, there is nothing to process (skip fast)
                    continue
    
                dependencies = json.loads(dependencies_string)

                normalized_dependencies = []

                # Ecosystem-Dependent: Normalize dependencies
                if node_label == 'NpmPackage':
                    for library_ident in dependencies.keys():
                        normalized_dependencies.append({'lib':library_ident, 'ver':dependencies[library_ident]})              
                elif node_label == 'NugetPackage':
                    for artifact_ref in dependencies:
                        normalized_dependencies.append({'lib':artifact_ref['package_id'], 'ver':artifact_ref['version_range']})
                
                references_reused = 0
                references_total = 0
                caught_references_parsing_errors = 0

                # Process normalized dependencies
                for dependency in normalized_dependencies:
                    library_ident = dependency['lib']
                    version_range = dependency['ver']
                    reference_coordinates = f"{library_ident}:{version_range}"

                    references_total += 1
                    
                    # Check and re-use existing reference node if possible
                    artifact_ref_exists = self.does_artifact_reference_exist(session, reference_coordinates)
                    

                    # If ArtifactReference does not exist: Create it 
                    if not artifact_ref_exists:
                        ref = ArtifactReference()

                        try:
                            # Ecosystem-Dependent version range parsing
                            if node_label == 'NugetPackage':
                                ref.init_for_nuget(library_ident, version_range)
                            elif node_label == 'NpmPackage':
                                ref.init_for_npm(library_ident, version_range)
                        except Exception:
                            # Create fallback reference node in case of parsing errors
                            ecosystem = ArtifactEcosystem.Nuget.name if node_label == 'NugetPackage' else ArtifactEcosystem.Npm.name
                            self.create_artifact_reference_node(session, node_label, coordinates, library_ident, version_range, ecosystem, True)
                            
                            caught_references_parsing_errors += 1
                            continue

                        # Create Reference Node
                        self.create_artifact_reference_node(session, node_label, coordinates, ref.library_identifier,
                            ref.version_range_string_representation, ref.ecosystem.name, ref.is_floating)

                        # Retrieve all versions of target library
                        versions_node_id_dict = self.get_version_node_id_dict(session, library_ident, node_label)

                        # Find Versions affected by dependency
                        for version_string in versions_node_id_dict.keys():
                            dependency_version = VersionNumber(version_string)
                            target_node_coordinates = versions_node_id_dict[version_string]
                                
                            # Create edge in graph
                            if ref.contains_version(dependency_version):
                                operator = 'MERGE' if self.use_merge_operator else 'CREATE'
                                reference_query = (
                                    f"MATCH (r:ArtifactReference {{coordinates:'{reference_coordinates}'}}) USING INDEX r:ArtifactReference(coordinates) "
                                    f"MATCH (t:{node_label} {{coordinates:'{target_node_coordinates}'}}) USING INDEX t:{node_label}(coordinates) "
                                    f"{operator} (r)-[:REFERENCES]->(t)"
                                )
                                self.graph_db_adapter.execute_cypher_query(reference_query)
                    else:
                        # Connect to already existing ArtifactReference node
                        query = (f"MATCH (a:{node_label} {{coordinates:'{coordinates}'}}) USING INDEX a:{node_label}(coordinates) "
                        f"MATCH (ref:ArtifactReference {{coordinates:'{reference_coordinates}'}}) USING INDEX ref:ArtifactReference(coordinates) "
                        "MERGE (a)-[:DEPENDS_ON]->(ref)")
                        
                        self.graph_db_adapter.execute_cypher_query(query)
                        
                        references_reused += 1
                    
                self.references_total += references_total
                self.references_reused += references_reused
                self.caught_references_parsing_errors += caught_references_parsing_errors
                        
            except Exception as x:
                print(f'Error processing artifact with coordinates {coordinates}, Exception {str(x)}')
                print(traceback.format_exc())
                error_list.append(coordinates)

                with open('resolver_errors.txt', 'a+') as file:
                    file.write(f'\r\n{str(coordinates)} -- {str(x)}')
        
        session.close()


    def get_version_node_id_dict(self, session:Session, library_ident: str, node_label:str):
        version_attr_name = 'version' if node_label == 'NpmPackage' else 'package_version'
        query = (
            f"MATCH (a:{node_label} {{package_id: '{library_ident}'}}) USING INDEX a:{node_label}(package_id) WITH a.{version_attr_name} as version, a.coordinates as coords "
            "RETURN version, coords"
        )
        result = session.run(query)
        
        result_dict = {}

        for record in result:
            result_dict[record['version']] = record['coords']

        return result_dict

    def get_total_node_count(self, node_label:str) -> int:
        record = self.graph_db_adapter.execute_cypher_query(f'MATCH (a:{node_label}) RETURN COUNT(a)').single()
        return int(record['COUNT(a)'])
    
    def create_artifact_reference_node(self, session:Session, node_label: str, artifact_coordinates:str, ref_lib_ident:str,
                                        ref_version_range:str, ref_eco_name:str, ref_is_floating:bool):

        ref_coordinates = f"{ref_lib_ident}:{ref_version_range}"

        session.run(f"MERGE (r:ArtifactReference {{library_id:'{ref_lib_ident}', "
            f"version_range:'{ref_version_range}', coordinates:'{ref_coordinates}', "
            f"ecosystem:'{ref_eco_name}', is_floating:{ref_is_floating}}})")
        
        session.run((f"MATCH (a:{node_label} {{coordinates:'{artifact_coordinates}'}}) USING INDEX a:{node_label}(coordinates) "
            f"MATCH (r:ArtifactReference {{coordinates:'{ref_coordinates}'}}) USING INDEX r:ArtifactReference(coordinates) "
            "MERGE (a)-[:DEPENDS_ON]->(r)"
        ))
    
    def does_artifact_reference_exist(self, session: Session, coordinates:str) -> bool:
        record = session.run(
            f"MATCH (ref: ArtifactReference {{coordinates:'{coordinates}'}}) USING INDEX ref:ArtifactReference(coordinates) RETURN id(ref)"
        ).single()

        if record == None:
            return False
        else:
            return True
       
    

