package org.tud.vulnanalysis.pom.dependencies;

import org.junit.jupiter.api.*;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.pom.PomFileUtils;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

public class RecursiveDependencyResolverTest {

    private final ArtifactIdentifier normalArtifactIdent =
            new ArtifactIdentifier("org.opencypher", "okapi-ir", "1.0.0-beta3");

    private final ArtifactIdentifier quarkusIdent =
            new ArtifactIdentifier("io.quarkus","quarkus-rest-client-jsonb","1.11.4.Final");

    private final ArtifactIdentifier artifactWithMissingVersions =
            new ArtifactIdentifier("com.highstreet-technologies.aaa","aaa-authn-api","0.12.1");

    private final ArtifactIdentifier artifactWithIdentifierInterpolation =
            new ArtifactIdentifier("org.apache.spark", "spark-repl_2.12", "2.4.4");

    private final ArtifactIdentifier infiniteLoop =
            new ArtifactIdentifier("org.finos.legend.engine", "legend-engine-language-pure-dsl-service", "2.18.0");

    private final ArtifactIdentifier infiniteLoop2 =
            new ArtifactIdentifier("org.apache.geronimo.modules","geronimo-axis2-builder", "2.1.5");


    private ResolverResult processArtifactWithRecursiveResolver(ArtifactIdentifier ident){
        InputStream stream = PomFileUtils.openPomFileInputStream(ident);
        Assertions.assertNotNull(stream);

        RecursiveDependencyResolver resolver = new RecursiveDependencyResolver(stream, ident);
        resolver.setIncludeDependenciesInProfiles(false);
        ResolverResult result = resolver.resolveDependencies();
        Assertions.assertFalse(result.hasErrors());
        Assertions.assertTrue(result.hasResults());

        return result;
    }

    @Test()
    @DisplayName("Test Dependency Resolving for Recursive Parents")
    public void testResolveDependencies(){
        ResolverResult result = processArtifactWithRecursiveResolver(normalArtifactIdent);

        Set<ArtifactDependency> dependencies = result.getResults();

        Assertions.assertNotNull(dependencies);
        Assertions.assertFalse(dependencies.isEmpty());

        Assertions.assertEquals(17, dependencies.size());
    }

    @Test()
    @DisplayName("RecursiveResolver must not run into infinite loops")
    public void testInfiniteLoops(){
        ResolverResult result = processArtifactWithRecursiveResolver(infiniteLoop);
        Assertions.assertNotNull(result.getResults());
    }

    @Test()
    @DisplayName("RecursiveResolver must no run into infinite loops 2")
    public void testInfiniteLoops2(){
        ResolverResult result = processArtifactWithRecursiveResolver(infiniteLoop2);
        Assertions.assertNotNull(result.getResults());
    }

    @Test()
    @DisplayName("RecusiveResolver should deal with complicated poms")
    public void testQuarkusPom() {
        ResolverResult result = processArtifactWithRecursiveResolver(quarkusIdent);

        Set<ArtifactDependency> dependencies = result.getResults();

        Assertions.assertNotNull(dependencies);
        Assertions.assertFalse(dependencies.isEmpty());

        Assertions.assertEquals(5, dependencies.size());


        ArtifactDependency expectedViaImportDep = new ArtifactDependency("org.jboss.resteasy",
                "resteasy-json-binding-provider", "4.5.9.Final", "compile");
        Assertions.assertTrue(dependencies.contains(expectedViaImportDep));
    }

    @Test()
    @DisplayName("Recursive Resolver must deal with interpolation in artifact identifiers")
    public void testInterpolation(){
        ResolverResult result = processArtifactWithRecursiveResolver(artifactWithIdentifierInterpolation);

        Set<ArtifactDependency> deps = result.getResults();

        Assertions.assertNotNull(deps);
    }

    @Test()
    @DisplayName("RecursiveResolver must deal with missing version definitions")
    public void testMissingVersions(){
        ResolverResult result = processArtifactWithRecursiveResolver(artifactWithMissingVersions);

        Set<ArtifactDependency> deps = result.getResults();

        Assertions.assertNotNull(deps);
        Assertions.assertFalse(deps.isEmpty());

        Assertions.assertEquals(12, deps.size());
    }

    @Test()
    @DisplayName("RecursiveResolver should output the same as the MavenResolver")
    public void compareToMvnResolver(){
        long startTime = System.currentTimeMillis();
        ResolverResult recResult = processArtifactWithRecursiveResolver(normalArtifactIdent);
        long recDuration = System.currentTimeMillis() - startTime;

        IDependencyResolver mvnResolver =
                new MvnPluginDependencyResolver(PomFileUtils.openPomFileInputStream(normalArtifactIdent), normalArtifactIdent);

        startTime = System.currentTimeMillis();
        ResolverResult mvnResult = mvnResolver.resolveDependencies();
        long mvnDuration = System.currentTimeMillis() - startTime;

        System.out.println("Time Maven: " + mvnDuration);
        System.out.println("Time Recursive: " + recDuration);

        Assertions.assertFalse(mvnResult.hasErrors() || recResult.hasErrors());
        Assertions.assertTrue(mvnResult.hasResults() && recResult.hasResults());

        Set<ArtifactDependency> mvnDeps = mvnResult.getResults();
        Set<ArtifactDependency> recDeps = recResult.getResults();

        Assertions.assertEquals(17, mvnDeps.size());
        Assertions.assertEquals(17, recDeps.size());

        Assertions.assertTrue(Objects.deepEquals(mvnDeps, recDeps));
    }
}
