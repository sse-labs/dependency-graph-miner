from npmanalysis.npmcrawling import NpmNodeProcessor
from npmanalysis.npmutils import NpmIndexDownloader
import os.path

if not os.path.isfile('./index.json'):
    download_success = NpmIndexDownloader.download_index_to_file('./index.json')

    if not download_success:
        raise Exception('Cannot process nodes, index could not be downloaded')
    else:
        print('Npm index successfully downloaded.')

p = NpmNodeProcessor()
p.initialize("./index.json")
p.store_all_packages()
