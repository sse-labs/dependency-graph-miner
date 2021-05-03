from npmanalysis.npmutils import NpmIndexReader, NpmRegistryHelper
from npmanalysis.npmmodel import NpmPackageVersion
from dependencygraphutils.neo4jsupport import Neo4jAdapter

import os, os.path, threading, time, json

class NpmNodeProcessor(object):

    # Name of temporary file that stores the index progress, to avoid cold restarts 
    PROGRESS_TEMP_FILE = 'progress.txt'

    def __init__(self):
        self.npm_index_reader = NpmIndexReader()
        self.npm_registry_adapter = NpmRegistryHelper()
        self.graph_db_adapter = None
        self.start_index = 0
        self.thread_pool = []
        self.thread_limit = int(os.getenv("THREAD_COUNT", "10"))
    
    def initialize(self, npm_index:str) -> bool:
        # Initialize the node package index from json file, fail on error
        if not self.npm_index_reader.load_index(npm_index):
            return False
        
        print(f'Successfully loaded NPM index')
        
        # Initialize the graph database connection
        self.graph_db_adapter = Neo4jAdapter.create_default_instance()
        print(f'Successfully initialized Neo4j db connection to {neo4j_url}')

        # Read index range to work on
        index_range_start = int(os.getenv('INDEX_START', '0'))
        self.start_index = index_range_start
        print(f'Assigned start index: {self.start_index}')

        index_range_end_raw = os.environ.get('INDEX_END')

        if index_range_end_raw == None:
            self.index_range_end = None
        else:
            self.index_range_end = int(index_range_end_raw)
            print(f'Assigned end index: {self.index_range_end}')

        # If previous progress is saved, then load the index from file
        if os.path.isfile(self.PROGRESS_TEMP_FILE):
            file = open(self.PROGRESS_TEMP_FILE, "r")
            self.start_index = int(file.read())
            file.close()
            print(f'Starting at last index: {self.start_index}')

        # Successully initialized
        return True
    
    def store_all_packages(self):
        # Statistics variables
        index = 0
        failures = 0

        # Iterate all packages from index
        for package_ref in self.npm_index_reader.data:
            # Skip all packages before the start index (restore previous progress)
            if index < self.start_index:
                index += 1
                continue

            index += 1

            try:
                print(f"Processing {package_ref['id']} ...")
                # Read all NpmPackageVersions for the current package id
                # Retrieves information from the node package registry and parses results
                versions = self.npm_registry_adapter.get_all_package_versions(package_ref['id'])

                # Write all versions to graph database
                self.__schedule_store_thread(versions)

            except Exception as x:
                # Catch all errors to avoid termination
                print(f'Failed to process {package_ref} :: {x}')
                # Count failures
                failures += 1
            
            # On every 10 iterations
            if index % 10 == 0:
                # Print progress
                print(f'Processing {index} with {failures} failures.')
                # Save progress to temp file to allow restart
                progress_file = open(self.PROGRESS_TEMP_FILE, 'w')
                progress_file.write(str(index))
                progress_file.close()
            
            if self.index_range_end != None and index >= self.index_range_end:
                print(f'Reached end of assigned index range: {index}')
                break
        
        print(f'Processed {index} packages, with {failures} failures')
    
    def store_package_versions(self, version_list):
        for package_version in version_list:
            query = ("MERGE (p: NpmPackage {"
                f"name: '{package_version.name}', "
                f"version: '{package_version.version_number}', "
                f"license: '{package_version.license}', "
                f"package_id: '{package_version.package_id}', "
                f"release_date: '{package_version.release_date}', "
                f"maintainers: '{package_version.maintainers_list()}', "
                f"dependencies: '{json.dumps(package_version.dependency_dictionary)}'"
                "})")
            self.graph_db_adapter.execute_cypher_query(query)
    
    def __cleanup_dead_threads(self):
        for thread in self.thread_pool:
                if not thread.is_alive():
                    self.thread_pool.remove(thread)
    
    def __schedule_store_thread(self, versions_to_store):
        self.__cleanup_dead_threads()

        print(f'Thread pool count: {len(self.thread_pool)}')

        while len(self.thread_pool) >= self.thread_limit:
            print(f'Waiting for free slot in thread pool...')
            
            self.__cleanup_dead_threads()
            
            time.sleep(1)
 
        thread = threading.Thread(target=self.store_package_versions, args=(versions_to_store, ), daemon=False)
        thread.start()
        self.thread_pool.append(thread)
