package org.tud.vulnanalysis.pom.dependencies;

import org.junit.jupiter.api.*;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.pom.PomFileDownloadResponse;
import org.tud.vulnanalysis.pom.PomFileUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;

public class RecursiveDependencyResolverTest {

    private final ArtifactIdentifier artifactIdent =
            new ArtifactIdentifier("org.opencypher", "okapi-ir", "1.0.0-beta3");

    private final ArtifactIdentifier quarkusIdent =
            new ArtifactIdentifier("io.quarkus","quarkus-rest-client-jsonb","1.11.4.Final");

    private final ArtifactIdentifier artifactWithMissingVersions =
            new ArtifactIdentifier("com.highstreet-technologies.aaa","aaa-authn-api","0.12.1");

    private final ArtifactIdentifier artifactWithIdentifierInterpolation =
            new ArtifactIdentifier("org.apache.spark", "spark-repl_2.12", "2.4.4");


    private static File tempDir = new File("test-temp/");
    private File pomFile = Paths.get(tempDir.getAbsolutePath(), "pom.xml").toFile();
    private File quarkusFile = Paths.get(tempDir.getAbsolutePath(), "quarkus.pom").toFile();
    private File missingVersionsFile = Paths.get(tempDir.getAbsolutePath(), "missing.pom").toFile();
    private File interpolationFile = Paths.get(tempDir.getAbsolutePath(), "interpolation.pom").toFile();

    @BeforeAll
    static void createTempDir(){
        tempDir.mkdir();
    }

    @AfterAll
    static void deleteTempDir(){
        tempDir.delete();
    }

    @BeforeEach
    public void setUp() {
        if(pomFile.exists()){
            pomFile.delete();
        }

        PomFileDownloadResponse response = PomFileUtils.downloadPomFile(artifactIdent, pomFile);

        Assertions.assertTrue(response.getSuccess());
        Assertions.assertTrue(pomFile.exists());
    }

    @Test()
    @DisplayName("Test Dependency Resolving for Recursive Parents")
    public void testResolveDependencies(){
        IDependencyResolver resolver = new RecursiveDependencyResolver(pomFile, artifactIdent);
        Set<ArtifactDependency> dependencies = resolver.resolveDependencies();

        Assertions.assertNotNull(dependencies);
        Assertions.assertFalse(dependencies.isEmpty());

        Assertions.assertEquals(17, dependencies.size());
    }

    @Test()
    @DisplayName("RecusiveResolver should deal with complicated poms")
    public void testQuarkusPom() {
        PomFileDownloadResponse response = PomFileUtils.downloadPomFile(quarkusIdent, quarkusFile);

        Assertions.assertTrue(response.getSuccess());
        Assertions.assertTrue(quarkusFile.exists());

        IDependencyResolver resolver = new RecursiveDependencyResolver(quarkusFile, quarkusIdent);
        Set<ArtifactDependency> dependencies = resolver.resolveDependencies();

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
        PomFileDownloadResponse response = PomFileUtils.downloadPomFile(artifactWithIdentifierInterpolation,
                interpolationFile);

        Assertions.assertTrue(response.getSuccess());
        Assertions.assertTrue(interpolationFile.exists());

        IDependencyResolver res = new RecursiveDependencyResolver(interpolationFile, artifactWithIdentifierInterpolation);

        Set<ArtifactDependency> deps = res.resolveDependencies();

        Assertions.assertNotNull(deps);
    }

    @Test()
    @DisplayName("RecursiveResolver must deal with missing version definitions")
    public void testMissingVersions(){
        PomFileDownloadResponse response = PomFileUtils.downloadPomFile(artifactWithMissingVersions, missingVersionsFile);

        Assertions.assertTrue(response.getSuccess());
        Assertions.assertTrue(missingVersionsFile.exists());

        IDependencyResolver res = new RecursiveDependencyResolver(missingVersionsFile, artifactWithMissingVersions);

        Set<ArtifactDependency> deps = res.resolveDependencies();

        Assertions.assertNotNull(deps);
        Assertions.assertFalse(deps.isEmpty());

        Assertions.assertEquals(12, deps.size());
    }

    @Test()
    @DisplayName("RecursiveResolver should output the same as the MavenResolver")
    public void compareToMvnResolver(){
        IDependencyResolver mvnResolver = new MvnPluginDependencyResolver(pomFile, artifactIdent);
        IDependencyResolver recursiveResolver = new RecursiveDependencyResolver(pomFile, artifactIdent);

        long startTime = System.currentTimeMillis();
        Set<ArtifactDependency> mvnDeps = mvnResolver.resolveDependencies();
        long mvnTime = System.currentTimeMillis();
        Set<ArtifactDependency> recDeps = recursiveResolver.resolveDependencies();
        long endTime = System.currentTimeMillis();

        long durationMvn = mvnTime - startTime;
        long durationRec = endTime - mvnTime;

        System.out.println("Time Maven: " + durationMvn);
        System.out.println("Time Recursive: " + durationRec);

        Assertions.assertEquals(17, mvnDeps.size());
        Assertions.assertEquals(17, recDeps.size());

        Assertions.assertTrue(Objects.deepEquals(mvnDeps, recDeps));
    }

    @AfterEach
    public void destroy(){
        pomFile.delete();

        if(quarkusFile.exists()){
            quarkusFile.delete();
        }

        if(missingVersionsFile.exists()){
            missingVersionsFile.delete();
        }

        if(interpolationFile.exists()){
            interpolationFile.delete();
        }
    }
}
