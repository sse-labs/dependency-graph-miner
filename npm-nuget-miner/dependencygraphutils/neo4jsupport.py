from neo4j import GraphDatabase
import os

class Neo4jAdapter(object):

    def __init__(self, db_url, user = None, password = None, encrypt_traffic=False):
        self.closed = False
        self.user = user
        self.url = db_url

        if user == None or password == None:
            self.driver = GraphDatabase.driver(db_url, encrypted=encrypt_traffic)
        else:
            self.driver = GraphDatabase.driver(db_url, auth=(user, password), encrypted=encrypt_traffic)

    def __del__(self):
        self.close_driver()

    def close_driver(self):
        self.driver.close()
        self.closed = True

    def create_session(self):
        return self.driver.session()
    
    def execute_cypher_query(self, query: str):
        with self.driver.session() as session:
            return session.write_transaction(lambda tx : tx.run(query))

    def execute_cypher_command_batch(self, queries) -> int:
        error_count = 0

        with self.driver.session() as session:
            for query in queries:
                try:
                    session.write_transaction(lambda tx : tx.run(query))
                except Exception as x:
                    print(f'Error while executing Neo4j command batch: {x}')
                    error_count += 1
        
        return error_count
        
    
    @staticmethod
    def create_default_instance():
        neo4j_user = os.getenv('NEO4J_USER', 'neo4j')
        neo4j_pass = os.getenv('NEO4J_PASS', 'neo4j')

        graph_db_url = os.getenv('NEO4J_URL', 'bolt://172.17.0.1:7687')

        print(f'Connecting to Neo4j @ {graph_db_url} with user {neo4j_user}...')

        return Neo4jAdapter(graph_db_url, neo4j_user, neo4j_pass)