package org.tud.vulnanalysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.tud.vulnanalysis.storage.BufferedNodeInformationIterator;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;

import static org.neo4j.driver.Values.parameters;

public class GraphNodeRelationResolver {

    private final Logger log = LogManager.getLogger(GraphNodeRelationResolver.class);
    private final BufferedNodeInformationIterator iterator = new BufferedNodeInformationIterator();

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

    public void createRelationsInGraph(){

        this.iterator.buildIndex();

        try(Session session = Neo4jSessionFactory.getInstance().buildSession()){
            while(this.iterator.hasNext()){

                if(this.numberOfNodes % 1000 == 0){
                    log.info("Processing relation " + this.numberOfNodes + " of " + this.iterator.getNumberOfNodes());
                }

                BufferedNodeInformationIterator.NodeInformation node = this.iterator.next();

                if(!makeRelationsExplicit(node, session)){
                    this.numberOfErrors += 1;
                }

                this.numberOfNodes += 1;
            }
        } catch (Exception x){
            log.error("Uncaught error while resolving dependencies in graph.", x);
        }

        log.info("Finished processing " + this.numberOfNodes + " relations with " + this.numberOfErrors + " errors.");
        log.info("Got " + this.numberOfUnmatchedDependencies + " unmatched dependencies for a total of " +
                this.numberOfArtifactsWithUnmatchedDependencies + " artifacts.");
        log.info("Got a total of " + this.numberOfUnmatchedParents + " unmatched parents.");

    }

    private boolean makeRelationsExplicit(BufferedNodeInformationIterator.NodeInformation node, Session session){
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
}
