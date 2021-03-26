package org.tud.vulnanalysis.pom.dependencies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

/**
 *  A dependency resolver implementation that uses XML parsing and on-demand loading of referenced POM files to
 *  resolve dependencies. It is significantly faster than using the resolver that spawns a "real" Maven instance, but
 *  does some overapproximations and is not able to resolve any kind of reference:
 *      -   Dependency-Exclusions are not taken into consideration. There are cases where a dependency is defined in a POM,
 *          but does not actually apply because of some exclusions in any of the parent POMs.
 *      -   When resolving property values, this implementation only takes into account the parent hierarchy and directly
 *          linked "import"-scope dependencies. However, a property may be defined in the parent hierarchy of an
 *          "import"-scope dependency, which is not considered in this implementation.
 */
public class RecursiveDependencyResolver extends DependencyResolver {

    private DocumentBuilderFactory builderFactory;
    private List<Document> parsedPomFileHierarchy;
    private List<ArtifactIdentifier> parentIdentifierHierarchy;

    private Hashtable<Integer, HashSet<DependencySpec>> dependencySpecsPerHierarchyLevel;
    private Hashtable<Integer, HashSet<DependencySpec>> dependencyManagementSpecsPerHierarchyLevel;

    private Hashtable<Integer, List<Document>> importScopeDocuments;
    private Hashtable<Integer, List<ArtifactIdentifier>> importScopeIdentifiers;

    private Set<ArtifactDependency> finalDependencySpecs;

    private ResolverResult result;

    private boolean includeDependenciesInProfiles;

    private Logger log = LogManager.getLogger(RecursiveDependencyResolver.class);

    public RecursiveDependencyResolver(InputStream pomFileStream, ArtifactIdentifier identifier){
        super(pomFileStream, identifier);
        // BuilderFactory for XML parsing
        builderFactory = DocumentBuilderFactory.newInstance();

        // List that will contain the parent relation of this artifact
        parsedPomFileHierarchy = new ArrayList<>();
        parentIdentifierHierarchy = new ArrayList<>();

        // Intermediate dictionaries for storing dependency specifications associated to their level in the parent hierarchy
        dependencySpecsPerHierarchyLevel = new Hashtable<>();
        dependencyManagementSpecsPerHierarchyLevel = new Hashtable<>();

        importScopeDocuments = new Hashtable<>();
        importScopeIdentifiers = new Hashtable<>();

        // Final (flat) list of dependencies with resolved versions
        finalDependencySpecs = new HashSet<>();

        includeDependenciesInProfiles = false;
    }

    public void setIncludeDependenciesInProfiles(boolean value){
        this.includeDependenciesInProfiles = value;
    }

    @Override
    public ResolverResult resolveDependencies() {
        this.result = new ResolverResult(this.identifier);

        try{
            Document pomDoc = parseXml(this.pomFileInputStream);
            if(pomDoc != null){
                // Construct the Parent Hierarchy for this document
                parsedPomFileHierarchy.add(pomDoc);
                parentIdentifierHierarchy.add(identifier);
                buildPomFileHierarchy(pomDoc);

                // Set direct parent in result
                if(parentIdentifierHierarchy.size() > 1){
                    result.setParentIdentifier(parentIdentifierHierarchy.get(1));
                }

                // Detect all dependency specifications in the entire hierarchy and store them in intermediate dictionaries
                findRawDependenciesInHierarchy(0);

                // Iterate all dependency management specs and detect dependencies with scope "import". Resolve them
                // and add their content to separate intermediate dictionaries.
                expandImportScopeDependencies();

                // Use intermediate dictionaries to resolve missing versions / resolve property definitions
                resolveDependencyVersionsInHierarchy(0);

                result.setResults(this.finalDependencySpecs);
            }

        } catch (Exception x){
            ResolverError error = new ResolverError("Uncaught exception while resolving dependencies", x);
            result.appendError(error);
        }

        return this.result;
    }

    private ArtifactDependency fullyResolveDependency(DependencySpec dependencySpec, int declarationLevel){
        ArtifactDependency dependency = dependencySpec.Dependency;

        dependency.GroupId = resolveAllReferencesInValue(dependency.GroupId, dependencySpec, declarationLevel);
        if(dependency.GroupId == null)
            return null;

        dependency.ArtifactId = resolveAllReferencesInValue(dependency.ArtifactId, dependencySpec, declarationLevel);
        if(dependency.ArtifactId == null)
            return null;

        if(dependency.Version == null){
            DependencySpec specWithVersion = resolveMissingVersion(dependencySpec, declarationLevel);
            if(specWithVersion == null){
                ResolverError error =
                        new ResolverError.ParsingRelatedResolverError("Failed to resolve missing version", dependency.toString());
                this.result.appendError(error);
                return null;
            }
            String resolvedVersion = resolveAllReferencesInValue(specWithVersion.Dependency.Version, specWithVersion,
                    declarationLevel);

            if(resolvedVersion == null){
                ResolverError error =
                        new ResolverError.ParsingRelatedResolverError("Failed to resolve references in version", dependency.toString());
                this.result.appendError(error);
                return null;
            }

            dependency.Version = resolvedVersion;
        } else {
            dependency.Version = resolveAllReferencesInValue(dependency.Version, dependencySpec, declarationLevel);
        }

        if(dependency.Version == null)
            return null;

        if(dependency.Scope == null)
            dependency.Scope = "compile"; //Default scope

        return dependency;
    }

    private void resolveDependencyVersionsInHierarchy(int level){
        Set<DependencySpec> rawSpecsOnLevel = this.dependencySpecsPerHierarchyLevel.get(level);

        for(DependencySpec spec: rawSpecsOnLevel){
            if(spec.IsDeclaredInImportPom)
                continue; // Dependencies in imported POMs do not apply

            ArtifactDependency resolvedDependency = fullyResolveDependency(spec, level);

            if(resolvedDependency == null)
                continue; // Error is already reported

            boolean sameArtifactAlreadyPresent = this.finalDependencySpecs.stream()
                    .anyMatch(d -> d.GroupId.equals(resolvedDependency.GroupId) &&
                            d.ArtifactId.equals(resolvedDependency.ArtifactId) &&
                            Objects.equals(d.Scope, resolvedDependency.Scope));

            if(!sameArtifactAlreadyPresent){
                this.finalDependencySpecs.add(resolvedDependency);
            }
        }

        if(level < parsedPomFileHierarchy.size() - 1){
            resolveDependencyVersionsInHierarchy(level + 1);
        }
    }

    private String resolveAllReferencesInValue(String value, DependencySpec depSpec, int startLevel){
        if(!value.contains("${"))
            return value;

        while(value.contains("${")){
            String reference = value.substring(value.indexOf("${"), value.indexOf("}") + 1);
            String expandedValue = resolvePropertyValue(reference, depSpec, startLevel);

            if(expandedValue == null){
                return null;
            }

            value = value.replace(reference, expandedValue);
        }

        return value;
    }

    private String resolvePropertyValueInDocument(String propertyName, ArtifactIdentifier documentIdentifier,
                                                  ArtifactIdentifier parentIdentifier, Document doc, DependencySpec specifiedIn){

        // Begin with special variables, as there are actually people that redefine "parent.version" inside their
        // Properties tag..which leads to endless recursion
        if(specifiedIn.DeclaredIn.equals(documentIdentifier) &&
                (propertyName.startsWith("project.") || propertyName.startsWith("pom."))) {
            String subPropertyName = propertyName.substring(propertyName.indexOf('.') + 1).trim().toLowerCase();
            switch (subPropertyName) {
                case "groupid":
                    return documentIdentifier.GroupId;
                case "artifactid":
                    return documentIdentifier.ArtifactId;
                case "version":
                    return documentIdentifier.Version;
                case "parent.version":
                    if (parentIdentifier != null && !specifiedIn.IsDeclaredInImportPom) {
                        return parentIdentifier.Version;
                    }
                    ResolverError error =
                            new ResolverError.ParsingRelatedResolverError("Reference to parent version but no parent found",
                                    parentIdentifier == null ? null : parentIdentifier.toString());
                    this.result.appendError(error);
                    return null;
            }
        }

        NodeList matchingNodes = doc.getElementsByTagName(propertyName);
        for(int j = 0; j < matchingNodes.getLength(); j++){
            Node currentNode = matchingNodes.item(j);
            if(currentNode instanceof Element &&
                    currentNode.getParentNode() instanceof Element &&
                    ((Element)currentNode.getParentNode()).getTagName().toLowerCase().equals("properties")){
                return currentNode.getTextContent().trim();
            }
        }


        return null;
    }



    private String resolvePropertyValue(String propertyReference, DependencySpec depSpec, int startLevel){
        // Drop ${...} around property name
        String propertyName = propertyReference.substring(2, propertyReference.length()-1);

        for(int i = startLevel; i < parsedPomFileHierarchy.size(); i++){
            Document mainDoc = parsedPomFileHierarchy.get(i);
            ArtifactIdentifier mainIdentifier = parentIdentifierHierarchy.get(i);
            ArtifactIdentifier parentIdentifier = null;
            if(i < parsedPomFileHierarchy.size() - 1){
                parentIdentifier = parentIdentifierHierarchy.get(i + 1);
            }

            String result = resolvePropertyValueInDocument(propertyName, mainIdentifier, parentIdentifier, mainDoc, depSpec);

            if(result != null)
                return result;

            List<Document> importScopeDocsOnLevel = importScopeDocuments.get(i);

            if(importScopeDocsOnLevel != null && !importScopeDocsOnLevel.isEmpty()){
                for(int j = 0; j < importScopeDocsOnLevel.size(); j++){
                    Document importDoc =  importScopeDocsOnLevel.get(j);
                    ArtifactIdentifier importIdent = importScopeIdentifiers.get(i).get(j);
                    // We do not recurse to parents for import scope dependencies
                    result = resolvePropertyValueInDocument(propertyName, importIdent, null, importDoc, depSpec);

                    if(result != null)
                        return result;
                }
            }

        }

        ResolverError error =
                new ResolverError.ParsingRelatedResolverError("Failed to resolve property value", propertyReference);
        this.result.appendError(error);
        return null;
    }

    private DependencySpec resolveMissingVersion(DependencySpec incompleteDependency, int startLevel){
        for(int i = startLevel; i < parsedPomFileHierarchy.size(); i++){
            // Look for version definition in management sections
            for(DependencySpec managementSpec: this.dependencyManagementSpecsPerHierarchyLevel.get(i)){

                ArtifactDependency dep = managementSpec.Dependency;
                if(dep.GroupId.equals(incompleteDependency.Dependency.GroupId)){
                    // It seems that some people actually user property refs in artifact identifiers, which are inside a
                    // <dependencyManagement> tag. We resolve those here on-demand, we don't want to resolve the entire
                    // management specifications, that would certainly produce a few resolver errors.
                    dep.ArtifactId = resolveAllReferencesInValue(dep.ArtifactId, incompleteDependency, startLevel);
                    if(dep.ArtifactId != null && dep.ArtifactId.equals(incompleteDependency.Dependency.ArtifactId) &&
                            dep.Version != null){
                        return managementSpec;
                    }
                }
            }

            // Backup: Look for version in "normal" dependency specs
            for(DependencySpec spec: this.dependencySpecsPerHierarchyLevel.get(i)){
                ArtifactDependency dep = spec.Dependency;
                if(dep.GroupId.equals(incompleteDependency.Dependency.GroupId) &&
                        dep.ArtifactId.equals(incompleteDependency.Dependency.ArtifactId) &&
                        dep.Version != null){
                    return spec;
                }
            }
        }
        return null;
    }

    private void processRawDependenciesInDocument(Document doc, int level, ArtifactIdentifier docIdent, boolean isImportDependency){
        NodeList dependenciesInCurrentPom = doc.getElementsByTagName("dependency");

        for(int i = 0; i < dependenciesInCurrentPom.getLength(); i++){
            Node currentNode = dependenciesInCurrentPom.item(i);

            if(currentNode instanceof Element){
                Element currentDependencyElement = (Element)currentNode;
                ArtifactDependency dependency = readDependency(currentDependencyElement);

                DependencyElementContext context = determineDependencyElementContext(currentDependencyElement);

                DependencySpec spec = new DependencySpec(dependency, docIdent, isImportDependency);

                if(context == DependencyElementContext.DEPENDENCY_MANAGEMENT){
                    this.dependencyManagementSpecsPerHierarchyLevel.get(level).add(spec);
                }
                else if(context == DependencyElementContext.PROFILE_PROJECT_DEPENDENCY && this.includeDependenciesInProfiles){

                    this.dependencySpecsPerHierarchyLevel.get(level).add(spec);
                }
                else if(context == DependencyElementContext.PROJECT_DEPENDENCY){
                    this.dependencySpecsPerHierarchyLevel.get(level).add(spec);
                }
                //Drop plugin dependencies
            }
        }
    }

    private void findRawDependenciesInHierarchy(int level){
        if(level >= this.parsedPomFileHierarchy.size()){
            throw new RuntimeException("Invalid level " + level + ", only " + this.parsedPomFileHierarchy.size() + " parents found.");
        }

        if(!this.dependencySpecsPerHierarchyLevel.containsKey(level)){
            this.dependencySpecsPerHierarchyLevel.put(level, new HashSet<>());
            this.dependencyManagementSpecsPerHierarchyLevel.put(level, new HashSet<>());
        }

        processRawDependenciesInDocument(this.parsedPomFileHierarchy.get(level), level,
                this.parentIdentifierHierarchy.get(level), false);

        int newLevel = level + 1;

        if(newLevel < parsedPomFileHierarchy.size())
            findRawDependenciesInHierarchy(newLevel);
    }


    private DependencyElementContext determineDependencyElementContext(Element dependencyElem){
        Node parentNode = dependencyElem.getParentNode();

        while(parentNode != null){
            if(parentNode instanceof Element){
                String tagname = ((Element)parentNode).getTagName().toLowerCase();
                switch (tagname) {
                    case "dependencymanagement":
                        return DependencyElementContext.DEPENDENCY_MANAGEMENT;
                    case "plugin":
                        return DependencyElementContext.PLUGIN_DEPENDENCY;
                    case "profile":
                        return DependencyElementContext.PROFILE_PROJECT_DEPENDENCY;
                }
            }
            parentNode = parentNode.getParentNode();
        }

        return DependencyElementContext.PROJECT_DEPENDENCY;
    }

    private ArtifactDependency readDependency(Element dependencyElement){
        ArtifactDependency dependency =
                new ArtifactDependency(null, null, null, null);
        NodeList childNodes = dependencyElement.getChildNodes();

        for(int i = 0; i < childNodes.getLength(); i++){
            Node childNode = childNodes.item(i);

            if(childNode instanceof Element){
                Element childElem = (Element)childNode;
                switch(childElem.getTagName().toLowerCase()){
                    case "groupid":
                        dependency.GroupId = childElem.getTextContent().trim();
                        break;
                    case "artifactid":
                        dependency.ArtifactId = childElem.getTextContent().trim();
                        break;
                    case "version":
                        dependency.Version = childElem.getTextContent().trim();
                        break;
                    case "scope":
                        dependency.Scope = childElem.getTextContent().trim();
                        break;
                }
            }
        }

        if(dependency.GroupId == null || dependency.ArtifactId == null){
            ResolverError error = new ResolverError.ParsingRelatedResolverError("Incomplete dependency specification found",
                    dependency.toString());
            this.result.appendError(error);
            return null;
        }

        return dependency;
    }

    private void buildPomFileHierarchy(Document rootPom){
        Document currentDoc = rootPom;

        while(hasParentDefinition(currentDoc)){
            ArtifactIdentifier currentIdent = this.parentIdentifierHierarchy.get(this.parentIdentifierHierarchy.size() - 1);
            ArtifactIdentifier parentIdent = getParentIdentifier(currentDoc, currentIdent);

            if(parentIdent == null){
                // Error object already created
                throw new RuntimeException("Critical resolver error: Parent POM reference invalid");
            }

            InputStream parentPomStream = MavenCentralRepository.getInstance().openPomFileInputStream(parentIdent);

            if(parentPomStream == null){
                ResolverError error = new ResolverError.ParsingRelatedResolverError("Parent POM not found on Maven Central",
                        parentIdent.toString());
                this.result.appendError(error);
                throw new RuntimeException("Critical resolver error: Parent POM definition not found on Maven Central");
            }

            Document parentDoc = parseXml(parentPomStream);

            if(parentDoc != null){
                parsedPomFileHierarchy.add(parentDoc);
                parentIdentifierHierarchy.add(parentIdent);
                currentDoc = parentDoc;
            }
            else
            {
                ResolverError error = new ResolverError.ParsingRelatedResolverError("Failed to parse parent POM",
                        parentIdent.toString());
                this.result.appendError(error);
                throw new RuntimeException("Critical resolver error: Parent POM definition has parsing errors");
            }

        }
    }

    private boolean hasParentDefinition(Document doc){
        return doc.getDocumentElement().getElementsByTagName("parent").getLength() > 0;
    }

    private ArtifactIdentifier getParentIdentifier(Document doc, ArtifactIdentifier currentIdentifier){
        NodeList parentElems = doc.getDocumentElement().getElementsByTagName("parent");

        if(parentElems.getLength() > 1) {
            log.warn("More than one parent element detected for artifact: " + currentIdentifier.toString());
        }

        Element parentElem = (Element) parentElems.item(0);
        NodeList children = parentElem.getChildNodes();
        ArtifactIdentifier ident = new ArtifactIdentifier(null, null, null);

        for(int i = 0; i < children.getLength(); i++){
            Node n = children.item(i);

            if(n.getNodeType() != Node.ELEMENT_NODE)
                continue;

            Element currChild = (Element)n;

            switch(currChild.getTagName().toLowerCase()){
                case "groupid":
                    ident.GroupId = currChild.getTextContent().trim();
                    break;
                case "artifactid":
                    ident.ArtifactId = currChild.getTextContent().trim();
                    break;
                case "version":
                    ident.Version = currChild.getTextContent().trim();
                    break;
            }
        }

        if(ident.GroupId == null || ident.ArtifactId == null || ident.Version == null){
            ResolverError error = new ResolverError("Incomplete parent definition in POM file");
            this.result.appendError(error);
            return null;
        }

        return ident;
    }

    private void expandImportScopeDependencies() {
        for (int level = 0; level < this.parentIdentifierHierarchy.size(); level++){
            importScopeDocuments.put(level, new ArrayList<>());
            importScopeIdentifiers.put(level, new ArrayList<>());

            boolean newImportScopeDeps = true;

            while(newImportScopeDeps)
            {
                newImportScopeDeps = false;

                // Clone Hashset to avoid concurrent modification exception
                HashSet<DependencySpec> specsOnLevel =
                        new HashSet<>(this.dependencyManagementSpecsPerHierarchyLevel.get(level));

                for(DependencySpec spec : specsOnLevel){
                    ArtifactDependency dep = spec.Dependency;
                    if(dep.Scope != null && dep.Scope.toLowerCase().equals("import")){
                        try{
                            ArtifactDependency resolvedImportScopeDep = fullyResolveDependency(spec, level);

                            if(resolvedImportScopeDep == null){
                                throw new RuntimeException("Failed to resolve import scope dependency: " + dep);
                            }

                            if(importScopeIdentifiers.get(level).contains(resolvedImportScopeDep)){
                                continue;
                            }

                            InputStream dependencyInputStream = MavenCentralRepository.getInstance()
                                    .openPomFileInputStream(resolvedImportScopeDep);

                            if(dependencyInputStream == null){
                                throw new RuntimeException("Import Dependency POM definition not found on Maven Central: " +
                                        resolvedImportScopeDep);
                            }

                            Document dependencyDoc = parseXml(dependencyInputStream);
                            importScopeDocuments.get(level).add(dependencyDoc);
                            importScopeIdentifiers.get(level).add(resolvedImportScopeDep);

                            if(dependencyDoc == null){
                                throw new RuntimeException("Failed to read import dependency pom: " + resolvedImportScopeDep);
                            }
                            newImportScopeDeps = true;
                            processRawDependenciesInDocument(dependencyDoc, level, resolvedImportScopeDep,
                                    true);
                        } catch(Exception x) {
                            ResolverError error = new ResolverError.ParsingRelatedResolverError(
                                    "Failed to resolve import scope dependency", dep.toString(), x);
                            this.result.appendError(error);
                            // Non-Critical errors
                        }

                    }
                }
            }


        }
    }

    private Document parseXml(InputStream inputStream){
        try{
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            return builder.parse(inputStream);
        } catch(Exception x){
            x.printStackTrace();
            return null;
        }

    }

    static class DependencySpec {
        ArtifactDependency Dependency;
        ArtifactIdentifier DeclaredIn;
        boolean IsDeclaredInImportPom;

        DependencySpec(ArtifactDependency d, ArtifactIdentifier i, boolean isImport){
            this.Dependency = d;
            this.DeclaredIn = i;
            this.IsDeclaredInImportPom = isImport;
        }
    }

    private enum DependencyElementContext {
        DEPENDENCY_MANAGEMENT, PLUGIN_DEPENDENCY, PROJECT_DEPENDENCY, PROFILE_PROJECT_DEPENDENCY
    }

}
