from nugetanalysis.utils.nugetcatalog import NugetCatalogHelper
from dependencygraphutils.neo4jsupport import Neo4jAdapter
from dependencygraphutils.indexutils import IndexHelper
import threading, time, json, os

ERROR_COUNT = 0

class NugetNodeCrawler(object):

    def __init__(self):
        self.nuget_catalog = NugetCatalogHelper()
        self.batch_size = int(os.getenv('BATCH_SIZE', '40'))
        self.thread_pool = []
        self.thread_limit = int(os.getenv('THREAD_COUNT', '5'))

        self.start_page_index = int(os.getenv('START_PAGE_INDEX', '0'))
        print(f'Starting at catalog page: {self.start_page_index}')

        try:
            self.graph_db_adapter = Neo4jAdapter.create_default_instance()
            IndexHelper.create_indices(self.graph_db_adapter)
        except ConnectionRefusedError as cre:
            raise Exception(f'Failed to connect to Neo4j at {self.graph_db_url}. Is the graph db running?')
    
    # Crawl all packages
    def do_crawling(self):
        number_of_batches = 0
        current_batch = []

        for package in self.nuget_catalog.get_all_packages(self.start_page_index):
            current_batch.append(package)

            if len(current_batch) == self.batch_size:
                self.__schedule_batch_processing(current_batch)
                number_of_batches += 1
                current_batch = []

                print(f'Number of processed batches: {number_of_batches}')
                print(f'Number of processed packages: {number_of_batches * self.batch_size}')
                print(f'Total errors so far: {ERROR_COUNT}')
    
    # Schedule a new thread for processing a batch of packages. Respect thread pool limit
    def __schedule_batch_processing(self, batch):
        self.__cleanup_dead_threads()

        print(f'Thread pool count: {len(self.thread_pool)}')

        while len(self.thread_pool) >= self.thread_limit:
            print(f'Waiting for free slot in thread pool...')
            
            self.__cleanup_dead_threads()
            
            time.sleep(1)
 
        thread = threading.Thread(target=self.process_package_batch, args=(batch, len(self.thread_pool),), daemon=False)
        thread.start()
        self.thread_pool.append(thread)
    
    # Remove all threads from pool that are not active anymore
    def __cleanup_dead_threads(self):
        for thread in self.thread_pool:
                if not thread.is_alive():
                    self.thread_pool.remove(thread)
    
    @staticmethod
    def __build_cypher_query(package):
        commit_id = ""

        if package.commit_id != None:
            commit_id = package.commit_id

        authors = ""

        if package.authors_string != None:
            authors = package.authors_string.replace("'", " ").replace("\\", "\\\\")
                
        unique_id = ""

        if package.unique_id != None:
            unique_id = package.unique_id

        dependency_string = '['

        for dep in package.dependencies:
            dependency_string = dependency_string + dep.to_json() + ','
                
        if len(dependency_string) > 1:
            dependency_string = dependency_string[:-1]
                
        dependency_string += ']'


        query = ("MERGE (p: NugetPackage {"
            f"package_id: '{package.id}', "
            f"package_version: '{package.version}', "
            f"created_at: '{package.created_at}', "
            f"dependencies: '{dependency_string}', "
            f"authors: '{authors}', "
            f"commit_id: '{commit_id}', "
            f"unique_id: '{unique_id}', "
            f"unlisted: {package.is_unlisted} "
            "})")

        return query
        
    # Process batch of packages
    def process_package_batch(self, list_of_packages, thread_ident = 0):
        global ERROR_COUNT

        errors_in_batch = 0

        query_batch = []
        
        for package in list_of_packages:
            if package == None:
                print(f'Skipped an empty package due to previous iterating errors.')
                errors_in_batch += 1
                continue
            
            try:
                query_batch.append(self.__build_cypher_query(package))
            except Exception as x:
                errors_in_batch += 1
                print(f'Failed to add package to graph: {str(package)}, Exception was: {x}')
        
        # Use a single session object for the whole batch to avoid creating too many sessions
        errors_in_batch += self.graph_db_adapter.execute_cypher_command_batch(query_batch)
        
        ERROR_COUNT += errors_in_batch
        print(f'Successfully processed a batch of {len(list_of_packages)} packages, with {errors_in_batch} errors.', flush=True)
            
            


