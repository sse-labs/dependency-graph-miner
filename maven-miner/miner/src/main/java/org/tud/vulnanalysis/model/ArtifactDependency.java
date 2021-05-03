package org.tud.vulnanalysis.model;

import java.util.Objects;

public class ArtifactDependency extends ArtifactIdentifier {

    public String Scope;

    public ArtifactDependency(String groupId, String artifactId, String version, String scope){
        super(groupId, artifactId, version);
        this.Scope = scope;
    }

    public static ArtifactDependency fromCompositeIdentifier(String compositeIdentifier){
        String[] parts = compositeIdentifier.split(":");

        if(parts.length == 5)
            return new ArtifactDependency(parts[0], parts[1], parts[3], parts[4]);
        else if(parts[2].equals("test-jar")){
            // Weird composite identifier format for test jars...
            return new ArtifactDependency(parts[0], parts[1], parts[4], parts[5]);
        }
        else{
            throw new RuntimeException("Invalid composite identifier format: " + compositeIdentifier);
        }
    }

    @Override
    public String getCoordinates(){
        return super.getCoordinates() + ":" + this.Scope;
    }

    @Override
    public String toString(){
        return String.join(":", super.GroupId, super.ArtifactId, super.Version, this.Scope);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ArtifactDependency that = (ArtifactDependency) o;
        return Objects.equals(Scope, that.Scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), Scope);
    }
}
