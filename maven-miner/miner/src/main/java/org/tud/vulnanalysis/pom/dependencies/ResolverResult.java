package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ResolverResult {

    private ArtifactIdentifier rootArtifactIdentifier;

    private Set<ArtifactDependency> resultSet;

    private List<ResolverError> resolverErrors;

    private ArtifactIdentifier parentArtifactIdentifier;

    public long LastModified;

    public ResolverResult(ArtifactIdentifier ident){
        this.rootArtifactIdentifier = ident;
        this.resultSet = null;
        this.resolverErrors = new ArrayList<>();
        this.parentArtifactIdentifier = null;
        this.LastModified = -1;
    }

    public void setParentIdentifier(ArtifactIdentifier parent){
        this.parentArtifactIdentifier = parent;
    }

    public ArtifactIdentifier getParentIdentifier(){
        return this.parentArtifactIdentifier;
    }

    public boolean hasParentIdentifier(){
        return this.parentArtifactIdentifier != null;
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

    public boolean hasDownloadErrors() {
        return this.resolverErrors.stream().anyMatch(error -> error.IsCausedByMissingFile);
    }

    public List<ResolverError> getErrors(){
        return this.resolverErrors;
    }

}
