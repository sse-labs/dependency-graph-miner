package org.anon.vulnanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.anon.vulnanalysis.storage.Neo4jSessionFactory;

import static org.neo4j.driver.Values.parameters;

public class GraphNodeRelationResolver {

    private final Logger log = LogManager.getLogger(GraphNodeRelationResolver.class);
    private final ObjectMapper reader = new ObjectMapper();

    private int numberOfErrors;
    private int numberOfNodes;
    private int numberOfArtifactsWithUnmatchedDependencies;
    private int numberOfUnmatchedDependencies;
    private int numberOfUnmatchedParents;

    public GraphNodeRelationResolver(){
        this.numberOfErrors = 0;
        this.numberOfNodes = 0;
        this.numberOfArtifactsWithUnmatchedDependencies = 0;
        this.numberOfUnmatchedDependencies = 0;
        this.numberOfUnmatchedParents = 0;
    }

    private void handleNodeRecord(Record record){
        try (Session session = Neo4jSessionFactory.getInstance().buildSession()) {
            String coords = record.get("coords").asString();
            String dependenciesRaw = record.get("deps").asString();
            String parentCoords = record.get("parent").asString();

            if (this.numberOfNodes % 1000 == 0) {
                log.info("Processing relation " + this.numberOfNodes);
            }

            NodeInformation nodeInformation = new NodeInformation();

            nodeInformation.nodeCoordinates = coords;
            nodeInformation.parentCoordinates = parentCoords;
            nodeInformation.nodeDependencies = reader.readValue(dependenciesRaw, String[].class);

            if (!this.makeRelationsExplicit(nodeInformation, session)) {
                this.numberOfErrors += 1;
            }

        } catch (Exception x) {
            log.error("Failed to handle node: " + record.get("coords").asString(), x);
        } finally {
            this.numberOfNodes += 1;
        }

    }

    public void createRelationsInGraph(){

        log.info("Start processing relations in graph...");

        try(Session session = Neo4jSessionFactory.getInstance().buildSession()){

            Result nodeIteratorResult =
                    session.run("MATCH (a:Artifact) RETURN a.coordinates AS coords, a.dependencies AS deps, " +
                            "a.parentCoords AS parent");

            while(nodeIteratorResult.hasNext()){
                this.handleNodeRecord(nodeIteratorResult.next());
            }
        }

        log.info("Finished processing " + this.numberOfNodes + " relations with " + this.numberOfErrors + " errors.");
        log.info("Got " + this.numberOfUnmatchedDependencies + " unmatched dependencies for a total of " +
                this.numberOfArtifactsWithUnmatchedDependencies + " artifacts.");
        log.info("Got a total of " + this.numberOfUnmatchedParents + " unmatched parents.");

    }

    private boolean makeRelationsExplicit(NodeInformation node, Session session){
        try {
            session.writeTransaction(transaction -> {
                Result result;

                int relationsCreated = 0;

                for(String dependency : node.nodeDependencies){
                    String[] parts = dependency.split(":");
                    String depCoords = parts[0] + ":" + parts[1] + ":" + parts[2];

                    result = transaction.run("MATCH (a: Artifact {coordinates: $ac}) MATCH (b: Artifact {coordinates: $dc}) " +
                            "CREATE (a)-[:DEPENDS_ON {scope: $scope}]->(b)", parameters(
                                    "ac", node.nodeCoordinates,
                            "dc", depCoords,
                            "scope", parts[3]
                    ));
                    relationsCreated += result.consume().counters().relationshipsCreated();
                }

                int unmatchedRelations = node.nodeDependencies.length - relationsCreated;

                if(unmatchedRelations > 0){
                    log.warn("Got " + unmatchedRelations + " unmatched relations for " + node.nodeCoordinates);
                    this.numberOfArtifactsWithUnmatchedDependencies += 1;
                    this.numberOfUnmatchedDependencies += unmatchedRelations;
                }

                if(!node.parentCoordinates.equals("none")){
                    result = transaction.run("MATCH (a: Artifact {coordinates: $ac}) MATCH (p: Artifact {coordinates: $p}) " +
                            "CREATE (a)-[:CHILD_OF]->(p)", parameters(
                            "ac", node.nodeCoordinates,
                            "p", node.parentCoordinates
                    ));

                    if(result.consume().counters().relationshipsCreated() == 0){
                        log.warn("Failed to located parent " + node.parentCoordinates + " for " + node.nodeCoordinates);
                        this.numberOfUnmatchedParents += 1;
                    }
                }

                return null;
            });

            return true;
        } catch (Exception x) {
            log.error("Error while making dependencies explicit for node: " + node.nodeCoordinates, x);
            return false;
        }
    }

    private static class NodeInformation {
        public String nodeCoordinates;

        public String[] nodeDependencies;

        public String parentCoordinates;
    }
}
