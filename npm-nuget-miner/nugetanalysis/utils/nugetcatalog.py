import requests, json

class NugetCatalogHelper(object):

    def __init__(self):
        self.service_index_url = 'https://api.nuget.org/v3/index.json'
        self.catalog = None
        self.__build_catalog()

    def __build_catalog(self):
        service_index_response = requests.get(self.service_index_url)

        if service_index_response.status_code != 200:
            raise Exception(f'Faild to locate catalog, invalid response code while querying service index: {service_index_response.status_code}')

        service_index = json.loads(service_index_response.text)

        catalog_url = None

        for resource_json in service_index['resources']:
            if resource_json['@type'] == 'Catalog/3.0.0':
                catalog_url = resource_json['@id']
                break
        
        if catalog_url == None:
            raise Exception(f'No catalog definition found in service index.')

        print(f'Downloading catalog from {catalog_url} ...')

        catalog_response = requests.get(catalog_url)

        if catalog_response.status_code != 200:
            raise Exception(f'Failed to download catalog, got status: {catalog_response.status_code}')

        print(f'Parsing catalog...')

        self.catalog = json.loads(catalog_response.text)

        print(f'Catalog Successfully initialized.')
    
    def get_all_leafs(self, page_start_index = 0):
        return NugetCatalogLeafIterator(self.catalog, page_start_index)
    
    def get_all_packages(self, page_start_index = 0):
        return NugetCatalogPackageIterator(self.catalog, page_start_index)

    def iterate_all(self):
        count = 0

        for catalog_leaf in self.get_all_packages():

            print(str(catalog_leaf))
            
            count += 1

            if count % 100 == 0:
                print(f'Processed {count} leafes')


class NugetCatalogLeafIterator:

    def __init__(self, catalog_json, page_start_index = 0):
        self.catalog_pages = catalog_json['items']
        self.catalog_pages.sort(key=lambda item: item['commitTimeStamp'])

        self.current_page_index = page_start_index
        self.__load_current_page()

        self.current_item_index = 0
    
    def __iter__(self):
        return self

    def __next__(self):
        if self.current_item_index >= self.current_page['count']:
            if self.current_page_index < len(self.catalog_pages):
                self.current_page_index += 1
                self.__load_current_page()
                self.current_item_index = 0
            else:
                raise StopIteration
        
        value = self.current_page['items'][self.current_item_index]

        self.current_item_index += 1

        return value

    def __load_current_page(self):
        page_url = self.catalog_pages[self.current_page_index]['@id']

        print(f'Fetching next page from catalog: {self.current_page_index}...')

        page_response = requests.get(page_url)

        if page_response.status_code != 200:
            raise Exception(f'Failed to retrieve catalog page at {page_url}, got {page_response.status_code}')

        self.current_page = json.loads(page_response.text)

class NugetCatalogPackageIterator:
    
    def __init__(self, catalog_json, page_start_index = 0):
        self.leaf_iterator = NugetCatalogLeafIterator(catalog_json, page_start_index)
    
    def __iter__(self):
        return self
    
    def __next__(self):
        try:
            leaf = next(self.leaf_iterator)

            package_url = leaf['@id']

            package_response = requests.get(package_url)

            if package_response.status_code != 200:
                raise Exception(f'Failed to retrieve catalog package at {package_url}, got {package_response.status_code}')

            return NugetPackage.from_json(json.loads(package_response.text))
        except Exception as x:
            print(f'Error while iterating package : {x}')
            return None


class NugetPackage(object):

    def __init__(self, id: str, version: str, created_at: str):
        self.id = id
        self.version = version
        self.created_at = created_at
        self.dependencies = []
        self.commit_id = None
        self.authors_string = None
        self.unique_id = None
    
    @staticmethod
    def from_json(json_obj):
        id = json_obj['id']
        version = json_obj['version']
        
        if 'created' in json_obj:
            created = json_obj['created']
        else:
            created = 'Unknown'

        package = NugetPackage(id, version, created)

        if 'authors' in json_obj:
            package.authors_string = json_obj['authors']
        
        package.commit_id = json_obj['catalog:commitId']
        package.unique_id = json_obj['@id']

        if 'dependencyGroups' in json_obj:
            dependencies = []

            for dependency_group in json_obj['dependencyGroups']:
                group_id = dependency_group['@id']

                if 'dependencies' in dependency_group:
                    for dependency in dependency_group['dependencies']:
                        dep = NugetPackageDependency(dependency['id'], dependency['range'], group_id)
                        dependencies.append(dep)

            package.dependencies = dependencies

        return package
    
    def __str__(self):
        return self.id + ':' + self.version + ' (' + self.created_at + ') - ' + str(len(self.dependencies)) + ' dependencies'

class NugetPackageDependency:

    def __init__(self, id:str, range:str, dependency_group:str):
        self.package_id = id
        self.version_range = range
        self.dependency_group_id = dependency_group
    
    def to_json(self):
        return f'{{"package_id": "{self.package_id}", "version_range": "{self.version_range}", "dependency_group_id": "{self.dependency_group_id}"}}'

    



