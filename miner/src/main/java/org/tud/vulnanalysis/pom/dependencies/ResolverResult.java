package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResolverResult {

    private ArtifactIdentifier rootArtifactIdentifier;

    private Set<ArtifactDependency> resultSet;

    private List<ResolverError> resolverErrors;

    public ResolverResult(ArtifactIdentifier ident){
        this.rootArtifactIdentifier = ident;
        this.resultSet = null;
        this.resolverErrors = new ArrayList<>();
    }

    public ArtifactIdentifier getRootArtifactIdentifier(){
        return this.rootArtifactIdentifier;
    }

    public boolean hasResults(){
        return this.resultSet != null;
    }

    public void setResults( Set<ArtifactDependency> results){
        this.resultSet = results;
    }

    public Set<ArtifactDependency> getResults(){
        return this.resultSet;
    }

    public void appendError(ResolverError error){
        this.resolverErrors.add(error);
    }

    public boolean hasErrors(){
        return !this.resolverErrors.isEmpty();
    }

    public List<ResolverError> getErrors(){
        return this.resolverErrors;
    }

}
