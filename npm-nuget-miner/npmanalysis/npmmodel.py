class NpmPackageMaintainer(object):
    def __init__(self, name: str, email: str):
        self.name = name
        self.email = email
    
    def __str__(self):
        return f"{self.name} ({self.email})"

class NpmPackageVersion(object):

    def __init__(self):
        self.package_id = None
        self.release_date = None
        self.name = None
        self.version_number = None
        self.license = None
        self.dependency_dictionary = None
        self.maintainers = None
    
    def maintainers_list(self) -> str:
        if self.maintainers == None:
            return "[]"

        val = "["
        for maintainer in self.maintainers:
            val = val + str(maintainer) + " "
        
        val = val + "]"

        return val
    
    def init_from_json(self, json_data):
        # Name of package, always a string
        self.name = json_data['name']

        # Version number string
        self.version_number = json_data['version']

        # Optional license
        if 'license' in json_data:
            # May be a string, if so then just store it
            if isinstance(json_data['license'], str):
                self.license = json_data['license']
            # May be a dictionary containing 'type' and 'url', compose a string out of that
            elif ('type' in json_data['license']) and ('url' in json_data['license']):
                self.license = f"{json_data['license']['type']} ({json_data['license']['url']})"
        
        # Optional dependencies, store as dictionary
        if 'dependencies' in json_data:
            self.dependency_dictionary = json_data['dependencies']
        
        self.maintainers = []

        # Store maintainers array
        if 'maintainers' in json_data:
            for maintainer_obj in json_data['maintainers']:
                mail = maintainer_obj['email'] if 'email' in maintainer_obj else ''
                self.maintainers.append(NpmPackageMaintainer(maintainer_obj['name'], mail))
