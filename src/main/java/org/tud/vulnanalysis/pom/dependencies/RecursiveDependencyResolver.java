package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class RecursiveDependencyResolver extends DependencyResolver {

    private DocumentBuilderFactory builderFactory;
    private List<Document> parsedPomFileHierarchy;
    private List<ArtifactIdentifier> parentIdentifierHierarchy;

    private Hashtable<Integer, HashSet<DependencySpec>> dependencySpecsPerHierarchyLevel;
    private Hashtable<Integer, HashSet<DependencySpec>> dependencyManagementSpecsPerHierarchyLevel;

    private Hashtable<Integer, List<Document>> importScopeDocuments;
    private Hashtable<Integer, List<ArtifactIdentifier>> importScopeIdentifiers;

    private Set<ArtifactDependency> finalDependencySpecs;

    public RecursiveDependencyResolver(File pomFile, ArtifactIdentifier identifier){
        super(pomFile, identifier);
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
    }

    @Override
    public Set<ArtifactDependency> resolveDependencies() {
        try{
            Document pomDoc = parseXml(Files.newInputStream(this.pomFile.toPath()));
            if(pomDoc != null){
                // Construct the Parent Hierarchy for this document
                parsedPomFileHierarchy.add(pomDoc);
                parentIdentifierHierarchy.add(identifier);
                buildPomFileHierarchy(pomDoc);

                // Detect all dependency specifications in the entire hierarchy and store them in intermediate dictionaries
                findRawDependenciesInHierarchy(0);

                expandImportScopeDependencies();

                // Use intermediate dictionaries to resolve missing versions / resolve property definitions
                resolveDependencyVersionsInHierarchy(0);
            }

        } catch (Exception x){
            x.printStackTrace();
            return null;
        }

        return this.finalDependencySpecs;
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
            DependencySpec specWithVersion = resolveMissingVersion(dependency, declarationLevel);
            if(specWithVersion == null){
                System.err.println("Failed to resolve missing version for " + dependency.toString());
                return null;
            }
            String resolvedVersion = resolveAllReferencesInValue(specWithVersion.Dependency.Version, specWithVersion,
                    declarationLevel);

            if(resolvedVersion == null){
                System.err.println("Failed to resolve missing version for " + dependency.toString());
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
            ArtifactDependency resolvedDependency = fullyResolveDependency(spec, level);

            if(resolvedDependency == null)
                continue;

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
        NodeList matchingNodes = doc.getElementsByTagName(propertyName);
        for(int j = 0; j < matchingNodes.getLength(); j++){
            Node currentNode = matchingNodes.item(j);
            if(currentNode instanceof Element &&
                    currentNode.getParentNode() instanceof Element &&
                    ((Element)currentNode.getParentNode()).getTagName().toLowerCase().equals("properties")){
                return currentNode.getTextContent().trim();
            }
        }

        if(specifiedIn.DeclaredIn.equals(documentIdentifier) &&
                (propertyName.startsWith("project.") || propertyName.startsWith("pom."))){
            String subPropertyName = propertyName.substring(propertyName.indexOf('.') + 1).trim().toLowerCase();
            switch(subPropertyName){
                case "groupid":
                    return documentIdentifier.GroupId;
                case "artifactid":
                    return documentIdentifier.ArtifactId;
                case "version":
                    return documentIdentifier.Version;
                case "parent.version":
                    if(parentIdentifier != null && !specifiedIn.IsDeclaredInImportPom){
                        return parentIdentifier.Version;
                    }
                    System.err.println("Reference to parent version but no parent found.");
                    return null;
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

        System.err.println("Failed to resolve property " + propertyReference);
        return null;
    }

    private DependencySpec resolveMissingVersion(ArtifactDependency incompleteDependency, int startLevel){
        for(int i = startLevel; i < parsedPomFileHierarchy.size(); i++){
            // Look for version definition in management sections
            for(DependencySpec managementSpec: this.dependencyManagementSpecsPerHierarchyLevel.get(i)){
                ArtifactDependency dep = managementSpec.Dependency;
                if(dep.GroupId.equals(incompleteDependency.GroupId) &&
                        dep.ArtifactId.equals(incompleteDependency.ArtifactId) &&
                        dep.Version != null){
                    return managementSpec;
                }
            }

            // Backup: Look for version in "normal" dependency specs
            for(DependencySpec spec: this.dependencySpecsPerHierarchyLevel.get(i)){
                ArtifactDependency dep = spec.Dependency;
                if(dep.GroupId.equals(incompleteDependency.GroupId) &&
                        dep.ArtifactId.equals(incompleteDependency.ArtifactId) &&
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

                int context = determineDependencyElementContext(currentDependencyElement);

                DependencySpec spec = new DependencySpec(dependency, docIdent, isImportDependency);

                if(context == 0){ //Dependency management
                    this.dependencyManagementSpecsPerHierarchyLevel.get(level).add(spec);
                }
                else if(context == 2){
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


    private int determineDependencyElementContext(Element dependencyElem){
        Node parentNode = dependencyElem.getParentNode();

        while(parentNode != null){
            if(parentNode instanceof Element){
                String tagname = ((Element)parentNode).getTagName().toLowerCase();
                if(tagname.equals("dependencymanagement")){
                    return 0;
                }
                else if(tagname.equals("plugin")){
                    return 1;
                }
            }
            parentNode = parentNode.getParentNode();
        }

        return 2;
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
            System.err.println("Incomplete dependency specification found!");
            return null;
        }

        return dependency;
    }

    private void buildPomFileHierarchy(Document rootPom){
        Document currentDoc = rootPom;

        while(hasParentDefinition(currentDoc)){
            ArtifactIdentifier parentIdent = getParentIdentifier(currentDoc);
            Document parentDoc = parseXml(MavenCentralRepository.getInstance().openPomFileInputStream(parentIdent));

            if(parentDoc != null){
                parsedPomFileHierarchy.add(parentDoc);
                parentIdentifierHierarchy.add(parentIdent);
                currentDoc = parentDoc;
            }
            else
            {
                System.err.println("Failed to parse parent pom.");
                break;
            }

        }
    }

    private boolean hasParentDefinition(Document doc){
        return doc.getDocumentElement().getElementsByTagName("parent").getLength() > 0;
    }

    private ArtifactIdentifier getParentIdentifier(Document doc){
        NodeList parentElems = doc.getDocumentElement().getElementsByTagName("parent");

        if(parentElems.getLength() > 1) {
            System.err.println("WARN: More than one parent element detected");
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
            System.err.println("Incomplete parent definition.");
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
                for(DependencySpec spec : (HashSet<DependencySpec>)this.dependencyManagementSpecsPerHierarchyLevel.get(level).clone()){
                    ArtifactDependency dep = spec.Dependency;
                    if(dep.Scope != null && dep.Scope.toLowerCase().equals("import")){
                        try{
                            ArtifactDependency resolvedImportScopeDep = fullyResolveDependency(spec, level);

                            if(importScopeIdentifiers.get(level).contains(resolvedImportScopeDep)){
                                continue;
                            }

                            newImportScopeDeps = true;

                            InputStream dependencyInputStream = MavenCentralRepository.getInstance()
                                    .openPomFileInputStream(resolvedImportScopeDep);
                            Document dependencyDoc = parseXml(dependencyInputStream);
                            importScopeDocuments.get(level).add(dependencyDoc);
                            importScopeIdentifiers.get(level).add(resolvedImportScopeDep);

                            if(dependencyDoc == null){
                                System.err.println("Failed to read import dependency pom: " + resolvedImportScopeDep);
                                continue;
                            }

                            processRawDependenciesInDocument(dependencyDoc, level, resolvedImportScopeDep,
                                    true);
                        } catch(Exception x) {
                            System.err.println("Failed to resolve import-scope dependency: " + x.getMessage());
                            x.printStackTrace();
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

}
