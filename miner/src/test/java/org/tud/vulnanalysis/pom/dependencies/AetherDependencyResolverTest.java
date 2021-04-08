package org.tud.vulnanalysis.pom.dependencies;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.utils.MinerConfiguration;

import java.util.Set;

public class AetherDependencyResolverTest {

    private final ArtifactIdentifier artifactWithIdentifierInterpolation =
            new ArtifactIdentifier("org.apache.spark", "spark-repl_2.12", "2.4.4");

    private final ArtifactIdentifier quarkusIdent =
            new ArtifactIdentifier("io.quarkus","quarkus-rest-client-jsonb","1.11.4.Final");


    private ResolverResult processArtifactWithAetherResolver(ArtifactIdentifier ident){

        AetherDependencyResolver resolver = new AetherDependencyResolver(null, ident, MinerConfiguration.getDefaultConfig());
        ResolverResult result = resolver.resolveDependencies();
        // Download errors are "not our fault"
        Assertions.assertFalse(result.hasErrors());
        Assertions.assertTrue(result.hasResults());

        return result;
    }

    @Test()
    @DisplayName("AetherResolver must produce valid results for Identifier Interpolation")
    public void testResolver(){
        ResolverResult result = processArtifactWithAetherResolver(artifactWithIdentifierInterpolation);

        Assertions.assertTrue(result.getResults().size() > 0);
    }

    @Test()
    @DisplayName("AetherResolver should deal with complicated poms")
    public void testQuarkusPom() {
        ResolverResult result = processArtifactWithAetherResolver(quarkusIdent);

        Set<ArtifactDependency> dependencies = result.getResults();

        Assertions.assertNotNull(dependencies);
        Assertions.assertFalse(dependencies.isEmpty());

        Assertions.assertEquals(5, dependencies.size());


        ArtifactDependency expectedViaImportDep = new ArtifactDependency("org.jboss.resteasy",
                "resteasy-json-binding-provider", "4.5.9.Final", "compile");
        Assertions.assertTrue(dependencies.contains(expectedViaImportDep));
    }
}
