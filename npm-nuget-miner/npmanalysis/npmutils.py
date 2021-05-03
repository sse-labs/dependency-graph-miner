import ijson, json
import urllib
import requests
from npmanalysis.npmmodel import NpmPackageVersion

class NpmIndexReader(object):

    def __init__(self):
        self.data = None

    def load_index(self, index_file: str) -> bool:
        try:

            json_file = open(index_file, 'r')

            self.data = ijson.items(json_file, 'rows.item')

            return True
        except Exception as x:
            print(f'Failed to read npm index file at {index_file}, exception: {x}')

            self.data = None

            return False

class NpmIndexDownloader(object):
    @staticmethod
    def download_index_to_file(out_file_path:str, index_url = 'https://replicate.npmjs.com/_all_docs') -> bool:
        try:
            print(f'Attempting to download index from: {index_url}')

            response = requests.get(index_url)
            response.raise_for_status()

            with open(out_file_path, 'wb') as out_file:
                for chunk in response.iter_content(chunk_size=8192):
                    out_file.write(chunk)
            
            response.close()
            
            return True
        except Exception as x:
            print(f'Failed to download npm index from {index_url}, exception: {x}')
            return False


class NpmRegistryHelper(object):

    def __init__(self):
        self.base_url = 'https://replicate.npmjs.com/'
    
    def get_package_data(self, package_id: str):
        url = self.base_url + package_id.replace('/', '%2F')

        response = requests.get(url)

        if response.status_code != 200:
            raise Exception(f'Non-Success status code while fetching artifact: {response.status_code}')

        if response.encoding != None: 
            page_source = response.text.encode(response.encoding)
        else:
            page_source = response.text

        if page_source == None:
            raise Exception(f'Empty response while fetching artifact')

        return json.loads(page_source)
    
    def get_all_package_versions(self, package_id: str):
        data = self.get_package_data(package_id)

        versions = []

        for version_string in data['versions'].keys():
            version = NpmPackageVersion()
            version.init_from_json(data['versions'][version_string])

            version.release_date = data['time'][version_string]
            version.package_id = package_id

            versions.append(version)
        
        return versions
